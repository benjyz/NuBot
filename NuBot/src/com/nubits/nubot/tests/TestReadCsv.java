/*
 * Copyright (C) 2014-2015 Nu Development Team
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
package com.nubits.nubot.tests;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
import java.util.ArrayList;
import java.util.logging.Logger;

import org.junit.Test;

import com.nubits.nubot.utils.FileSystem;

public class TestReadCsv {

    private static final Logger LOG = Logger.getLogger(TestReadCsv.class.getName());
    private static final String TEST_FILE_PATH = "currencies.csv";

    @Test
    public void testReadCSV(){
        ArrayList<String[]> parsedCsv = FileSystem.parseCsvFromFile(TEST_FILE_PATH);
        for (int j = 0; j < parsedCsv.size(); j++) {
            String[] tempLine = parsedCsv.get(j);
            String message = "Line " + j + 1 + "/" + parsedCsv.size() + " = ";
            for (int i = 0; i < tempLine.length; i++) {
                message += "[" + i + "]=" + tempLine[i];
            }
            LOG.info(message);
        }

    }
}
