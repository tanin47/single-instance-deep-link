package tanin.singleinstanceapp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class MultipleInstancesTest extends Base {
  @Test
  void secondInstanceStop() throws Exception {
    SingleInstanceApp.SOCKET_FILE_DIR = Files.createTempDirectory("s").toFile();
    AtomicReference<String[]> invokedArgsFromFirst = new AtomicReference<>();

    var firstResult = SingleInstanceApp.setupSocketOrCommunicate(
      new String[] {"first"},
      invokedArgsFromFirst::set,
      2
    );
    assertEquals(SingleInstanceApp.Operation.SHOULD_CONTINUE, firstResult);
    Thread.sleep(500);

    AtomicReference<String[]> invokedArgsFromSecond = new AtomicReference<>();
    var secondResult = SingleInstanceApp.setupSocketOrCommunicate(
      new String[] {"second", "something"},
      invokedArgsFromSecond::set,
      2
    );
    assertEquals(SingleInstanceApp.Operation.SHOULD_EXIT, secondResult);

    waitUntil(() -> {
      assertNotNull(invokedArgsFromFirst.get());
      assertEquals(List.of("second", "something"), Arrays.stream(invokedArgsFromFirst.get()).toList());
      assertEquals(List.of("second", "something"), Arrays.stream(invokedArgsFromFirst.get()).toList());
    });
    assertNull(invokedArgsFromSecond.get());
  }
}
