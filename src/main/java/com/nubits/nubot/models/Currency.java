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

package com.nubits.nubot.models;

import com.nubits.nubot.global.Settings;
import com.nubits.nubot.utils.CSVtools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Objects;


public class Currency {

    private static final Logger LOG = LoggerFactory.getLogger(Currency.class.getName());
    private boolean fiat; // indicate whether its crypto or fiat
    private String code; // i.e USD
    private String extendedName; // the extended name where available


    /**
     *
     */
    public static Currency createCurrency(String code) {
        Currency toRet = null;
        ArrayList<String[]> currencyList = CSVtools.parseCsvFromFile(Settings.CURRENCY_FILE_PATH);
        boolean found = false;
        for (int j = 1; j < currencyList.size(); j++) {
            String[] tempLine = currencyList.get(j);

            if (tempLine[0].equalsIgnoreCase(code)) {
                return new Currency(Boolean.parseBoolean(tempLine[2]), tempLine[0], tempLine[1]);
            }
        }

        if (!found) {
            LOG.warn("Didn't find a currency with code " + code + " in lookup table " + Settings.CURRENCY_FILE_PATH
                    + "\nUpdate the currency file to avoid malfunctionings.");

            return new Currency(false, code, "");

        }

        return toRet;
    }

    private Currency(boolean fiat, String code, String extendedName) {
        this.fiat = fiat;
        this.code = code;
        this.extendedName = extendedName;
    }

    /**
     * @return
     */
    public boolean isFiat() {
        return fiat;
    }

    /**
     * @param fiat
     */
    public void setFiat(boolean fiat) {
        this.fiat = fiat;
    }

    /**
     * @return
     */
    public String getCode() {
        return code;
    }

    /**
     * @param code
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * @return
     */
    public String getExtendedName() {
        return extendedName;
    }

    /**
     * @param extendedName
     */
    public void setExtendedName(String extendedName) {
        this.extendedName = extendedName;
    }

    @Override
    public String toString() {
        return "Currency{fiat=" + fiat + ", code=" + code + ", extendedName=" + extendedName + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Currency other = (Currency) obj;
        if (!Objects.equals(this.code, other.code)) {
            return false;
        }
        return true;
    }
}
