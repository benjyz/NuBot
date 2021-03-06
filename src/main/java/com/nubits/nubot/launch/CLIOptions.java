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

package com.nubits.nubot.launch;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse CLI arguments and provide a help menu using Apache Commons CLI
 */
public class CLIOptions {
    private static final Logger LOG = LoggerFactory.getLogger(CLIOptions.class.getName());

    public static final String GUI = "GUI";
    public static final String CFG = "cfg";

    public static final String USAGE_STRING = "java - jar NuBot -" + CFG + "=<path/to/options.json> [-" + GUI + "]";

    /**
     * Construct and provide GNU-compatible Options.
     *
     * @return Options expected from command-line of GNU form.
     */
    public Options constructGnuOptions() {
        final Options gnuOptions = new Options();

        Option UIOption = new Option(GUI, "graphic user interface", false, "Run with GUI");
        gnuOptions.addOption(UIOption);

        Option CfgFileOption = new Option(CFG, "configuration file", true, "Specify Configuration file");
        gnuOptions.addOption(CfgFileOption);

        return gnuOptions;
    }

    /**
     * Apply Apache Commons CLI GnuParser to command-line arguments.
     *
     * @param commandLineArguments Command-line arguments to be processed with
     *                             Gnu-style parser.
     */
    public CommandLine parseCommandLineArguments(final String[] commandLineArguments, Options gnuOptions) {
        final CommandLineParser cmdLineGnuParser = new GnuParser();

        CommandLine commandLine = null;
        try {
            commandLine = cmdLineGnuParser.parse(gnuOptions, commandLineArguments);
        } catch (ParseException parseException)  // checked exception
        {
            LOG.error("Encountered exception while parsing using GnuParser:\n"
                    + parseException.getMessage());
            MainLaunch.exitWithNotice("run nubot with \n" + USAGE_STRING);
        }
        return commandLine;
    }
}
