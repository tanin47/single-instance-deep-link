package tanin.singleinstancedeeplink;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class MultipleInstancesTest extends Base {
  @Test
  void secondInstanceStop() throws Exception {
    SingleInstanceDeepLink.SOCKET_FILE_DIR = Files.createTempDirectory("s").toFile();
    var socketFilenamePrefix = "single";
    AtomicReference<String[]> invokedArgsFromFirst = new AtomicReference<>();

    var firstResult = SingleInstanceDeepLink.setupSocketOrCommunicate(
      new String[] {"first"},
      socketFilenamePrefix,
      invokedArgsFromFirst::set,
      2
    );
    assertEquals(SingleInstanceDeepLink.Operation.SHOULD_CONTINUE, firstResult);
    Thread.sleep(500);

    AtomicReference<String[]> invokedArgsFromSecond = new AtomicReference<>();
    var secondResult = SingleInstanceDeepLink.setupSocketOrCommunicate(
      new String[] {"second", "something"},
      socketFilenamePrefix,
      invokedArgsFromSecond::set,
      2
    );
    assertEquals(SingleInstanceDeepLink.Operation.SHOULD_EXIT, secondResult);

    waitUntil(() -> {
      assertNotNull(invokedArgsFromFirst.get());
      assertEquals(List.of("second", "something"), Arrays.stream(invokedArgsFromFirst.get()).toList());
      assertEquals(List.of("second", "something"), Arrays.stream(invokedArgsFromFirst.get()).toList());
    });
    assertNull(invokedArgsFromSecond.get());
  }
}
