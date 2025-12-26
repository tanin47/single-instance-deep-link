package tanin.singleinstancedeeplink;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StaleSocketTest extends Base {
  @Test
  void handleStaleSocketFile() throws Exception {
    SingleInstanceDeepLink.SOCKET_FILE_DIR = Files.createTempDirectory("s").toFile();
    var _ignored = Files.createFile(SingleInstanceDeepLink.SOCKET_FILE_DIR.toPath().resolve("app.sock"));

    var firstResult = SingleInstanceDeepLink.setupSocketOrCommunicate(
      new String[] {"first"},
      args -> {},
      2
    );
    assertEquals(SingleInstanceDeepLink.Operation.SHOULD_CONTINUE, firstResult);
  }
}
