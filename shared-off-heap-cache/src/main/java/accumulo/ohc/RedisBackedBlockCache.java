package accumulo.ohc;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.accumulo.core.file.blockfile.cache.impl.ClassSize;
import org.apache.accumulo.core.spi.cache.BlockCache;
import org.apache.accumulo.core.spi.cache.CacheEntry;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Weigher;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.annotations.VisibleForTesting;

import io.micrometer.common.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

/**
 * Implements an on-heap/off-heap Accumulo BlockCache. This
 * is a copy of https://github.com/keith-turner/accumulo-ohc
 * but instead of using OHC for the off-heap cache, this uses
 * Redis to enable for an off-heap cache that can be shared
 * by multiple processes on the same host.
 */
public class RedisBackedBlockCache implements BlockCache {

  private static final Logger LOG = LoggerFactory.getLogger(RedisBackedBlockCache.class);

  private final LoadingCache<String,Block> onHeapCache;
  
  // Jedis (Java Redis Connection), not thread-safe, so
  // uses a connection pool
  private final JedisPool offHeapConnectionPool;
  
  private final long onHeapSize;

  private final String cacheType;

  RedisBackedBlockCache(final RedisBackedBlockCacheConfiguration config) {
    
    final String hostname = config.getOffHeapProperties().getOrDefault("hostname", Protocol.DEFAULT_HOST);
    final Integer port = Integer.parseInt(config.getOffHeapProperties().getOrDefault("port", Integer.toString(Protocol.DEFAULT_PORT)));
    final String password = config.getOffHeapProperties().get("password");

    offHeapConnectionPool = new JedisPool(new JedisPoolConfig(), hostname, port, 
        Protocol.DEFAULT_TIMEOUT, password);

    Map<String,String> onHeapProps = new HashMap<>(config.getOnHeapProperties());
    onHeapProps.computeIfAbsent("maximumWeight", k -> config.getMaxSize() + "");
    onHeapProps.computeIfAbsent("initialCapacity", k -> "" + (int) Math.ceil(1.2 * config.getMaxSize() / config.getBlockSize()));
    // Allow setting recordStats to true/false, a deviation from CaffineSpec. This is done because a default of 'on' is wanted, but want to allow user to
    // override.
    onHeapProps.compute("recordStats", (k, v) -> v == null || v.equals("") || v.equals("true") ? "" : null);

    this.onHeapSize = Long.valueOf(onHeapProps.get("maximumWeight"));

    String specification = onHeapProps.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));

    Weigher<String,Block> weigher = (blockName, block) -> {
      int keyWeight = ClassSize.align(blockName.length()) + ClassSize.STRING;
      return keyWeight + block.weight();
    };
    onHeapCache = Caffeine.from(specification).weigher(weigher).evictionListener(new RemovalListener<String,Block>(){
      @Override
      public void onRemoval(@Nullable String key, @Nullable Block block,
          @NonNull RemovalCause cause) {
        // this is called before the entry is actually removed from the cache
        if (cause.wasEvicted()) {
          // TODO: I'm seeing that newly loaded blocks (from load() line 198) are being immediately evicted.
          // Found https://github.com/ben-manes/caffeine/issues/616 which may be related.
          LOG.info("Evicting block {} from on-heap cache, putting to off-heap cache", key);
          set(key, block.getBuffer());
        }
      }
    }).build(new CacheLoader<String,Block>() {
      @Override
      public Block load(String key) throws Exception {
        byte[] buffer = get(key);
        if (buffer == null) {
          return null;
        }
        // TODO: I'm never seeing this message. It appears that off-heap blocks
        // are loaded back in via load() line 196, called from getBlock(String,Loader)
        LOG.info("Promoting block {} from off-heap cache to on-heap cache", key);
        return new Block(buffer);
      }
    });

    cacheType = config.getType().name();
  }
  
  private void set(String key, byte[] block) {
    try (Jedis redis = offHeapConnectionPool.getResource()) {
      redis.set(key.getBytes(StandardCharsets.UTF_8), block);
    }
  }
  
  private byte[] get(String key) {
    try (Jedis redis = offHeapConnectionPool.getResource()) {
      return redis.get(key.getBytes(StandardCharsets.UTF_8));
    }
  }
  
  private Map<String,String> stats() {
    Map<String,String> stats = new HashMap<>();
    try (Jedis redis = offHeapConnectionPool.getResource()) {
      try {
        Properties props = new Properties();
        props.load(new StringReader(redis.info("all")));
        props.forEach((k,v) -> {
          stats.put((String) k, (String) v);
        });
      } catch (IOException e) {
        LOG.error("Error parsing stats", e);
      }
    }
    LOG.debug("Redis stats: {}", stats);
    return stats;
  }

  @Override
  public CacheEntry cacheBlock(String blockName, byte[] buf) {
    return wrap(blockName, onHeapCache.asMap().computeIfAbsent(blockName, k -> new Block(buf)));
  }

  @Override
  public CacheEntry getBlock(String blockName) {
    return wrap(blockName, onHeapCache.get(blockName));
  }

  private CacheEntry wrap(String cacheKey, Block block) {
    if (block != null) {
      return new TlfuCacheEntry(cacheKey, block);
    }

    return null;
  }

  private class TlfuCacheEntry implements CacheEntry {

    private final String cacheKey;
    private final Block block;

    TlfuCacheEntry(String k, Block b) {
      this.cacheKey = k;
      this.block = b;
    }

    @Override
    public byte[] getBuffer() {
      return block.getBuffer();
    }

    @Override
    public <T extends Weighable> T getIndex(Supplier<T> supplier) {
      return block.getIndex(supplier);
    }

    @Override
    public void indexWeightChanged() {
      if (block.indexWeightChanged()) {
        // update weight
        onHeapCache.put(cacheKey, block);
      }
    }
  }

  private Block load(String key, Loader loader, Map<String,byte[]> resolvedDeps) {

    byte[] data = get(key);
    if (data == null) {
      data = loader.load((int) Math.min(Integer.MAX_VALUE, this.onHeapSize), resolvedDeps);
      if (data == null) {
        return null;
      } else {
        LOG.info("Loaded block {} from Loader", key);
      }
    } else {
      LOG.info("Loaded block {} from off-heap cache", key);
    }
    return new Block(data);
  }

  private Map<String,byte[]> resolveDependencies(Map<String,Loader> deps) {
    if (deps.size() == 1) {
      Entry<String,Loader> entry = deps.entrySet().iterator().next();
      CacheEntry ce = getBlock(entry.getKey(), entry.getValue());
      if (ce == null) {
        return null;
      }
      return Collections.singletonMap(entry.getKey(), ce.getBuffer());
    } else {
      HashMap<String,byte[]> resolvedDeps = new HashMap<>();
      for (Entry<String,Loader> entry : deps.entrySet()) {
        CacheEntry ce = getBlock(entry.getKey(), entry.getValue());
        if (ce == null) {
          return null;
        }
        resolvedDeps.put(entry.getKey(), ce.getBuffer());
      }
      return resolvedDeps;
    }
  }

  @Override
  public CacheEntry getBlock(String blockName, Loader loader) {
    Map<String,Loader> deps = loader.getDependencies();
    Block block;
    if (deps.size() == 0) {
      block = onHeapCache.get(blockName, k -> load(blockName, loader, Collections.emptyMap()));
    } else {
      // This code path exist to handle the case where dependencies may need to be loaded. Loading dependencies will access the cache. Cache load functions
      // should not access the cache.
      block = onHeapCache.getIfPresent(blockName);

      if (block == null) {
        // Load dependencies outside of cache load function.
        Map<String,byte[]> resolvedDeps = resolveDependencies(deps);
        if (resolvedDeps == null) {
          return null;
        }

        // Use asMap because it will not increment stats, getIfPresent recorded a miss above. Use computeIfAbsent because it is possible another thread loaded
        // the data since this thread called getIfPresent.
        block = onHeapCache.asMap().computeIfAbsent(blockName, k -> load(blockName, loader, resolvedDeps));
      }
    }

    return wrap(blockName, block);
  }

  @Override
  public long getMaxSize() {
    String offHeapSize = stats().get("max_memory");
    if (! StringUtils.isEmpty(offHeapSize)) {
      return Long.parseLong(offHeapSize) + onHeapSize;
    }
    return onHeapSize;
  }

  public void logStats() {
    LOG.info("On  Heap Cache Stats {} {}", cacheType, onHeapCache.stats());
    LOG.info("Off Heap Cache Stats {} {}", cacheType, stats());
  }

  @Override
  public Stats getStats() {

    CacheStats stats = onHeapCache.stats();

    return new Stats() {

      @Override
      public long hitCount() {
        return stats.hitCount() + Long.parseLong(stats().get("keyspace_hits"));
      }

      @Override
      public long requestCount() {
        return stats.requestCount();
      }

    };
  }

  public void stop() {
    onHeapCache.invalidateAll();
    onHeapCache.cleanUp();

    logStats();
    
  }

  @Override
  public long getMaxHeapSize() {
    return this.onHeapSize;
  }

  @VisibleForTesting
  CacheStats getOnHeapStats() {
    return onHeapCache.stats();
  }

  @VisibleForTesting
  Map<String,String> getOffHeapStats() {
    return stats();
  }
}
