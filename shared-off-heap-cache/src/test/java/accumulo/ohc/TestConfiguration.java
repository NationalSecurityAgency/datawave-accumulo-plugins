package accumulo.ohc;

import java.util.Map;

import org.apache.accumulo.core.spi.cache.BlockCacheManager.Configuration;
import org.apache.accumulo.core.spi.cache.CacheType;
import org.junit.Assert;

public class TestConfiguration implements Configuration {

  private CacheType expectedType;
  private long maxSize;
  private Map<String,String> props;

  public TestConfiguration(CacheType expectedType, long maxSize, Map<String,String> props) {
    this.expectedType = expectedType;
    this.maxSize = maxSize;
    this.props = props;
  }

  @Override
  public long getMaxSize(CacheType type) {
    Assert.assertEquals(expectedType, type);
    return maxSize;
  }

  @Override
  public long getBlockSize() {
    return 1000000;
  }

  @Override
  public Map<String,String> getProperties(String prefix, CacheType type) {
    Assert.assertEquals(expectedType, type);
    return props;
  }

}