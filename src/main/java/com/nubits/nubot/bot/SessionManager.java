package com.nubits.nubot.bot;

import com.nubits.nubot.global.Settings;
import com.nubits.nubot.launch.MainLaunch;
import com.nubits.nubot.options.*;
import com.nubits.nubot.strategy.Primary.NuBotSimple;
import com.nubits.nubot.strategy.Secondary.NuBotSecondary;
import com.nubits.nubot.utils.FilesystemUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.PeriodType;
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

    private static final Logger LOG = LoggerFactory.getLogger(MainLaunch.class.getName());

    private static final Logger sessionLOG = LoggerFactory.getLogger(Settings.SESSION_LOGGER_NAME);

    public static boolean sessionRunning = false;

    public static boolean sessionShuttingDown = false;

    public static DateTime sessionStartDate;

    public static long sessionStarted;

    public static long sessionStopped;

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
            LOG.debug("loading opt: " + opt.toStringNoKeys());
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
     * setup all the logging and storage for one session
     */
    private static void setupSession() {

        //set up session dir
        String wdir = FilesystemUtils.getBotAbsolutePath();

        Global.sessionLogFolder = wdir + "/" + Global.sessionPath;

        //create session file
        createSessionFile();

    }

    private static void sessionStart(){

        sessionRunning = true;
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

    public static String durationString() {
        Long diff = System.currentTimeMillis() - sessionStarted;

        /*long seconds = diff / 1000;
        LocalTime timeOfDay = LocalTime.ofSecondOfDay(seconds);
        String time = timeOfDay.toString();
        return time;*/

        PeriodType minutesEtc = PeriodType.time().withHoursRemoved();
        Period period = new Period(diff, minutesEtc);
        String periodString = String.format("%d min, %d sec",
                period.getMinutes(),
                period.getSeconds());
        return periodString;
    }

    public static boolean wasRunOnce() {
        return runonce;
    }

    /**
     * execute a NuBot based on valid options
     *
     * @param opt
     */
    public static void launchBot(NuBotOptions opt) throws NuBotRunException {

        Global.mainThread = Thread.currentThread();

        Global.createShutDownHook();

        setupSession();

        LOG.debug("execute bot depending on defined strategy");

        if (opt.requiresSecondaryPegStrategy()) {

            LOG.debug("creating secondary bot object");
            Global.bot = new NuBotSecondary();
            try {
                Global.bot.execute(opt);
                sessionStart();
            } catch (NuBotRunException e) {
                throw e;
            }

        } else {

            LOG.debug("creating simple bot object");
            Global.bot = new NuBotSimple();
            try {
                Global.bot.execute(opt);
                sessionStart();
            } catch (NuBotRunException e) {
                throw e;
            }

        }

    }


    /**
     * check whether other sessions are active via a temp file
     *
     * @return
     */
    public static boolean isSessionActive() {

        appFolder = System.getProperty("user.home") + "/" + Settings.APP_FOLDER;

        sessionFile = new File
                (appFolder, Settings.SESSION_FILE);
        //LOG.warn("checking " + sessionFile.getAbsolutePath() + " " + sessionFile.exists());
        return sessionFile.exists();
    }


    //TODO this is not clean but a workaround due to race conditions at startup
    public static boolean sessionRunningFinal(){
        if (sessionRunning && Global.orderManager != null && Global.balanceManager != null){
            return true;
        }
        return false;
    }

    /**
     * create a session file in the app folder. increase session counter with each session
     * Note: session file gets marked as deleted after exit
     */
    public static void createSessionFile() {

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

    }

    public static void removeSessionFile() {
        String sessionFileName = Settings.SESSION_FILE;
        sessionFile = new File(appFolder, sessionFileName);
        if (sessionFile.exists()) {
            try {
                sessionFile.delete();
            } catch (Exception e) {

            }
        }
    }


}
