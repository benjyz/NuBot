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
import com.nubits.nubot.exchanges.ExchangeFacade;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Settings;
import com.nubits.nubot.models.*;
import com.nubits.nubot.options.NuBotConfigException;
import com.nubits.nubot.trading.LiquidityDistribution.*;
import com.nubits.nubot.utils.InitTests;
import com.nubits.nubot.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class TestLiquidityDistribution {

    //define Logging by using predefined Settings which points to an XML
    static {
        System.setProperty("logback.configurationFile", Settings.TEST_LOGXML);
    }

    private static final Logger LOG = LoggerFactory.getLogger(TestLiquidityDistribution.class.getName());
    private static final String TEST_OPTIONS_PATH = "config/myconfig/poloniex.json";
    private LiquidityDistributionModel ldm;
    private ModelParameters sellParams;
    private ModelParameters buyParams;
    private Amount balanceNBT;
    private Amount balancePEG;
    private CurrencyPair pair;
    private double txFee;
    private boolean execOrders;
    double pegPrice;

    public static void main(String a[]) {
        InitTests.setLoggingFilename(TestLiquidityDistribution.class.getSimpleName());

        TestLiquidityDistribution test = new TestLiquidityDistribution();

        InitTests.loadConfig(TEST_OPTIONS_PATH);

        test.init(ExchangeFacade.INTERNAL_EXCHANGE_PEATIO); //Pass an empty string to avoid placing the orders
        test.configureTest();
        test.exec();
    }

    private void init(String exchangeName) {

        execOrders = false;
        if (!exchangeName.equals("")) {
            //Setup the exchange
            pair = CurrencyList.NBT_BTC;
            try {
                WrapperTestUtils.configureExchange(exchangeName);
            } catch (NuBotConfigException e) {

            }
            WrapperTestUtils.testClearAllOrders(pair);
        }


    }

    private void configureTest() {
        LOG.info("Configuring test");

        //Custodian balance simulation
        balanceNBT = new Amount(27100.0, CurrencyList.NBT);
        balancePEG = new Amount(100, CurrencyList.BTC);
        if (execOrders) {
            configureBalances(pair);
        }

        pegPrice = 300; // value of 1 unit expressed in USD

        txFee = 0.2; // %

        //Configure sell Params
        double sellOffset = 0.01;
        double sellWallHeight = 60;
        double sellWallWidth = 0.15;
        double sellWallDensity = 0.020;

        //Configure Liquidity curve
        //LiquidityCurve sellCurve = new LiquidityCurveLin(LiquidityCurve.STEEPNESS_MID); //Linear
        //LiquidityCurve sellCurve = new LiquidityCurveExp(LiquidityCurve.STEEPNESS_LOW); //Exponential
        LiquidityCurve sellCurve = new LiquidityCurveLog(LiquidityCurve.STEEPNESS_LOW); //Logarithmic

        //Configure buy Params
        double buyOffset = 0.01;
        double buyWallHeight = 4;
        double buyWallWidth = 0.15;
        double buyWallDensity = 0.020;
        //Configure Liquidity curve
        LiquidityCurve buyCurve = new LiquidityCurveLin(LiquidityCurve.STEEPNESS_MID); //Linear
        //LiquidityCurve buyCurve = new LiquidityCurveExp(LiquidityCurve.STEEPNESS_HIGH); //Exponential
        //LiquidityCurve buyCurve = new LiquidityCurveLog(LiquidityCurve.STEEPNESS_LOW);//Logarithmic


        sellParams = new ModelParameters(sellOffset, sellWallHeight, sellWallWidth, sellWallDensity, sellCurve);
        buyParams = new ModelParameters(buyOffset, buyWallHeight, buyWallWidth, buyWallDensity, buyCurve);

        String config = "Sell order book configuration : " + sellParams.toString();
        config += "Buy order book configuration : " + buyParams.toString();
        config += "Pair : " + pair.toString();
        config += "\nbalanceNBT : " + balanceNBT.getQuantity();
        config += "\nbalancePEG : " + balancePEG.getQuantity();
        config += "\npegPrice : " + pegPrice;
        config += "\ntxFee : " + txFee;
        config += "\n\n -------------------";

        LOG.info(config);

    }

    private void exec() {
        ldm = new LiquidityDistributionModel(sellParams, buyParams);

        ArrayList<OrderToPlace> sellOrders = ldm.getOrdersToPlace(Constant.SELL, balanceNBT, pegPrice, pair, txFee);
        ArrayList<OrderToPlace> buyOrders = ldm.getOrdersToPlace(Constant.BUY, balancePEG, pegPrice, pair, txFee);

        printOrderBooks(sellOrders, buyOrders);
        Utils.drawOrderBooks(sellOrders, buyOrders, pegPrice);

        if (execOrders) {
            placeOrders(sellOrders, buyOrders);
        }

    }

    private void printOrderBooks(ArrayList<OrderToPlace> sellOrders, ArrayList<OrderToPlace> buyOrders) {
        String sellOrdersString = printOrderBook(sellOrders, Constant.SELL);
        String buyOrdersString = printOrderBook(buyOrders, Constant.BUY);

        LOG.info(sellOrdersString + "\n" + buyOrdersString);

    }

    private String printOrderBook(ArrayList<OrderToPlace> orders, String type) {
        String toReturn = "----- " + type + " order book\n";
        double sumSize = 0;
        for (int i = 0; i < orders.size(); i++) {
            OrderToPlace tempOrder = orders.get(i);
            toReturn += Utils.round(tempOrder.getPrice() * pegPrice, 6) + "," + tempOrder.getPrice() + "," + tempOrder.getSize() + "\n";
            sumSize += tempOrder.getSize();
        }
        toReturn += "Order book size = " + sumSize + " NBT ";

        double buyBalanceNBT = Utils.round(balancePEG.getQuantity() * pegPrice, 8);
        double sellBalanceNBT = balanceNBT.getQuantity();

        boolean overThreshold = false;
        if (type.equals(Constant.SELL) && sumSize > sellBalanceNBT) {
            overThreshold = true;
        }

        if (type.equals(Constant.BUY) && sumSize > buyBalanceNBT) {
            overThreshold = true;
        }

        if (overThreshold) {
            toReturn += "\n\n!The funds are not sufficient to satisfy current order books configuration!";
        }


        toReturn += "----- ";
        return toReturn;
    }

    private void placeOrders(ArrayList<OrderToPlace> sellOrders, ArrayList<OrderToPlace> buyOrders) {

        long startTime = System.nanoTime(); //TIC

        LOG.info("Placing sell orders on " + Global.exchange.getName());
        WrapperTestUtils.testMultipleOrders(sellOrders, pair);

        LOG.info("Placing buy orders on " + Global.exchange.getName());
        WrapperTestUtils.testMultipleOrders(buyOrders, pair);

        LOG.info("Total Time: " + (System.nanoTime() - startTime) / 1000000 + " ms"); //TOC

    }

    private boolean configureBalances(CurrencyPair pair) {
        boolean success = true;
        ApiResponse balanceNBTResponse = Global.exchange.getTrade().getAvailableBalance(CurrencyList.NBT);
        if (balanceNBTResponse.isPositive()) {
            Amount balance = (Amount) balanceNBTResponse.getResponseObject();
            LOG.info("NBT Balance : " + balance.toString());
            balanceNBT = balance;
        } else {
            LOG.error(balanceNBTResponse.getError().toString());
            success = false;
        }

        ApiResponse balancePEGResponse = Global.exchange.getTrade().getAvailableBalance(pair.getPaymentCurrency());
        if (balancePEGResponse.isPositive()) {
            Amount balance = (Amount) balancePEGResponse.getResponseObject();
            LOG.info(pair.getPaymentCurrency().getCode() + " Balance : " + balance.toString());
            balancePEG = balance;
        } else {
            LOG.error(balancePEGResponse.getError().toString());
            success = false;
        }

        return success;
    }
}
