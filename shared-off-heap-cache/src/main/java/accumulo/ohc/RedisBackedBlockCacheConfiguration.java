package accumulo.ohc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.spi.cache.BlockCacheManager.Configuration;
import org.apache.accumulo.core.spi.cache.CacheType;

public class RedisBackedBlockCacheConfiguration {

  public static final String PROPERTY_PREFIX = "redis";

  public static final String ON_HEAP_PREFIX = "on-heap.";
  public static final String OFF_HEAP_PREFIX = "off-heap.";

  public static final String LOG_INTERVAL_PROP = "logInterval";

  private final Map<String, String> offHeapProps;
  private final Map<String, String> onHeapProps;

  private final long maxSize;
  private final long blockSize;
  private final CacheType type;
  private final String logInterval;

  public RedisBackedBlockCacheConfiguration(Configuration conf, CacheType type) {
    Map<String, String> allProps = conf.getProperties(PROPERTY_PREFIX, type);

    Map<String, String> onHeapProps = new HashMap<>();
    Map<String, String> offHeapProps = new HashMap<>();

    allProps.forEach((k, v) -> {
      if (k.startsWith(ON_HEAP_PREFIX)) {
        onHeapProps.put(k.substring(ON_HEAP_PREFIX.length()), v);
      } else if (k.startsWith(OFF_HEAP_PREFIX)) {
        offHeapProps.put(k.substring(OFF_HEAP_PREFIX.length()), v);
      }
    });

    this.offHeapProps = Collections.unmodifiableMap(offHeapProps);
    this.onHeapProps = Collections.unmodifiableMap(onHeapProps);

    this.maxSize = conf.getMaxSize(type);
    this.blockSize = conf.getBlockSize();
    this.logInterval = allProps.get(LOG_INTERVAL_PROP);
    this.type = type;
  }

  public Map<String, String> getOffHeapProperties() {
    return offHeapProps;
  }

  public Map<String, String> getOnHeapProperties() {
    return onHeapProps;
  }

  @Override
  public String toString() {
    return "OnHeapProps:" + onHeapProps + "  offHeapProps:" + offHeapProps;
  }

  public long getMaxSize() {
    return maxSize;
  }

  public long getBlockSize() {
    return blockSize;
  }

  public CacheType getType() {
    return type;
  }

  public Long getLogInterval(TimeUnit unit) {
    return logInterval == null ? null : unit.convert(Long.valueOf(logInterval), TimeUnit.SECONDS);
  }
}
