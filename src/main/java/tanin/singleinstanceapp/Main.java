package tanin.singleinstanceapp;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) throws Exception {
    SingleInstanceApp.VALIDATE_OS = false;
    SingleInstanceApp.SOCKET_FILE_DIR = new File(".");
    SingleInstanceApp.setUp(
      args,
      "fingertipai",
      (anotherInstanceArgs) -> {
        logger.info("Callback was invoked with: " + String.join(" ", anotherInstanceArgs));
      }
    );

    AtomicBoolean isRunning = new AtomicBoolean(true);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> { isRunning.set(false); }));
    final String[] SPINNER_FRAMES = new String[]{"|", "/", "-", "\\"};
    int currentFrame = 0;

    while (isRunning.get()) {
      Thread.sleep(100);
      System.out.print("\r" + SPINNER_FRAMES[currentFrame] + " running...");
      currentFrame = (currentFrame + 1) % SPINNER_FRAMES.length;
    }
    System.out.print("\r");
    System.out.println("Exiting...");
  }
}
