package accumulo.ohc;

import java.util.function.Supplier;

import org.apache.accumulo.core.file.blockfile.cache.impl.ClassSize;
import org.apache.accumulo.core.file.blockfile.cache.impl.SizeConstants;
import org.apache.accumulo.core.spi.cache.CacheEntry.Weighable;

final class Block {

  private final byte[] buffer;
  private Weighable index;
  private volatile int lastIndexWeight;

  Block(byte[] buffer) {
    this.buffer = buffer;
    this.lastIndexWeight = buffer.length / 100;
  }

  int weight() {
    int indexWeight = lastIndexWeight + SizeConstants.SIZEOF_INT + ClassSize.REFERENCE;
    return indexWeight + ClassSize.align(getBuffer().length) + SizeConstants.SIZEOF_LONG + ClassSize.REFERENCE + ClassSize.OBJECT + ClassSize.ARRAY;
  }

  public byte[] getBuffer() {
    return buffer;
  }

  @SuppressWarnings("unchecked")
  public synchronized <T extends Weighable> T getIndex(Supplier<T> supplier) {
    if (index == null) {
      index = supplier.get();
    }

    return (T) index;
  }

  public synchronized boolean indexWeightChanged() {
    if (index != null) {
      int indexWeight = index.weight();
      if (indexWeight != lastIndexWeight) {
        lastIndexWeight = indexWeight;
        return true;
      }
    }

    return false;
  }
}