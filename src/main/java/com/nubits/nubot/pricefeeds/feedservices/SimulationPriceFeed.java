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

package com.nubits.nubot.pricefeeds.feedservices;

import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.LastPrice;
import com.nubits.nubot.pricefeeds.FeedFacade;
import com.nubits.nubot.utils.Utils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class SimulationPriceFeed extends AbstractPriceFeed {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationPriceFeed.class.getName());

    public final static String name = FeedFacade.CoinbasePriceFeed;

    public SimulationPriceFeed() {
        refreshMinTime = 50 * 1000; //one minutee
    }

    @Override
    public LastPrice getLastPrice(CurrencyPair pair) {

        try {
            double last = 200.0;
            lastRequest = System.currentTimeMillis();
            lastPrice = new LastPrice(false, name, pair.getOrderCurrency(), new Amount(last, pair.getPaymentCurrency()));
            return lastPrice;
        } catch (Exception ex) {
            LOG.error(ex.toString());
            lastRequest = System.currentTimeMillis();
            return new LastPrice(true, name, pair.getOrderCurrency(), null);
        }

    }

}
