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

package com.nubits.nubot.bot;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import com.nubits.nubot.RPC.NuSetup;
import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.exchanges.ExchangeFacade;
import com.nubits.nubot.exchanges.ExchangeLiveData;
import com.nubits.nubot.global.Settings;
import com.nubits.nubot.launch.MainLaunch;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.CurrencyList;
import com.nubits.nubot.models.Order;
import com.nubits.nubot.notifications.HipChatNotifications;
import com.nubits.nubot.options.NuBotConfigException;
import com.nubits.nubot.options.NuBotOptions;
import com.nubits.nubot.tasks.TaskManager;
import com.nubits.nubot.trading.TradeInterface;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.trading.wrappers.CcexWrapper;
import com.nubits.nubot.utils.FrozenBalancesManager;
import com.nubits.nubot.utils.Utils;
import io.evanwong.oss.hipchat.v2.rooms.MessageColor;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract NuBot. implements all primitives without the strategy itself
 */
public abstract class NuBotBase {

    /**
     * the strategy setup for specific NuBots to implement
     */
    abstract public void configureStrategy() throws NuBotConfigException;

    final static Logger LOG = LoggerFactory.getLogger(NuBotBase.class);

    /**
     * Logger for session data. called only once per session
     */
    private static final Logger sessionLOG = LoggerFactory.getLogger("SessionLOG");

    protected String mode;

    protected boolean liveTrading;


    /**
     * all setups
     */
    protected void setupAllConfig() {

        //Generate Bot Session unique id
        Global.sessionId = Utils.generateSessionID();
        LOG.info("Session ID = " + Global.sessionId);

        this.mode = "sell-side";
        if (Global.options.isDualSide()) {
            this.mode = "dual-side";
        }

        setupLog();

        setupSessionLog();

        setupSSL();

        setupExchange();

    }

    /**
     * setup all the logging and storage for one session
     */
    private static void setupSessionLog() {

        //log info
        LoggerContext loggerContext = ((ch.qos.logback.classic.Logger) LOG).getLoggerContext();
        URL mainURL = ConfigurationWatchListUtil.getMainWatchURL(loggerContext);

        LOG.info("Logback used '{}' as the configuration file.", mainURL);

        List<ch.qos.logback.classic.Logger> llist = loggerContext.getLoggerList();

        Iterator<ch.qos.logback.classic.Logger> it = llist.iterator();
        while (it.hasNext()) {
            ch.qos.logback.classic.Logger l = it.next();
            LOG.debug("" + l);
        }

        //set up session dir
        String wdir = System.getProperty("user.dir");

        String timeStamp = "" + System.currentTimeMillis();
        File ldir = new File(wdir + "/" + Settings.LOGS_PATH);
        if (!ldir.exists())
            ldir.mkdir();

        String sessiondir = wdir + "/" + Settings.LOGS_PATH + Settings.SESSION_LOG + timeStamp;
        File f = new File(sessiondir); // current directory

        f.mkdir();
        Global.sessionLogFolders = f.getAbsolutePath();
        Global.sessionStarted = System.currentTimeMillis();
        sessionLOG.debug("session start;" + Global.sessionLogFolders + ";" + Global.sessionStarted);


    }

    /**
     * setup logging
     */
    protected void setupLog() {

        //for debug purposes: determine the logback.xml file used
        LoggerContext loggerContext = ((ch.qos.logback.classic.Logger) LOG).getLoggerContext();
        URL mainURL = ConfigurationWatchListUtil.getMainWatchURL(loggerContext);
        LOG.debug("Logback used '{}' as the configuration file.", mainURL);

        //Disable hipchat debug logging https://github.com/evanwong/hipchat-java/issues/16
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");
    }

    protected void setupSSL() {
        LOG.info("Set up SSL certificates");
        boolean trustAllCertificates = false;
        if (Global.options.getExchangeName().equalsIgnoreCase(ExchangeFacade.INTERNAL_EXCHANGE_PEATIO)) {
            trustAllCertificates = true;
        }
        Utils.installKeystore(trustAllCertificates);
    }


    protected void setupExchange() {
        LOG.info("setup Exchange object");

        LOG.debug("Wrap the keys into a new ApiKeys object");
        ApiKeys keys = new ApiKeys(Global.options.getApiSecret(), Global.options.getApiKey());

        Global.exchange = new Exchange(Global.options.getExchangeName());

        LOG.debug("Create e ExchangeLiveData object to accommodate liveData from the exchange");
        ExchangeLiveData liveData = new ExchangeLiveData();
        Global.exchange.setLiveData(liveData);

        TradeInterface ti = null;
        try {
            ti = ExchangeFacade.getInterfaceByName(Global.options.getExchangeName(), keys, Global.exchange);
        } catch (Exception e) {
            MainLaunch.exitWithNotice("exchange unknown");
        }

        //TradeInterface ti = ExchangeFacade.getInterfaceByName(Global.options.getExchangeName());
        LOG.debug("Create a new TradeInterface object");
        ti.setKeys(keys);
        ti.setExchange(Global.exchange);


        //TODO handle on exchange level, not bot level
        if (Global.options.getExchangeName().equals(ExchangeFacade.CCEX)) {
            ((CcexWrapper) (ti)).initBaseUrl();
        }

        if (Global.options.getPair().getPaymentCurrency().equals(CurrencyList.NBT)) {
            Global.swappedPair = true;
        } else {
            Global.swappedPair = false;
        }

        LOG.info("Swapped pair mode : " + Global.swappedPair);

        String apibase = "";
        //TODO handle on exchange level, not bot level
        if (Global.options.getExchangeName().equalsIgnoreCase(ExchangeFacade.INTERNAL_EXCHANGE_PEATIO)) {
            ti.setApiBaseUrl(ExchangeFacade.INTERNAL_EXCHANGE_PEATIO_API_BASE);
        }

        //TODO exchange and tradeinterface are circular referenced
        Global.exchange.setTrade(ti);
        Global.exchange.getLiveData().setUrlConnectionCheck(Global.exchange.getTrade().getUrlConnectionCheck());


        //For a 0 tx fee market, force a price-offset of 0.1%
        ApiResponse txFeeResponse = Global.exchange.getTrade().getTxFee(Global.options.getPair());
        if (txFeeResponse.isPositive()) {
            double txfee = (Double) txFeeResponse.getResponseObject();
            if (txfee == 0) {
                LOG.warn("The bot detected a 0 TX fee : forcing a priceOffset of 0.1% [if required]");
                double maxOffset = 0.1;
                if (Global.options.getSpread() < maxOffset) {
                    Global.options.setSpread(maxOffset);
                }
            }
        }
    }


    protected void checkNuConn() throws NuBotConnectionException {

        LOG.info("Check connection with nud");
        if (Global.rpcClient.isConnected()) {
            LOG.info("RPC connection OK!");
        } else {
            //TODO: recover?
            throw new NuBotConnectionException("problem with nu connectivity");
        }
    }

    /**
     * execute the NuBot based on a configuration
     */
    public void execute(NuBotOptions opt) {

        LOG.debug("----- new session -----");

        LOG.info("Setting up NuBot version : " + Utils.versionName());

        //DANGER ZONE : This variable set to true will cause orders to execute
        if (opt.isExecuteOrders()) {
            liveTrading = true;
        } else {
            LOG.info("Trades will not be executed [executetrade:false]");
            liveTrading = false;
        }

        Global.options = opt;

        setupAllConfig();

        LOG.debug("Create a TaskManager ");
        Global.taskManager = new TaskManager();

        if (Global.options.isSubmitliquidity()) {
            NuSetup.setupNuRPCTask();
            NuSetup.startTask();
        }


        LOG.debug("Starting task : Check connection with exchange");
        int conn_delay = 1;
        Global.taskManager.getCheckConnectionTask().start(conn_delay);


        LOG.info("Waiting  a for the connectionThreads to detect connection");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            LOG.error(ex.toString());
        }

        //test setup exchange
        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getActiveOrders(Global.options.getPair());
        if (activeOrdersResponse.isPositive()) {
        } else {
            MainLaunch.exitWithNotice("could not query exchange. exchange setup went wrong [ " + activeOrdersResponse.getError() + " ]");
        }


        //Start task to check orders
        int start_delay = 40;
        Global.taskManager.getSendLiquidityTask().start(start_delay);

        if (Global.options.isSubmitliquidity()) {
            try {
                checkNuConn();
            } catch (NuBotConnectionException e) {
                MainLaunch.exitWithNotice("can't connect to Nu " + e);
            }
        }

        LOG.info("Start trading Strategy specific for " + Global.options.getPair().toString());

        LOG.info("Options loaded : " + Global.options.toStringNoKeys());

        // Set the frozen balance manager in the global variable

        Global.frozenBalances = new FrozenBalancesManager(Global.options.getExchangeName(), Global.options.getPair());

        try {
            configureStrategy();
        } catch (NuBotConfigException e) {
            MainLaunch.exitWithNotice("can't configure strategy");
        }

        notifyOnline();

    }

    protected void notifyOnline() {
        String exc = Global.options.getExchangeName();
        String p = Global.options.getPair().toStringSep();
        String msg = "A new <strong>" + mode + "</strong> bot just came online on " + exc + " pair (" + p + ")";
        LOG.debug("notify online " + msg);
        HipChatNotifications.sendMessage(msg, MessageColor.GREEN);
    }

    public void shutdownBot(){

        LOG.info("Bot shutting down..");

        String additionalInfo = "after " + Utils.getBotUptime() + " uptime on "
                + Global.options.getExchangeName() + " ["
                + Global.options.getPair().toStringSep() + "]";

        HipChatNotifications.sendMessageCritical("Bot shut-down " + additionalInfo);

        //Try to cancel all orders, if any
        if (Global.exchange.getTrade() != null && Global.options.getPair() != null) {
            LOG.info("Clearing out active orders ... ");

            ApiResponse deleteOrdersResponse = Global.exchange.getTrade().clearOrders(Global.options.getPair());
            if (deleteOrdersResponse.isPositive()) {
                boolean deleted = (boolean) deleteOrdersResponse.getResponseObject();

                if (deleted) {
                    LOG.info("Order clear request successful");
                } else {
                    LOG.error("Could not submit request to clear orders");
                }

            } else {
                LOG.error(deleteOrdersResponse.getError().toString());
            }
        }

        //reset liquidity info
        if (Global.options.isSubmitliquidity()) {
            if (Global.rpcClient.isConnected()) {
                //tier 1
                LOG.info("Resetting Liquidity Info before quit");

                JSONObject responseObject1 = Global.rpcClient.submitLiquidityInfo(Global.rpcClient.USDchar,
                        0, 0, 1);
                if (null == responseObject1) {
                    LOG.error("Something went wrong while sending liquidityinfo");
                } else {
                    LOG.info(responseObject1.toJSONString());
                }

                JSONObject responseObject2 = Global.rpcClient.submitLiquidityInfo(Global.rpcClient.USDchar,
                        0, 0, 2);
                if (null == responseObject2) {
                    LOG.error("Something went wrong while sending liquidityinfo");
                } else {
                    LOG.info(responseObject2.toJSONString());
                }
            }
        }
    }

}
