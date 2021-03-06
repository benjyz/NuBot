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

package com.nubits.nubot.NTP;

import com.nubits.nubot.global.Settings;
import org.apache.commons.net.time.TimeUDPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;

public final class NTPClient {

    private static final Logger LOG = LoggerFactory.getLogger(NTPClient.class.getName());
    private ArrayList<String> hostnames;

    public NTPClient() {
    }

    private void initHosts() {
        hostnames = new ArrayList<>();
        hostnames.add("nist1-pa.ustiming.org");
        hostnames.add("nist-time-server.eoni.com");
        hostnames.add("time.nist.gov");
        hostnames.add("utcnist.colorado.edu");
        hostnames.add("nist.time.nosc.us");
    }

    public Date getTime(String host) {
        try {
            return getTimeImpl(host);
        } catch (IOException ex) {
            LOG.error("Cannot read the date from the time server " + host + "\n"
                    + ex.toString());
            return new Date();
        }
    }

    public Date getTime() {
        initHosts();
        boolean found = false;
        for (int i = 0; i < hostnames.size(); i++) {
            try {
                return getTimeImpl(hostnames.get(i));
            } catch (IOException ex) {
                LOG.warn("Problem with timeserver " + hostnames.get(i) + ""
                        + "\n" + ex.toString());
                if (i != hostnames.size() - 1) {
                    LOG.info("Trying next server");
                }
            }
        }
        if (!found) {
            LOG.error("Cannot update time after querying " + hostnames.size() + " timeservers. ");
        }
        return new Date(); //statement is never reached


    }

    private Date getTimeImpl(String host) throws IOException {
        Date toRet;
        TimeUDPClient client = new TimeUDPClient();
        // We want to timeout if a response takes longer than TIMEOUT seconds
        client.setDefaultTimeout(Settings.NTP_TIMEOUT);
        client.open();
        toRet = client.getDate(InetAddress.getByName(host));
        client.close();

        return toRet;
    }
}
