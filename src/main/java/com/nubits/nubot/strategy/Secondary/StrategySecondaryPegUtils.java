/*
 * Copyright (C) 2014 desrever <desrever at nubits.com>
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
package com.nubits.nubot.strategy.Secondary;

import com.nubits.nubot.bot.Global;
import com.nubits.nubot.bot.SessionManager;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Settings;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.models.PairBalance;
import com.nubits.nubot.notifications.HipChatNotifications;
import com.nubits.nubot.notifications.MailNotifications;
import com.nubits.nubot.utils.Utils;
import io.evanwong.oss.hipchat.v2.rooms.MessageColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StrategySecondaryPegUtils {


    final static Logger LOG = LoggerFactory.getLogger(StrategySecondaryPegUtils.class);
    private final int MAX_RANDOM_WAIT_SECONDS = 5;
    private final int SHORT_WAIT_SECONDS = 6;
    private StrategySecondaryPegTask strategy;

    public StrategySecondaryPegUtils(StrategySecondaryPegTask strategy) {
        this.strategy = strategy;
    }

    public boolean reInitiateOrders(boolean firstTime) {
        strategy.setResettingOrders(true);
        LOG.debug("reInitiateOrders . firstTime=" + firstTime);

        if (SessionManager.sessionInterrupted()) return false; //external interruption
        //They are either 0 or need to be cancelled
        Global.orderManager.fetchOrders();
        int totalOrders = Global.orderManager.getNumTotalActiveOrders();
        if (totalOrders > 0) {
            ApiResponse deleteOrdersResponse = Global.exchange.getTrade().clearOrders(Global.options.getPair());
            if (deleteOrdersResponse.isPositive()) {
                if (SessionManager.sessionInterrupted()) return false;
                boolean deleted = (boolean) deleteOrdersResponse.getResponseObject();
                if (deleted) {
                    LOG.info("Clear all orders request successfully");
                    if (firstTime) //update the initial balance of the secondary peg
                    {
                        Global.frozenBalancesManager.setBalanceAlreadyThere(Global.options.getPair().getPaymentCurrency());
                    }
                    //Wait until there are no active orders
                    boolean timedOut = false;
                    long timeout = Global.options.getEmergencyTimeout() * 1000;
                    long wait = SHORT_WAIT_SECONDS * 1000;
                    long count = 0L;

                    boolean areAllOrdersCanceled = false;
                    do {
                        try {
                            if (SessionManager.sessionInterrupted()) return false;
                            Thread.sleep(wait);
                            areAllOrdersCanceled = Global.orderManager.tryCancelAllOrders(Global.options.getPair());
                            if (areAllOrdersCanceled) {
                                LOG.warn("All orders canceled successfully");
                            } else {
                                LOG.error("There was a problem cancelling the orders (count: " + count + ")");
                            }

                            count += wait;
                            timedOut = count > timeout;
                            if (SessionManager.sessionInterrupted()) return false;
                        } catch (InterruptedException ex) {
                            LOG.error(ex.toString());
                        }
                    } while (!areAllOrdersCanceled && !timedOut);

                    if (timedOut) {
                        String message = "There was a problem cancelling all existing orders (timeout:" + timeout + ", count: " + count + ")";
                        LOG.error(message);
                        HipChatNotifications.sendMessage(message, MessageColor.YELLOW);
                        MailNotifications.send(Global.options.getMailRecipient(), "NuBot : Problem cancelling existing orders", message);
                        //Continue anyway, maybe there is some balance to put up on order.
                    }
                    //Update the balance
                    placeInitialWalls();
                } else {
                    String message = "Could not submit request to clear orders";
                    LOG.error(message);
                    strategy.setResettingOrders(false);
                    return false;
                }

            } else {
                LOG.error(deleteOrdersResponse.getError().toString());
                String message = "Could not submit request to clear orders";
                LOG.error(message);
                strategy.setResettingOrders(false);
                return false;
            }
        } else {
            if (firstTime) //update the initial balance of the secondary peg
            {
                Global.frozenBalancesManager.setBalanceAlreadyThere(Global.options.getPair().getPaymentCurrency());
            }
            placeInitialWalls();
        }
        try {
            Thread.sleep(SHORT_WAIT_SECONDS); //Give the time to new orders to be placed before counting again
        } catch (InterruptedException ex) {
            LOG.error(ex.toString());
        }
        strategy.setResettingOrders(false);
        return true;
    }

    public void placeInitialWalls() {
        if (SessionManager.sessionInterrupted()) return;
        boolean buysOrdersOk = true;
        double sellPrice = strategy.getSellPricePEG();
        if (SessionManager.sessionInterrupted()) return;
        LOG.debug("init sell orders. price: " + sellPrice);
        boolean sellsOrdersOk = initOrders(Constant.SELL, sellPrice);
        if (SessionManager.sessionInterrupted()) return;
        if (Global.options.isDualSide()) {
            if (SessionManager.sessionInterrupted()) return;
            double buyPrice = strategy.getBuyPricePEG();
            LOG.debug("init buy orders. price: " + buyPrice);
            buysOrdersOk = initOrders(Constant.BUY, buyPrice);
            if (SessionManager.sessionInterrupted()) return;
        }

        if (buysOrdersOk && sellsOrdersOk) {
            strategy.setMightNeedInit(false);
            LOG.info("Strategy initialization completed");
        } else {
            strategy.setMightNeedInit(true);
        }
    }

    private Currency getCurrency(String type) {
        Currency currency;
        if (!Global.swappedPair) {
            if (type.equals(Constant.SELL)) {
                currency = Global.options.getPair().getOrderCurrency();
            } else {
                currency = Global.options.getPair().getPaymentCurrency();
            }
        } else {
            if (type.equals(Constant.SELL)) {
                currency = Global.options.getPair().getPaymentCurrency();
            } else {
                currency = Global.options.getPair().getOrderCurrency();
            }
        }

        return currency;
    }

    private String hipchatMsg(String type, String orderString1) {
        return "New " + type + " wall is up on <strong>" + Global.options.getExchangeName() + "</strong> : " + orderString1;
    }


    public boolean initOrders(String type, double price) {
        if (SessionManager.sessionInterrupted()) return false;

        LOG.info("initOrders " + type + ", price " + price);

        boolean success = true;
        Amount balance = null;
        //Update the available balance
        Currency currency = getCurrency(type);

        ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalance(currency);
        if (!balancesResponse.isPositive()) {
            LOG.error(balancesResponse.getError().toString());
            return false;
        }
        if (SessionManager.sessionInterrupted()) return false;

        double oneNBT = 1;
        if (type.equals(Constant.SELL)) {
            balance = (Amount) balancesResponse.getResponseObject();
        } else {
            //Here its time to compute the balance to put apart, if any
            balance = (Amount) balancesResponse.getResponseObject();
            balance = Global.frozenBalancesManager.removeFrozenAmount(balance, Global.frozenBalancesManager.getFrozenAmount());
            oneNBT = Utils.round(1 / Global.conversion, Settings.DEFAULT_PRECISION);
        }
        if (SessionManager.sessionInterrupted()) return false;

        if (balance.getQuantity() < oneNBT * 2) {
            LOG.info("No need to execute " + type + "orders : available balance < 1 " + currency.getCode() + " (1 NBT equivalent).  Balance : " + balance.getQuantity());
            return true;
        }

        //Update TX fee :
        //Get the current transaction fee associated with a specific CurrencyPair
        ApiResponse txFeeNTBPEGResponse = Global.exchange.getTrade().getTxFee(Global.options.getPair());
        if (SessionManager.sessionInterrupted()) return false;

        //short hand variables
        double maxSell = Global.options.getMaxSellVolume();
        double maxBuy = Global.options.getMaxBuyVolume();

        LOG.debug("balance " + balance + " maxBuy " + maxBuy + ". maxSell " + maxSell);

        if (txFeeNTBPEGResponse.isPositive()) {
            double txFeePEGNTB = (Double) txFeeNTBPEGResponse.getResponseObject();
            LOG.trace("Updated Transaction fee = " + txFeePEGNTB + "%");

            double amount1 = Utils.round(balance.getQuantity() / 2, Settings.DEFAULT_PRECISION);
            LOG.debug("amount1: " + amount1 + " . balance " + balance.getQuantity());
            //check the calculated amount against the set maximum sell amount set in the options.json file


            if (maxSell > 0 && type.equals(Constant.SELL)) {
                if (amount1 > (maxSell / 2))
                    amount1 = (maxSell / 2);
            }


            if (type.equals(Constant.BUY) && !Global.swappedPair) {
                amount1 = Utils.round(amount1 / price, Settings.DEFAULT_PRECISION);
                LOG.debug("buy: => amount " + amount1);
                //check the calculated amount against the max buy amount option, if any.
                if (maxBuy > 0) {
                    if (amount1 > (maxBuy / 2))
                        amount1 = (maxBuy / 2);
                }

            }
            if (SessionManager.sessionInterrupted()) return false;

            success = Global.orderManager.executeOrder(type, Global.options.getPair(), amount1, price);
            if (!success)
                return false;

            //wait a while to give the time to the new amount to update

            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException ex) {
                LOG.error(ex.toString());
            }
            if (SessionManager.sessionInterrupted()) return false;

            //read balance again
            ApiResponse balancesResponse2 = Global.exchange.getTrade().getAvailableBalance(currency);
            if (balancesResponse2.isPositive()) {

                balance = (Amount) balancesResponse2.getResponseObject();

                if (type.equals(Constant.BUY)) {
                    balance = Global.frozenBalancesManager.removeFrozenAmount(balance, Global.frozenBalancesManager.getFrozenAmount());
                }


                double amount2 = balance.getQuantity();

                //check the calculated amount against the set maximum sell amount set in the options.json file

                if (type.equals(Constant.SELL) && maxSell > 0) {
                    if (amount2 > (maxSell / 2))
                        amount2 = maxSell / 2;
                }

                if ((type.equals(Constant.BUY) && !Global.swappedPair)
                        || (type.equals(Constant.SELL) && Global.swappedPair)) {
                    //hotfix
                    amount2 = Utils.round(amount2 - (oneNBT * 0.9), Settings.DEFAULT_PRECISION); //multiply by .9 to keep it below one NBT

                    amount2 = Utils.round(amount2 / price, Settings.DEFAULT_PRECISION);

                    //check the calculated amount against the max buy amount option, if any.
                    if (maxBuy > 0) {
                        if (amount2 > (maxBuy / 2))
                            amount2 = (maxBuy / 2);
                    }

                }

                //execute second order
                if (SessionManager.sessionInterrupted()) return false;

                success = Global.orderManager.executeOrder(type, Global.options.getPair(), amount2, price);
                if (!success)
                    return false;

            } else {
                LOG.error("Error while reading the balance the second time " + balancesResponse2.getError().toString());
            }

        }


        return success;
    }

    public void recount() {
        if (SessionManager.sessionInterrupted()) return; //external interruption

        ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalances(Global.options.getPair());
        if (balancesResponse.isPositive()) {
            PairBalance balance = (PairBalance) balancesResponse.getResponseObject();
            double balanceNBT = balance.getNBTAvailable().getQuantity();
            double balancePEG = (Global.frozenBalancesManager.removeFrozenAmount(balance.getPEGAvailableBalance(), Global.frozenBalancesManager.getFrozenAmount())).getQuantity();

            strategy.setOrdersAndBalancesOK(false);

            double oneNBT = Utils.round(1 / Global.conversion, Settings.DEFAULT_PRECISION);
            if (SessionManager.sessionInterrupted()) return; //external interruption

            Global.orderManager.fetchOrders();
            int activeSellOrders = Global.orderManager.getNumActiveSellOrders();
            int activeBuyOrders = Global.orderManager.getNumActiveBuyOrders();

            if (Global.options.isDualSide()) {

                boolean correctOrders = (activeSellOrders == 2 && activeBuyOrders == 2)
                        || (activeSellOrders == 2 && activeBuyOrders == 0 && balancePEG < oneNBT)
                        || (activeSellOrders == 0 && activeBuyOrders == 2 && balanceNBT < 1)
                        || (activeSellOrders == 0 && activeBuyOrders == 0 && balanceNBT < 1 && balancePEG < oneNBT);
                LOG.debug("correct orders: " + correctOrders);
                strategy.setOrdersAndBalancesOK(correctOrders);


                if (balancePEG > oneNBT
                        && Global.options.getPair().getPaymentCurrency().isFiat()
                        && !strategy.isFirstTime()
                        && Global.options.getMaxBuyVolume() != 0) { //Only for EUR...CNY etc
                    LOG.warn("The " + balance.getPEGAvailableBalance().getCurrency().getCode() + " balance is not zero (" + balancePEG + " ). If the balance represent proceedings "
                            + "from a sale the bot will notice.  On the other hand, If you keep seying this message repeatedly over and over, you should restart the bot. ");
                    strategy.setProceedsInBalance(true);
                } else {
                    strategy.setProceedsInBalance(false);
                }
            } else {
                boolean correctOrders = activeSellOrders == 2 && activeBuyOrders == 0;
                LOG.debug("correctOrders: " + correctOrders);
                strategy.setOrdersAndBalancesOK(correctOrders); // Ignore the balance
            }
        } else {
            LOG.error(balancesResponse.getError().toString());
        }
    }

    public void aggregateAndKeepProceeds() {
        if (SessionManager.sessionInterrupted()) return; //external interruption

        LOG.info("aggregateAndKeepProceeds");

        boolean cancel = Global.orderManager.takeDownOrders(Constant.BUY, Global.options.getPair());
        if (!cancel) {
            LOG.error("An error occurred while attempting to cancel buy orders.");
            return;
        }

        //get the balance and see if it does still require an aggregation

        Global.frozenBalancesManager.freezeNewFunds();

        //Introuce an aleatory sleep time to desync bots at the time of placing orders.
        //This will favour competition in markets with multiple custodians
        try {
            Thread.sleep(Utils.randInt(0, MAX_RANDOM_WAIT_SECONDS) * 1000);
        } catch (InterruptedException ex) {
            LOG.error(ex.toString());
        }

        double buyPrice = strategy.getBuyPricePEG();
        LOG.info("init buy orders. price " + buyPrice);
        initOrders(Constant.BUY, buyPrice);


    }


    public boolean shiftWalls() {
        if (SessionManager.sessionInterrupted()) return false; //external interruption

        if (strategy.isResettingOrders()) {
            LOG.warn("Cannot shift walls, orders are being reinitiated. Price is already adjusted.");
            return false;
        }

        if (strategy.isResettingOrders()) {
            LOG.warn("Cannot shift walls, orders are being reinitiated. Price is already adjusted.");
            return false;
        }
        
        LOG.debug("Executing shiftWalls()");

        boolean success = true;

        //Communicate to the priceMonitorTask that a wall shift is in place
        strategy.getPriceMonitorTask().setWallsBeingShifted(true);
        strategy.getSendLiquidityTask().setWallsBeingShifted(true);

        //fix prices, so that if they change during wait time, this wall shift is not affected.
        double sellPrice = strategy.getSellPricePEG();
        double buyPrice = strategy.getBuyPricePEG();

        //Swap prices
        if (Global.swappedPair) {
            sellPrice = strategy.getBuyPricePEG();
            buyPrice = strategy.getSellPricePEG();
        }

        LOG.info("Immediately try to cancel all orders");
        if (SessionManager.sessionInterrupted()) return false; //external interruption

        //immediately try to : cancel all active orders
        ApiResponse deleteOrdersResponse = Global.exchange.getTrade().clearOrders(Global.options.getPair());
        if (SessionManager.sessionInterrupted()) return false; //external interruption

        if (deleteOrdersResponse.isPositive()) {
            boolean deleted = (boolean) deleteOrdersResponse.getResponseObject();
            if (deleted) {
                LOG.warn("Orders deleted");
                if (Global.options.isMultipleCustodians()) {
                    //Introuce an aleatory sleep time to desync bots at the time of placing orders.
                    //This will favour competition in markets with multiple custodians
                    if (SessionManager.sessionInterrupted()) return false; //external interruption

                    try {
                        Thread.sleep(SHORT_WAIT_SECONDS + Utils.randInt(0, MAX_RANDOM_WAIT_SECONDS) * 1000); //SHORT_WAIT_SECONDS gives the time to other bots to take down their order
                    } catch (InterruptedException ex) {
                        LOG.error(ex.toString());
                    }
                }
                if (SessionManager.sessionInterrupted()) return false; //external interruption

                //Update frozen balances
                if (!Global.options.isDualSide() //Do not do this for sell side custodians or...
                        || !Global.options.getPair().getPaymentCurrency().isFiat()) //...do not do this for stable secondary pegs (e.g EUR)
                {
                    // update the initial balance of the secondary peg
                    Global.frozenBalancesManager.freezeNewFunds();
                }


                if (SessionManager.sessionInterrupted()) return false; //external interruption

                //Reset sell side orders
                boolean initSells = initOrders(Constant.SELL, sellPrice); //Force init sell orders

                if (!initSells) {
                    success = false;
                }

                if (initSells) { //Only move the buy orders if sure that the sell have been taken down
                    if (Global.options.isDualSide()) {
                        boolean initBuys;
                        if (SessionManager.sessionInterrupted()) return false; //external interruption

                        initBuys = initOrders(Constant.BUY, buyPrice);
                        if (!initBuys) {
                            success = false;
                            LOG.error("NuBot has not been able to shift buy orders");
                        }
                        if (SessionManager.sessionInterrupted()) return false; //external interruption

                    }
                } else { //success false with the first part of the shift
                    LOG.error("NuBot has not been able to shift sell orders");
                }
            } else {
                LOG.error("Coudn't delete orders ");
            }

            //Here I wait until the two orders are correctly displaied. It can take some seconds
            try {
                Thread.sleep(SHORT_WAIT_SECONDS * 1000);
            } catch (InterruptedException ex) {
                LOG.error(ex.toString());
            }

            //Communicate to the priceMonitorTask that the wall shift is over
            strategy.getPriceMonitorTask().setWallsBeingShifted(false);
            strategy.getSendLiquidityTask().setWallsBeingShifted(false);

        } else {
            LOG.info("Could not submit request to clear orders");
            success = false;
            //Communicate to the priceMonitorTask that the wall shift is over
            strategy.getPriceMonitorTask().setWallsBeingShifted(false);
            strategy.getSendLiquidityTask().setWallsBeingShifted(false);
            LOG.error(deleteOrdersResponse.getError().toString());
        }

        return success;
    }
}