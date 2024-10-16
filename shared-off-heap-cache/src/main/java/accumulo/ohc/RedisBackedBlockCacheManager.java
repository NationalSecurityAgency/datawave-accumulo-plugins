package accumulo.ohc;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.spi.cache.BlockCache;
import org.apache.accumulo.core.spi.cache.BlockCacheManager;
import org.apache.accumulo.core.spi.cache.CacheType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisBackedBlockCacheManager extends BlockCacheManager {

  private static final Logger LOG = LoggerFactory.getLogger(RedisBackedBlockCacheManager.class);

  private Timer timer = null;

  @Override
  public void start(Configuration conf) {
    super.start(conf);

    for (CacheType type : CacheType.values()) {
      RedisBackedBlockCacheConfiguration cc = new RedisBackedBlockCacheConfiguration(conf, type);
      Long interval = cc.getLogInterval(TimeUnit.MILLISECONDS);
      if(interval != null) {
        if(timer == null) {
          timer = new Timer(true);
        }

        RedisBackedBlockCache cmbc = (RedisBackedBlockCache) getBlockCache(type);
        TimerTask task =new TimerTask() {
          @Override
          public void run() {
            cmbc.logStats();
          }
        };

        timer.scheduleAtFixedRate(task, interval, interval);
      }
    }
  }

  @Override
  protected BlockCache createCache(Configuration conf, CacheType type) {
    RedisBackedBlockCacheConfiguration cc = new RedisBackedBlockCacheConfiguration(conf, type);
    LOG.info("Creating {} cache with configuration {}", type, cc);
    return new RedisBackedBlockCache(cc);
  }

  @Override
  public void stop() {
    if(timer != null) {
      timer.cancel();
    }

    for (CacheType type : CacheType.values()) {
      RedisBackedBlockCache cmbc = (RedisBackedBlockCache) getBlockCache(type);
      cmbc.stop();
    }

    super.stop();
  }

}
