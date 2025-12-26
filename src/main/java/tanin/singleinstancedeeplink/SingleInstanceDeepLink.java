package tanin.singleinstancedeeplink;

import java.io.*;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.BindException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.net.StandardProtocolFamily;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SingleInstanceDeepLink {
  private static final Logger logger = Logger.getLogger(SingleInstanceDeepLink.class.getName());
  public interface OnAnotherInstanceActivated {
    void onActivated(String[] args);
  }

  public enum Operation {
    SHOULD_EXIT,
    SHOULD_CONTINUE
  }


  private static boolean isRunning = false;
  private static ServerSocketChannel serverSocket;
  private static Thread loop;

  public static File SOCKET_FILE_DIR = null;
  public static boolean VALIDATE_OS = true;
  public static String OS = System.getProperty("os.name").toLowerCase();

  public static void setUp(
    String[] args,
    String uriScheme,
    String appName,
    OnAnotherInstanceActivated onAnotherInstanceActivated
  ) throws Exception {
    if (VALIDATE_OS) {
      if (OS.contains("mac")) {
        throw new UnsupportedOperationException(
          "MacOS is not supported. For Mac, please use java.awt.Desktop.setOpenURIHandler(..) and Info.plist's LSMultipleInstancesProhibited with either ASWebAuthenticationSession or Info.plist's CFBundleURLSchemes."
        );
      }

      if (!OS.contains("win")) {
        throw new UnsupportedOperationException(
          "Only Windows is supported."
        );
      }
    }

    var result = setupSocketOrCommunicate(args, uriScheme, onAnotherInstanceActivated, 2);

    if (result == Operation.SHOULD_EXIT) {
      System.exit(0);
    }

    var commandPath = ProcessHandle.current().info().command();

    if (commandPath.isPresent()) {
      logger.info("The current executable path is: " + commandPath.get());
      var prefix = "HKCU\\SOFTWARE\\Classes\\" + uriScheme;
      addRegistry(prefix);
      addRegistry(prefix, "", appName);
      addRegistry(prefix, "URL Protocol", "");
      addRegistry(prefix + "\\shell");
      addRegistry(prefix + "\\shell\\open");
      addRegistry(prefix + "\\shell\\open\\command");
      addRegistry(prefix + "\\shell\\open\\command", "", "\\\"" + commandPath.get() + "\\\" \\\"%1\\\"");

    } else {
      logger.info("Could not determine the executable path. The registry keys are not set.");
    }
  }

  private static void addRegistry(String path) throws IOException, InterruptedException {
    runCmd(new String[] {"reg", "add", path, "/f"});
  }

  private static void addRegistry(String path, String key, String value) throws IOException, InterruptedException {
    var args = new ArrayList<String>();
    args.add("reg");
    args.add("add");
    args.add(path);

    if (key.isEmpty()) {
      args.add("/ve");
    } else {
      args.add("/v");
      args.add(key);
    }

    if (!value.isEmpty()) {
      args.add("/d");
      args.add(value);
    }
    args.add("/f");
    runCmd(args.toArray(String[]::new));
  }

  private static void runCmd(String[] cmds) throws IOException, InterruptedException {
    logger.info("Run cmd: " + String.join(" ", cmds));
    var process = Runtime.getRuntime().exec(cmds);

    try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
         BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

      String line;
      while ((line = stdInput.readLine()) != null) {
        logger.info("stdout: " + line);
      }
      while ((line = stdError.readLine()) != null) {
        logger.warning("stderr: " + line);
      }
    }

    if (process.waitFor() != 0) {
      throw new RuntimeException("Failed to add registry key");
    }
  }

  static Operation setupSocketOrCommunicate(
    String[] args,
    String socketFilenamePrefix,
    OnAnotherInstanceActivated onAnotherInstanceActivated,
    int retryCount
  ) throws Exception {
    if (SOCKET_FILE_DIR == null) {
      if (OS.contains("win")) {
        SOCKET_FILE_DIR = new File(System.getenv("LOCALAPPDATA"));
      } else if (OS.contains("mac")) {
        SOCKET_FILE_DIR = new File(".");
      } else if (OS.contains("nux")) {
        throw new UnsupportedOperationException("Linux is not supported.");
      }
    }

    var socketFilename = socketFilenamePrefix + ".sock";

    assert SOCKET_FILE_DIR != null;
    var socketPath = SOCKET_FILE_DIR.toPath().resolve(socketFilename).toFile();
    logger.info("Acquiring the socket at: " + socketPath.getCanonicalPath());
    var socketAddress = UnixDomainSocketAddress.of(socketPath.toPath());

    serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX);

    try {
      serverSocket.bind(socketAddress);
    } catch (Exception errorOpeningSocket) {
      if (errorOpeningSocket instanceof BindException && errorOpeningSocket.getMessage().contains("in use")) {
        logger.info("The socket at " + socketPath.getCanonicalPath() + " is already in use.");
      } else {
        logger.log(Level.WARNING, "Failed to open the socket at: " + socketPath.getCanonicalPath(), errorOpeningSocket);
      }
      logger.info("Connecting to the socket at: " + socketPath.getCanonicalPath());

      try (SocketChannel client = SocketChannel.open(StandardProtocolFamily.UNIX)) {
        client.connect(socketAddress);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(args);
        oos.flush();
        client.write(ByteBuffer.wrap(baos.toByteArray()));
      } catch (Exception errorConnectingSocket) {
        logger.log(Level.WARNING, "Failed to connect to the socket at: " + socketPath.getCanonicalPath(), errorConnectingSocket);

        if (retryCount > 0) {
          logger.warning("The socket is stale. Deleting " + socketPath.getCanonicalPath() + " and retrying...");
          var _ignored = socketPath.delete();
          return setupSocketOrCommunicate(args, socketFilenamePrefix, onAnotherInstanceActivated, retryCount - 1);
        } else {
          logger.log(Level.SEVERE, "Unable to neither open nor connect to the socket at: " + socketPath.getCanonicalPath() + ". Please delete the socket file and try again.", errorConnectingSocket);
          throw errorConnectingSocket;
        }
      }

      logger.info("This is the second instance. The args have been sent to the first instance. Exiting...");
      return Operation.SHOULD_EXIT;
    }


    loop = new Thread(() -> {
      isRunning = true;
      while (isRunning) {
        try (SocketChannel clientChannel = serverSocket.accept()) {
          ByteBuffer buffer = ByteBuffer.allocate(1024);
          ByteArrayOutputStream baos = new ByteArrayOutputStream();

          while (clientChannel.read(buffer) > 0) {
            buffer.flip();
            baos.write(buffer.array(), 0, buffer.limit());
            buffer.clear();
          }

          ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
          String[] receivedArgs = (String[]) ois.readObject();
          logger.info("Received args from another instance: " + String.join(", ", receivedArgs));
          onAnotherInstanceActivated.onActivated(receivedArgs);
        } catch (NotYetBoundException ignored) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException ignored2) {
            // ignored
          }
        } catch (Exception e) {
          logger.log(Level.SEVERE, "An error occurred in the socket server listening loop", e);
        }
      }
    });
    loop.setDaemon(true);
    loop.start();
    Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceDeepLink::shutdown));
    logger.info("This is the first instance. The socket server is accepting incoming connections...");
    return Operation.SHOULD_CONTINUE;
  }

  public static void shutdown() {
    isRunning = false;
    try {
      loop.interrupt();
    } catch (Exception ignored) {}
    try {
      serverSocket.close();
    } catch (IOException ignored) {}
  }
}
