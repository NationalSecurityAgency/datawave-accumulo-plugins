package accumulo.ohc;

import static accumulo.ohc.RedisBackedBlockCacheConfiguration.OFF_HEAP_PREFIX;
import static accumulo.ohc.RedisBackedBlockCacheConfiguration.ON_HEAP_PREFIX;

import org.apache.accumulo.core.spi.cache.CacheType;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class BasicTest extends RedisServerTest {

  @Test
  public void testConfig() {

    TestConfiguration config = new TestConfiguration(CacheType.INDEX, 4000000,
        ImmutableMap.of(
            OFF_HEAP_PREFIX + "hostname", "localhost",
            OFF_HEAP_PREFIX + "port", "6379",
            OFF_HEAP_PREFIX + "password", PASSWORD));

    RedisBackedBlockCache obc = new RedisBackedBlockCache(new RedisBackedBlockCacheConfiguration(config, CacheType.INDEX));

    Assert.assertEquals(4000000, obc.getMaxHeapSize());
    Assert.assertTrue(obc.getMaxSize() >= 4000000);

    obc.stop();
  }

  @Test
  public void testCaffineConfig() {

    TestConfiguration config = new TestConfiguration(CacheType.INDEX, 5000000,
        ImmutableMap.of(
            OFF_HEAP_PREFIX + "hostname", "localhost",
            OFF_HEAP_PREFIX + "port", "6379",
            OFF_HEAP_PREFIX + "password", PASSWORD,
            ON_HEAP_PREFIX + "maximumWeight", "4000000"));
    RedisBackedBlockCache obc = new RedisBackedBlockCache(new RedisBackedBlockCacheConfiguration(config, CacheType.INDEX));

    Assert.assertEquals(4000000, obc.getMaxHeapSize());
    Assert.assertTrue(obc.getMaxSize() >= 4000000);

    obc.stop();
  }
}
