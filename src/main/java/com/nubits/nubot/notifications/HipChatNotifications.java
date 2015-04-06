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

package com.nubits.nubot.notifications;


import com.nubits.nubot.bot.Global;
import com.nubits.nubot.global.Passwords;
import io.evanwong.oss.hipchat.v2.HipChatClient;
import io.evanwong.oss.hipchat.v2.rooms.MessageColor;
import io.evanwong.oss.hipchat.v2.rooms.SendRoomNotificationRequestBuilder;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class HipChatNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(HipChatNotifications.class.getName());

    public static void sendMessage(String message, MessageColor color) {
        sendMessageImpl(message, color, false);
    }

    public static void sendMessageCritical(String message) {
        sendMessageImpl(message, MessageColor.RED, true);
    }

    private static void sendMessageImpl(String message, MessageColor color,
                                        boolean critical) {

        String publicAddress = "";


        publicAddress = Global.options.getNubitsAddress();
        boolean send = Global.options.isHipchat();
        if (!send) {
            return;
        }

        String sessionId = "";
        if (Global.sessionId != null) {
            sessionId = Global.sessionId;
        }

        String toSend = message + " ( " + sessionId + " - " + publicAddress + ")";

        try {
            if (critical) {
                HipChatClient hipchat = new HipChatClient(Passwords.HIPCHAT_CRITICAL_ROOM_TOKEN);
                SendRoomNotificationRequestBuilder builder = hipchat.prepareSendRoomNotificationRequestBuilder(Passwords.HIPCHAT_CRITICAL_ROOM_ID, toSend);
                builder.setColor(MessageColor.RED).setNotify(true).build().execute();

            } else {
                HipChatClient hipchat = new HipChatClient(Passwords.HIPCHAT_NOTIFICATIONS_ROOM_TOKEN);
                SendRoomNotificationRequestBuilder builder = hipchat.prepareSendRoomNotificationRequestBuilder(Passwords.HIPCHAT_NOTIFICATIONS_ROOM_ID, toSend);
                builder.setColor(color).setNotify(false).build().execute();

            }

        } catch (Exception e) {
            LOG.error("Not sending hipchat notification. Network problem");
        }

    }
}
