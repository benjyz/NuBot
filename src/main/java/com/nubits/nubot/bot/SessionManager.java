package com.nubits.nubot.bot;

import com.nubits.nubot.global.Settings;
import com.nubits.nubot.launch.MainLaunch;
import com.nubits.nubot.options.*;
import com.nubits.nubot.strategy.Primary.NuBotSimple;
import com.nubits.nubot.strategy.Secondary.NuBotSecondary;
import com.nubits.nubot.utils.FilesystemUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

/**
 * Session Manager starts and stops NuBots and manages the associated sessions
 */
public class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class.getName());

    private static final Logger sessionLOG = LoggerFactory.getLogger(Settings.SESSION_LOGGER_NAME);

    //private static final String MODE_NOTSTARTED = "MODE_NOTSTARTED";

    private static final String MODE_STARTING = "MODE_STARTING";

    private static final String MODE_HALTING = "MODE_HALTING";

    private static final String MODE_RUNNING = "MODE_RUNNING";

    private static final String MODE_HALTED = "MODE_HALTED";

    //public static String sessionMode = MODE_NOTSTARTED;
    public static String sessionMode = MODE_HALTED;

    public static DateTime sessionStartDate;

    public static long sessionStarted;

    public static long sessionStopped;

    /**
     * time it took from startup to first order
     */
    public static long startupDuration = -1;

    public static String sessionId;

    private static File sessionFile;

    private static String appFolder;

    private static boolean runonce = false;

    /**
     * set config in global
     *
     * @param configfile
     */
    public static void setConfigGlobal(String configfile, boolean skipValidation) {

        sessionLOG.debug("parsing options from " + configfile);

        try {
            //Check if NuBot has valid parameters and quit if it doesn't
            NuBotOptions opt = ParseOptions.parseOptionsSingle(configfile, skipValidation);
            LOG.debug("loading opt: " + opt.toString());
            Global.options = opt;
            Global.currentOptionsFile = configfile;
        } catch (NuBotConfigException e) {
            MainLaunch.exitWithNotice("" + e);
        }

    }

    /**
     * set config in global
     */
    public static void setConfigDefault() {

        NuBotOptions defaultOpt = NuBotOptionsDefault.defaultFactory();
        Global.options = defaultOpt;
        Global.currentOptionsFile = Settings.DEFAULT_CONFIG_FILE_PATH;
        SaveOptions.saveOptionsPretty(defaultOpt, Settings.DEFAULT_CONFIG_FILE_PATH);

    }

    /**
     * setup all the path for one session
     */
    private static void setupSessionPath() {

        //set up session dir
        String wdir = FilesystemUtils.getBotAbsolutePath();
        Global.sessionLogFolder = wdir + "/" + Global.sessionPath;

    }

    public static void sessionStart() {

        runonce = true;
        sessionStarted = System.currentTimeMillis();
        sessionStartDate = new DateTime();

        String timestamp =
                new java.text.SimpleDateFormat("yyyyMMdd HH:mm:ss").format(new Date());

        LOG.info("*** session *** starting at " + timestamp);

    }

    public static String startedString() {
        Date startdate = Date.from(Instant.ofEpochSecond(sessionStarted / 1000));
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        String dstr = df.format(startdate);
        return dstr;
    }

    public static boolean wasRunOnce() {
        return runonce;
    }

    /**
     * the time in milliseconds since a session was last stopped
     *
     * @return
     */
    public static long lastStopped() {
        if (sessionStopped != -1) {
            return System.currentTimeMillis() - sessionStopped;
        } else
            return -1;
    }

    /**
     * execute a NuBot based on valid options
     *
     * @param opt
     */
    public static void launchBot(NuBotOptions opt) throws NuBotRunException {

        Global.mainThread = Thread.currentThread();

        setupSessionPath();

        LOG.debug("execute bot depending on defined strategy");

        if (opt.requiresSecondaryPegStrategy()) {
            LOG.debug("creating secondary bot object");
            Global.bot = new NuBotSecondary();
        } else {
            LOG.debug("creating simple bot object");
            Global.bot = new NuBotSimple();
        }

        try {
            sessionStart();
            Global.bot.execute(opt);
            SessionManager.setModeRunning();
        } catch (NuBotRunException e) {
            throw e;
        }

    }


    /**
     * check whether other sessions are active via a temp file
     *
     * @return
     */
    /*public static boolean isSessionActive() {

        appFolder = System.getProperty("user.home") + "/" + Settings.APP_FOLDER;

        sessionFile = new File
                (appFolder, Settings.SESSION_FILE);
        //LOG.warn("checking " + sessionFile.getAbsolutePath() + " " + sessionFile.exists());
        return sessionFile.exists();
    }*/


    /**
     * create a session file in the app folder. increase session counter with each session
     * Note: session file gets marked as deleted after exit
     */
    /*public static void createSessionFile() {

        try {
            File appdir = new File(System.getProperty("user.home") + "/" + Settings.APP_FOLDER);
            if (!appdir.exists())
                appdir.mkdir();
        } catch (Exception e) {
        }


        appFolder = System.getProperty("user.home") + "/" + Settings.APP_FOLDER;
        sessionFile = new File
                (appFolder, Settings.SESSION_FILE);
        if (!sessionFile.exists()) {

            try {
                sessionFile.createNewFile();
                sessionFile.deleteOnExit();

            } catch (Exception e) {

            }
        }

    }*/

    /**
     * boolean for session in startup or shutdown mode, i.e. not running and not halted
     *
     * @return
     */
    private static boolean startupOrShutdown() {
        boolean startupOrShutdown = sessionMode.equals(MODE_HALTING) || sessionMode.equals(MODE_STARTING);
        return startupOrShutdown;
    }

    public static boolean isSessionRunning() {
        //TODO: remove sessionisactive and integrate here
        boolean r = sessionMode.equals(MODE_RUNNING);
        return r;
    }

    public static void setModeStarting() {
        LOG.debug("set mode to starting");
        sessionMode = MODE_STARTING;
    }

    public static void setModeHalting() {
        LOG.debug("set mode to halting");
        sessionMode = MODE_HALTING;
    }


    public static void setModeHalted() {
        LOG.debug("set mode to halted");
        sessionMode = MODE_HALTED;
    }

    public static void setModeRunning() {
        sessionMode = MODE_RUNNING;
    }

    public static boolean isModeHalting() {
        return sessionMode == MODE_HALTING;
    }

    public static boolean isModeHalted() {
        return sessionMode == MODE_HALTED;
    }

    public static boolean isModeActive() {
        return !sessionInterrupted() && !isModeHalted();
    }

    public static String getMode() {
        return sessionMode;
    }

    public static String getReadableMode() {
        String readableMode = "undefined";
        switch (sessionMode) {
           /* case MODE_NOTSTARTED:
                readableMode = "not started";
                break;*/
            case MODE_HALTED:
                readableMode = "halted";
                break;
            case MODE_RUNNING:
                readableMode = "running";
                break;
            case MODE_STARTING:
                readableMode = "starting";
                break;
            case MODE_HALTING:
                readableMode = "halting";
                break;

        }

        return readableMode;
    }

    public static boolean sessionInterrupted() {
        boolean ishalting = isModeHalting();
        boolean ishalted = isModeHalted();
        boolean interrupt = ishalting || ishalted;
        if (interrupt)
            LOG.debug("Session Interrupted catch");
        return interrupt;
    }


}
