package com.nubits.nubot.options;

import com.nubits.nubot.global.Constant;
import com.nubits.nubot.notifications.MailNotifications;

/**
 * Default options for NuBot
 */
public class NuBotOptionsDefault {

    //double wallchangeThreshold = 0.5;
    //double spread = 0;
    //double distanceThreshold = 10;

    public static NuBotOptions defaultFactory() {

        NuBotOptions opt = new NuBotOptions();
        opt.dualSide = true;
        opt.apiKey = "";
        opt.apiSecret = "";
        opt.rpcUser = "";
        opt.rpcPass = "";
        opt.nudIp = "127.0.0.1";
        opt.priceIncrement = 0.0003;
        opt.txFee = 0.2;
        opt.submitLiquidity = false;
        opt.executeOrders = false;
        opt.sendHipchat = true;
        opt.sendMails = MailNotifications.MAIL_LEVEL_SEVERE;
        opt.mailRecipient = "";
        opt.emergencyTimeout = 30;
        opt.keepProceeds = 0.0;
        opt.distributeLiquidity = false;
        opt.secondarypeg = false;
        opt.pair = Constant.NBT_USD;
        opt.verbose = false;
        opt.sendHipchat = true;
        opt.multipleCustodians = false;
        opt.maxSellVolume = 0;
        opt.maxBuyVolume = 0;
        opt.nudPort = 9091;
        opt.nudIp = "127.0.0.1";
        return opt;
    }
}
