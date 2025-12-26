package tanin.singleinstanceapp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StaleSocketTest extends Base {
  @Test
  void handleStaleSocketFile() throws Exception {
    SingleInstanceApp.SOCKET_FILE_DIR = Files.createTempDirectory("s").toFile();
    var _ignored = Files.createFile(SingleInstanceApp.SOCKET_FILE_DIR.toPath().resolve("app.sock"));

    var firstResult = SingleInstanceApp.setupSocketOrCommunicate(
      new String[] {"first"},
      args -> {},
      2
    );
    assertEquals(SingleInstanceApp.Operation.SHOULD_CONTINUE, firstResult);
  }
}
