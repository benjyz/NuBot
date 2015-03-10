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
package com.nubits.nubot.testsmanual;


import com.nubits.nubot.notifications.HipChatNotifications;
import com.nubits.nubot.notifications.MailNotifications;
import io.evanwong.oss.hipchat.v2.rooms.MessageColor;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class TestNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(TestNotifications.class.getName());

    public static void main(String[] a) {

        MailNotifications.send("desrever.nu@gmail.com", "Test Title", "Test Message");
        MailNotifications.sendCritical("desrever.nu@gmail.com", "Test Critial Title", "Test critical message");
        //USES RED FOR CRITICAL, ANYTHING ELSE FOR STANDARD
        HipChatNotifications.sendMessageCritical("Critical notification test");
        HipChatNotifications.sendMessage("Standard notification test", MessageColor.GREEN);

    }
}