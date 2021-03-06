package processing.mode.android;

import processing.app.Platform;
import processing.app.Preferences;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.core.PApplet;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;


class AndroidSDK {
  private final File folder;
  private final File tools;
  private final File platforms;
  private final File platformTools;
  private final File androidTool;

  static private boolean downloading;

  private static final String ANDROID_SDK_PRIMARY =
    "Is the Android SDK installed?";

  private static final String ANDROID_SDK_SECONDARY =
    "The Android SDK does not appear to be installed, <br>" +
    "because the ANDROID_SDK variable is not set. <br>" +
    "If it is installed, click “Locate SDK path” to select the <br>" +
    "location of the SDK, or “Download SDK” to let <br>" +
    "Processing download SDK automatically.<br><br>" +
    "If you want to download SDK manually, you can visit <br>"+
    "download site at http://developer.android.com/sdk.";

  private static final String SELECT_ANDROID_SDK_FOLDER =
    "Choose the location of the Android SDK";

  private static final String NOT_ANDROID_SDK =
    "The selected folder does not appear to contain an Android SDK,\n" +
    "or the SDK needs to be updated to the latest version.";

//  private static final String ANDROID_SDK_URL =
//    "http://developer.android.com/sdk/";


  public AndroidSDK(File folder) throws BadSDKException, IOException {
    this.folder = folder;
    if (!folder.exists()) {
      throw new BadSDKException(folder + " does not exist");
    }

    tools = new File(folder, "tools");
    if (!tools.exists()) {
      throw new BadSDKException("There is no tools folder in " + folder);
    }

    platformTools = new File(folder, "platform-tools");
    if (!platformTools.exists()) {
      throw new BadSDKException("There is no platform-tools folder in " + folder);
    }

    platforms = new File(folder, "platforms");
    if (!platforms.exists()) {
      throw new BadSDKException("There is no platforms folder in " + folder);
    }

    androidTool = findAndroidTool(tools);

    String path = Platform.getenv("PATH");

    Platform.setenv("ANDROID_SDK", folder.getCanonicalPath());
    path = platformTools.getCanonicalPath() + File.pathSeparator +
      tools.getCanonicalPath() + File.pathSeparator + path;

    String javaHomeProp = System.getProperty("java.home");
    File javaHome = new File(javaHomeProp).getCanonicalFile();
    Platform.setenv("JAVA_HOME", javaHome.getCanonicalPath());

    path = new File(javaHome, "bin").getCanonicalPath() + File.pathSeparator + path;
    Platform.setenv("PATH", path);

    checkDebugCertificate();
  }


  /**
   * If a debug certificate exists, check its expiration date. If it's expired,
   * remove it so that it doesn't cause problems during the build.
   */
  protected void checkDebugCertificate() {
    File dotAndroidFolder = new File(System.getProperty("user.home"), ".android");
    File keystoreFile = new File(dotAndroidFolder, "debug.keystore");
    if (keystoreFile.exists()) {
      // keytool -list -v -storepass android -keystore debug.keystore
      ProcessHelper ph = new ProcessHelper(new String[] {
        "keytool", "-list", "-v",
        "-storepass", "android",
        "-keystore", keystoreFile.getAbsolutePath()
      });
      try {
        ProcessResult result = ph.execute();
        if (result.succeeded()) {
          // Valid from: Mon Nov 02 15:38:52 EST 2009 until: Tue Nov 02 16:38:52 EDT 2010
          String[] lines = PApplet.split(result.getStdout(), '\n');
          for (String line : lines) {
            String[] m = PApplet.match(line, "Valid from: .* until: (.*)");
            if (m != null) {
              String timestamp = m[1].trim();
              // "Sun Jan 22 11:09:08 EST 2012"
              // Hilariously, this is the format of Date.toString(), however
              // it isn't the default for SimpleDateFormat or others. Yay!
              DateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
              try {
                Date date = df.parse(timestamp);
                long expireMillis = date.getTime();
                if (expireMillis < System.currentTimeMillis()) {
                  System.out.println("Removing expired debug.keystore file.");
                  String hidingName = "debug.keystore." + AndroidMode.getDateStamp(expireMillis);
                  File hidingFile = new File(keystoreFile.getParent(), hidingName);
                  if (!keystoreFile.renameTo(hidingFile)) {
                    System.err.println("Could not remove the expired debug.keystore file.");
                    System.err.println("Please remove the file " + keystoreFile.getAbsolutePath());
                  }
//                } else {
//                  System.out.println("Nah, that won't expire until " + date); //timestamp);
                }
              } catch (ParseException pe) {
                System.err.println("The date “" + timestamp + "” could not be parsed.");
                System.err.println("Please report this as a bug so we can fix it.");
              }
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  public File getAndroidTool() {
    return androidTool;
  }


  public String getAndroidToolPath() {
    return androidTool.getAbsolutePath();
  }


  public File getSdkFolder() {
    return folder;
  }


  /*
  public File getToolsFolder() {
    return tools;
  }
  */


  public File getPlatformToolsFolder() {
    return platformTools;
  }


  /**
   * Checks a path to see if there's a tools/android file inside, a rough check
   * for the SDK installation. Also figures out the name of android/android.bat
   * so that it can be called explicitly.
   */
  private static File findAndroidTool(final File tools) throws BadSDKException {
    if (new File(tools, "android.exe").exists()) {
      return new File(tools, "android.exe");
    }
    if (new File(tools, "android.bat").exists()) {
      return new File(tools, "android.bat");
    }
    if (new File(tools, "android").exists()) {
      return new File(tools, "android");
    }
    throw new BadSDKException("Cannot find the android tool in " + tools);
  }


  /**
   * Check for the ANDROID_SDK environment variable. If the variable is set,
   * and refers to a legitimate Android SDK, then use that and save the pref.
   *
   * Check for a previously set android.sdk.path preference. If the pref
   * is set, and refers to a legitimate Android SDK, then use that.
   *
   * Prompt the user to select an Android SDK. If the user selects a
   * legitimate Android SDK, then use that, and save the preference.
   *
   * @return an AndroidSDK
   * @throws BadSDKException
   * @throws IOException
   */
  public static AndroidSDK load() throws IOException {
    // The environment variable is king. The preferences.txt entry is a page.
    final String sdkEnvPath = Platform.getenv("ANDROID_SDK");
    if (sdkEnvPath != null) {
      try {
        final AndroidSDK androidSDK = new AndroidSDK(new File(sdkEnvPath));
        // Set this value in preferences.txt, in case ANDROID_SDK
        // gets knocked out later. For instance, by that pesky Eclipse,
        // which nukes all env variables when launching from the IDE.
        Preferences.set("android.sdk.path", sdkEnvPath);
        return androidSDK;
      } catch (final BadSDKException drop) { }
    }

    // If android.sdk.path exists as a preference, make sure that the folder
    // is not bogus, otherwise the SDK may have been removed or deleted.
    final String sdkPrefsPath = Preferences.get("android.sdk.path");
    if (sdkPrefsPath != null) {
      try {
        final AndroidSDK androidSDK = new AndroidSDK(new File(sdkPrefsPath));
        // Set this value in preferences.txt, in case ANDROID_SDK
        // gets knocked out later. For instance, by that pesky Eclipse,
        // which nukes all env variables when launching from the IDE.
        Preferences.set("android.sdk.path", sdkPrefsPath);
        return androidSDK;
      } catch (final BadSDKException wellThatsThat) {
        Preferences.unset("android.sdk.path");
      }
    }
    return null;
  }


  static public AndroidSDK locate(final Frame window, final AndroidMode androidMode)
      throws BadSDKException, IOException {
    final int result = showLocateDialog(window);
    if (result == JOptionPane.CANCEL_OPTION) {
      throw new BadSDKException("User canceled attempt to find SDK.");
    }
    if (result == JOptionPane.YES_OPTION) {
      // here we are going to download sdk automatically
      //Base.openURL(ANDROID_SDK_URL);
      //throw new BadSDKException("No SDK installed.");

      return download(androidMode);
    }
    while (true) {
      // TODO this is really a yucky way to do this stuff. fix it.
      File folder = selectFolder(SELECT_ANDROID_SDK_FOLDER, null, window);
      if (folder == null) {
        throw new BadSDKException("User canceled attempt to find SDK.");
      }
      try {
        final AndroidSDK androidSDK = new AndroidSDK(folder);
        Preferences.set("android.sdk.path", folder.getAbsolutePath());
        return androidSDK;

      } catch (final BadSDKException nope) {
        JOptionPane.showMessageDialog(window, NOT_ANDROID_SDK);
      }
    }
  }

  static boolean isDownloading() {
    return downloading;
  }

  static public AndroidSDK download(final AndroidMode androidMode) throws BadSDKException {
    // TODO This is never set back to false once true [fry 150813]
    downloading = true;

    EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        SDKDownloader downloader = new SDKDownloader(androidMode);
        downloader.startDownload();
      }
    });
    return null;
  }

  static public int showLocateDialog(Frame editor) {
    // Pane formatting adapted from the Quaqua guide
    // http://www.randelshofer.ch/quaqua/guide/joptionpane.html
    JOptionPane pane =
        new JOptionPane("<html> " +
            "<head> <style type=\"text/css\">"+
            "b { font: 13pt \"Lucida Grande\" }"+
            "p { font: 11pt \"Lucida Grande\"; margin-top: 8px; width: 300px }"+
            "</style> </head>" +
            "<b>" + ANDROID_SDK_PRIMARY + "</b>" +
            "<p>" + ANDROID_SDK_SECONDARY + "</p>",
            JOptionPane.QUESTION_MESSAGE);

    String[] options = new String[] {
        "Download SDK automatically", "Locate SDK path manually"
    };
    pane.setOptions(options);

    // highlight the safest option ala apple hig
    pane.setInitialValue(options[0]);

    JDialog dialog = pane.createDialog(editor, null);
    dialog.setVisible(true);

    Object result = pane.getValue();
    if (result == options[0]) {
      return JOptionPane.YES_OPTION;
    } else if (result == options[1]) {
      return JOptionPane.NO_OPTION;
    } else {
      return JOptionPane.CLOSED_OPTION;
    }
  }

  // this was banished from Base because it encourages bad practice.
  // TODO figure out a better way to handle the above.
  static public File selectFolder(String prompt, File folder, Frame frame) {
    if (Platform.isMacOS()) {
      if (frame == null) frame = new Frame(); //.pack();
      FileDialog fd = new FileDialog(frame, prompt, FileDialog.LOAD);
      if (folder != null) {
        fd.setDirectory(folder.getParent());
        //fd.setFile(folder.getName());
      }
      System.setProperty("apple.awt.fileDialogForDirectories", "true");
      fd.setVisible(true);
      System.setProperty("apple.awt.fileDialogForDirectories", "false");
      if (fd.getFile() == null) {
        return null;
      }
      return new File(fd.getDirectory(), fd.getFile());

    } else {
      JFileChooser fc = new JFileChooser();
      fc.setDialogTitle(prompt);
      if (folder != null) {
        fc.setSelectedFile(folder);
      }
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

      int returned = fc.showOpenDialog(frame);
      if (returned == JFileChooser.APPROVE_OPTION) {
        return fc.getSelectedFile();
      }
    }
    return null;
  }


  private static final String ADB_DAEMON_MSG_1 = "daemon not running";
  private static final String ADB_DAEMON_MSG_2 = "daemon started successfully";

  public static ProcessResult runADB(final String... cmd)
  throws InterruptedException, IOException {
    final String[] adbCmd;
    if (!cmd[0].equals("adb")) {
      adbCmd = PApplet.splice(cmd, "adb", 0);
    } else {
      adbCmd = cmd;
    }
    // printing this here to see if anyone else is killing the adb server
    if (processing.app.Base.DEBUG) {
      PApplet.printArray(adbCmd);
    }
//    try {
    ProcessResult adbResult = new ProcessHelper(adbCmd).execute();
    // Ignore messages about starting up an adb daemon
    String out = adbResult.getStdout();
    if (out.contains(ADB_DAEMON_MSG_1) && out.contains(ADB_DAEMON_MSG_2)) {
      StringBuilder sb = new StringBuilder();
      for (String line : out.split("\n")) {
        if (!out.contains(ADB_DAEMON_MSG_1) &&
            !out.contains(ADB_DAEMON_MSG_2)) {
          sb.append(line).append("\n");
        }
      }
      return new ProcessResult(adbResult.getCmd(),
                               adbResult.getResult(),
                               sb.toString(),
                               adbResult.getStderr(),
                               adbResult.getTime());
    }
    return adbResult;
//    } catch (IOException ioe) {
//      ioe.printStackTrace();
//      throw ioe;
//    }
  }

  static class SDKTarget {
    public int version = 0;
    public String name;
  }

  public ArrayList<SDKTarget> getAvailableSdkTargets() throws IOException {
    ArrayList<SDKTarget> targets = new ArrayList<SDKTarget>();

    for(File platform : platforms.listFiles()) {
      File propFile = new File(platform, "build.prop");
      if (!propFile.exists()) continue;

      SDKTarget target = new SDKTarget();

      BufferedReader br = new BufferedReader(new FileReader(propFile));
      String line;
      while ((line = br.readLine()) != null) {
        String[] lineData = line.split("=");
        if (lineData[0].equals("ro.build.version.sdk")) {
          target.version = Integer.valueOf(lineData[1]);
        }

        if (lineData[0].equals("ro.build.version.release")) {
          target.name = lineData[1];
          break;
        }
      }
      br.close();

      if (target.version != 0 && target.name != null) targets.add(target);
    }

    return targets;
  }
}
