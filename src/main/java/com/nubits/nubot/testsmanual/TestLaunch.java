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

import com.nubits.nubot.bot.Global;
import com.nubits.nubot.global.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;


/**
 * the test launcher class for all functions
 */
public class TestLaunch {


    static {
        System.setProperty("testlogfolder","abc");
        System.setProperty("logback.configurationFile", Settings.TEST_LAUNCH_XML);
    }

    static String configfile = "config/myconfig/bitspark.json";

    private static final Logger LOG = LoggerFactory.getLogger(TestLaunch.class.getName());

    private static boolean runui = false;


    /**
     * Start the NuBot. start if config is valid and other instance is running
     *
     * @param args a list of valid arguments
     */
    public static void main(String args[]) {



        Global.sessionLogFolders = "session_" + System.currentTimeMillis();

        MDC.put("session", "session_" + System.currentTimeMillis());

        LOG.info("bla");

        MDC.put("session", "session_" + System.currentTimeMillis());
        LOG.info("sssbla");

        //SessionManager.sessionLaunch(configfile, false);

    }




}

