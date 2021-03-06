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

package com.nubits.nubot.trading.LiquidityDistribution;


import com.nubits.nubot.global.Constant;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.OrderToPlace;
import com.nubits.nubot.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class LiquidityDistributionModel {

    private static final Logger LOG = LoggerFactory.getLogger(LiquidityDistributionModel.class.getName());
    private ModelParameters sellParams, buyParams;

    public LiquidityDistributionModel(ModelParameters sellParams, ModelParameters buyParams) {
        this.sellParams = sellParams;
        this.buyParams = buyParams;
    }

    public LiquidityDistributionModel(ModelParameters params, String type) {
        if (type.equals(Constant.SELL)) {
            this.sellParams = params;
        } else {
            this.buyParams = params;
        }
    }

    public static ModelParameters getStandardParams(String type, double wallHeight) {

        double wallDensity = 0.020;
        double wallWidth = 0.15;


        double offset;
        LiquidityCurve curve;

        if (type.equals(Constant.SELL)) {
            offset = 0.005;
            curve = new LiquidityCurveLin(LiquidityCurve.STEEPNESS_MID); //Linear

        } else {
            offset = 0.002;
            curve = new LiquidityCurveExp(LiquidityCurve.STEEPNESS_LOW); //Exponential
        }

        return new ModelParameters(offset, wallHeight, wallWidth, wallDensity, curve);

    }

    public ArrayList<OrderToPlace> getOrdersToPlace(String type, Amount fundsAvailable, double pegPrice, CurrencyPair pair, double txFee) {
        if (type.equals(Constant.SELL)) {
            return getOrdersToPlaceImpl(this.sellParams, fundsAvailable, Constant.SELL, pegPrice, pair, txFee);
        } else {
            return getOrdersToPlaceImpl(this.buyParams, fundsAvailable, Constant.BUY, pegPrice, pair, txFee);
        }
    }

    public ArrayList<OrderToPlace> getSellOrdersToPlace(Amount fundsAvailable, double pegPrice, CurrencyPair pair, double txFee) {
        return getOrdersToPlaceImpl(this.sellParams, fundsAvailable, Constant.SELL, pegPrice, pair, txFee);
    }

    public ArrayList<OrderToPlace> getBuyOrdersToPlace(Amount fundsAvailable, double pegPrice, CurrencyPair pair, double txFee) {
        return getOrdersToPlaceImpl(this.buyParams, fundsAvailable, Constant.BUY, pegPrice, pair, txFee);
    }

    private ArrayList<OrderToPlace> getOrdersToPlaceImpl(ModelParameters params, Amount funds, String wallType, double pegPrice, CurrencyPair pair, double txFee) {
        ArrayList<OrderToPlace> toReturn = new ArrayList();

        //First create the wall order and add it to the list
        double oneUSD = Utils.round(1 / pegPrice, 8); //one $ expressed in the peg currency
        double wallPrice = oneUSD;

        double offset = Utils.round(params.getOffset() * oneUSD, 8); //Convert the spread in the peg currency
        double fee = Utils.round((oneUSD / 100) * txFee, 8); //Convert the txFee in the peg currency

        double totalOffset = offset + fee;//Compute the total offset by adding spread+fee

        double endPrice; // The last price of the book
        double wallWidthPeg = Utils.round(params.getWallWidth() * oneUSD, 8);


        double totalFundsAvailNBT = funds.getQuantity();
        //Add it or remove it from the price, based on the type of order
        if (wallType.equals(Constant.SELL)) {
            wallPrice += totalOffset;
            endPrice = wallPrice + wallWidthPeg;
        } else {
            wallPrice -= totalOffset;
            endPrice = wallPrice - wallWidthPeg;
            totalFundsAvailNBT = Utils.round(totalFundsAvailNBT * pegPrice, 8);
        }
        wallPrice = Utils.round(wallPrice, 8);


        double wallHeight = params.getWallHeight();
        boolean tailAvailable = true;
        if (wallHeight > totalFundsAvailNBT) {
            LOG.warn("The current balance equivalent " + totalFundsAvailNBT
                    + " NBT is not enought to place the full " + wallType + "wall"
                    + " defined in the liquidity model (" + wallHeight + "). "
                    + "\nResizing the size of the order, and not adding the tail");

            tailAvailable = false;
            wallHeight = totalFundsAvailNBT - 1;
        }

        toReturn.add(new OrderToPlace(wallType, pair, wallHeight, wallPrice));        //Add first order
        totalFundsAvailNBT -= wallHeight;

        if (tailAvailable) {
            //Compute the array of prices
            double[] prices = getPriceArray(wallPrice, endPrice, wallType, params.getDensity(), pegPrice);
            double[] sizes = params.getCurve().computeOrderSize(prices, params.getWallHeight(), wallType, wallPrice, pegPrice);

            boolean prepareNext = true;
            for (int i = 0; i < prices.length; i++) {
                if (prepareNext) {
                    if (sizes[i] < totalFundsAvailNBT) {
                        toReturn.add(new OrderToPlace(wallType, pair, sizes[i], prices[i]));
                        totalFundsAvailNBT -= sizes[i];
                    } else {
                        LOG.warn("Not enough liquidity to place the full tail as defined in the model. Skipping the rest of the tail"
                                + "\ntotalFundsAvailNBT = " + totalFundsAvailNBT + " ; sizes[i] = " + sizes[i]);
                        break;
                    }

                }
            }
            System.out.println("\n");

        }
        return toReturn;
    }

    private double[] getPriceArray(double startPrice, double endPrice, String type, double density, double pegPrice) {
        double oneUSD = Utils.round(1 / pegPrice, 8); //one $ expressed in the peg currency
        double distanceAmongOrders = Utils.round(density * oneUSD, 8); //Convert the distance in the peg currency
        int numberOfElements = Utils.safeLongToInt(Math.round((Math.abs(startPrice - endPrice)) / distanceAmongOrders));
        double[] toReturn = new double[numberOfElements];

        for (int i = 0; i < numberOfElements; i++) {
            if (type.equals(Constant.SELL)) {
                toReturn[i] = Utils.round(startPrice + ((i + 1) * distanceAmongOrders), 8);
            } else {
                toReturn[i] = Utils.round(startPrice - ((i + 1) * distanceAmongOrders), 8);
            }
        }


        return toReturn;
    }

    public ModelParameters getSellParams() {
        return sellParams;
    }

    public void setSellParams(ModelParameters sellParams) {
        this.sellParams = sellParams;
    }

    public ModelParameters getBuyParams() {
        return buyParams;
    }

    public void setBuyParams(ModelParameters buyParams) {
        this.buyParams = buyParams;
    }
}
