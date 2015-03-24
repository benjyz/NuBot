/*
 * Copyright (C) 2015 Nu Development Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.nubits.nubot.testsmanual;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import com.nubits.nubot.bot.Global;
import com.nubits.nubot.models.CurrencyList;
import com.nubits.nubot.options.NuBotConfigException;
import com.nubits.nubot.options.NuBotOptions;
import com.nubits.nubot.options.ParseOptions;
import com.nubits.nubot.strategy.Primary.NuBotSimple;
import com.nubits.nubot.strategy.Secondary.NuBotSecondary;
import com.nubits.nubot.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;


/**
 * the test launcher class for all functions
 */
public class TestLaunch {


    static String configfile = "config/myconfig/bitspark.json";

    private static final Logger LOG = LoggerFactory.getLogger(TestLaunch.class.getName());

    private static boolean runui = false;


    /**
     * Start the NuBot. start if config is valid and other instance is running
     *
     * @param args a list of valid arguments
     */
    public static void main(String args[]) {

        //TestWrappers.runTests();

        //log info
        LoggerContext loggerContext = ((ch.qos.logback.classic.Logger) LOG).getLoggerContext();
        URL mainURL = ConfigurationWatchListUtil.getMainWatchURL(loggerContext);

        LOG.info("Logback used '{}' as the configuration file.", mainURL);

        List<ch.qos.logback.classic.Logger> llist = loggerContext.getLoggerList();

        Iterator<ch.qos.logback.classic.Logger> it = llist.iterator();
        while (it.hasNext()){
            ch.qos.logback.classic.Logger l = it.next();
            LOG.info("" + l);
        }

        String wdir = System.getProperty("user.dir");

        File f = new File(wdir + "/" + "logs/current"); // current directory

        File[] files = f.listFiles();
        String currentLogfoldername = "";
        for (File file : files) {
            if (file.isDirectory()) {
                LOG.info("directory:");
                currentLogfoldername = file.getName();
                LOG.info(currentLogfoldername);
                Global.sessionLogFolders = "logs" + "/" + "current" + "/" + currentLogfoldername;
            }
        }

        Global.sessionStarted = System.currentTimeMillis();



        LOG.info("main. with args " + args.length);



        mainLaunch(configfile, false);

    }

    /**
     * main launch of a bot
     *
     * @param configfile
     * @param runui
     */
    public static void mainLaunch(String configfile, boolean runui) {
        LOG.info("main launch. with configfile " + configfile + " " + runui);

        NuBotOptions nuopt = null;

        try {
            //Check if NuBot has valid parameters and quit if it doesn't
            nuopt = ParseOptions.parseOptionsSingle(configfile);
        } catch (NuBotConfigException e) {
            exitWithNotice("" + e);
        }


        LOG.info("-- new main launched --");

        LOG.info("** run command line **");
        executeBot(nuopt);

    }


    /**
     * exit application and notify user
     *
     * @param msg
     */
    private static void exitWithNotice(String msg) {
        LOG.error(msg);
        System.exit(0);
    }


    /**
     * execute a NuBot based on valid options. Also make sure only one NuBot is running
     *
     * @param opt
     */
    public static void executeBot(NuBotOptions opt) {

        Global.mainThread = Thread.currentThread();

        Global.createShutDownHook();

        //exit if already running or show info to user
        if (Global.running) {
            exitWithNotice("NuBot is already running. Make sure to terminate other instances.");
        } else {
            if (opt.requiresSecondaryPegStrategy()) {
                LOG.info("creating secondary bot");
                NuBotSecondary bot = new NuBotSecondary();
                bot.execute(opt);
            } else {
                LOG.info("creating simple bot");
                NuBotSimple bot = new NuBotSimple();
                bot.execute(opt);
            }
        }

    }


}
