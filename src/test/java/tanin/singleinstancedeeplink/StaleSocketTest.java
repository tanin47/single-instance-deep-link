package tanin.singleinstancedeeplink;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class StaleSocketTest extends Base {
  @Test
  void handleStaleSocketFile() throws Exception {
    SingleInstanceDeepLink.SOCKET_FILE_DIR = Files.createTempDirectory("s").toFile();
    var socketFilenamePrefix = "single";

    var dummyFile = Files.createFile(SingleInstanceDeepLink.SOCKET_FILE_DIR.toPath().resolve(socketFilenamePrefix + ".sock"));
    Files.writeString(dummyFile, "dummy-content");
    assertEquals("dummy-content", Files.readString(dummyFile));
    assertEquals(13L, Files.size(dummyFile));

    var firstResult = SingleInstanceDeepLink.setupSocketOrCommunicate(
      new String[] {"first"},
      socketFilenamePrefix,
      args -> {},
      2
    );
    assertEquals(SingleInstanceDeepLink.Operation.SHOULD_CONTINUE, firstResult);
    assertNotEquals(13L, Files.size(dummyFile));
  }
}
