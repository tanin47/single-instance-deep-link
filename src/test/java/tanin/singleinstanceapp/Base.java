package tanin.singleinstanceapp;

import java.util.NoSuchElementException;

public class Base {
  @FunctionalInterface
  public interface InterruptibleSupplier {
    boolean get() throws InterruptedException;
  }

  @FunctionalInterface
  public interface VoidFn {
    void invoke() throws InterruptedException;
  }

  public void waitUntil(VoidFn fn) throws InterruptedException {
    waitUntil(5000, fn);
  }

  public void waitUntil(long waitUntilTimeoutInMillis, VoidFn fn) throws InterruptedException {
    InterruptibleSupplier newFn = () -> {
      try {
        fn.invoke();
        return true;
      } catch (AssertionError e) {
        return false;
      }
    };

    var startTime = System.currentTimeMillis();
    while ((System.currentTimeMillis() - startTime) < waitUntilTimeoutInMillis) {
      if (newFn.get()) return;
      Thread.sleep(500);
    }

    fn.invoke();
  }
}
