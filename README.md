Enforcing a single instance for Java app (with deep link support)
------------------------------------------------------------------

A zero-external-dependency and lightweight library for enforcing a single instance on a Java app with deep link support i.e. a support for
custom URI scheme handler e.g. `backdoor://auth?key=1234`. 


Why do we need it?
-------------------

In a desktop app, we may have a need to authenticate a user through a browser login. The flow is typically:

1. The app opens a browser to: `https://yourwebsite.com/login`.
2. The user logins
3. The website opens the URL to `yourappcustomurischeme://login?authKey=<somekey>`.
4. The app uses the `authKey` to authenticate against your server.

To authenticate the user securely, your app needs to achieve the followings:

1. Register `yourappcustomurischeme` with the current OS
2. Be able to receive `yourappcustomurischeme://login?authKey=<somekey>`
3. Enforce a single instance of your app. Otherwise, there would be multiple instances to login into.

This library handles the above 3 items for you for *Windows* and *Linux* (coming soon).

For MacOS, the library is not needed because MacOS supports the above 3 items natively through 
`java.awt.Desktop.setOpenURIHandler(..)` and `LSMultipleInstancesProhibited` with 
either `ASWebAuthenticationSession` or `CFBundleURLSchemes`. However, Windows and Linux don't provide similar native approaches.
That's why we need this library.

As a side note, [Electron](https://www.electronjs.org/docs/latest/tutorial/launch-app-from-url-in-another-app) 
also implements a special logic to support the deep link mechanism on Windows and Linux.


How to use
-----------

Add the dependency to your project:

```
<dependency>
    <groupId>io.github.tanin47</groupId>
    <artifactId>single-instance-app</artifactId>
    <version>1.0.0</version>
</dependency>
```

Put the below code in your main method:

```
public static void main(String[] args) {
    ... your code ...
    
    SingleInstanceApp.setUp(
      args,
      "singleinstanceapp", // The custom URI scheme for your app
      (anotherInstanceArgs) -> {
        logger.info("Callback was invoked with: " + String.join(" ", anotherInstanceArgs));
      }
    );
    
    ... your code ...
}
```

How to develop
---------------

There are 3 ways of testing: (1) unit tests, (2) run the app from Gradle, and (3) run the packaged app


### Run unit tests

Run `./gradlew test` in order to run the tests


### Run the app from Gradle

Open one terminal and run `./gradlew run`.

Open another terminal and run `./gradlew run`.

We'll see that the second run will send its args to the first run and exit.


### Run the package app

Testing a packaged app is important to ensure it works on a production app.

1. Run `./gradlew jpackage` in order to package the app.
2. Run the app from the terminal
3. Open a browser, type `singleinstanceapp://test` in the URL input box, and press enter

You should see the first app receives `singleinstanceapp://test`.
