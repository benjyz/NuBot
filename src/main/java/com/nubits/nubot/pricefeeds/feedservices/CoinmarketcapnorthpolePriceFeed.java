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


public class CoinmarketcapnorthpolePriceFeed extends AbstractPriceFeed {

    private static final Logger LOG = LoggerFactory.getLogger(BitcoinaveragePriceFeed.class.getName());

    public static final String name = FeedFacade.CoinmarketcapnorthpolePriceFeed;

    public CoinmarketcapnorthpolePriceFeed() {
        refreshMinTime = 50 * 1000;
        lastRequest = 0L;
    }

    @Override
    public LastPrice getLastPrice(CurrencyPair pair) {

        long now = System.currentTimeMillis();
        long diff = now - lastRequest;
        if (diff >= refreshMinTime) {
            String htmlString;
            try {
                htmlString = Utils.getHTML(getUrl(pair), true);
            } catch (IOException ex) {
                LOG.error(ex.toString());
                return new LastPrice(true, name, pair.getOrderCurrency(), null);
            }
            JSONParser parser = new JSONParser();
            try {
                JSONObject httpAnswerJson = (JSONObject) (parser.parse(htmlString));
                double last = Utils.getDouble(httpAnswerJson.get("price"));
                lastRequest = System.currentTimeMillis();
                lastPrice = new LastPrice(false, name, pair.getOrderCurrency(), new Amount(last, pair.getPaymentCurrency()));
                return lastPrice;
            } catch (Exception ex) {
                LOG.error(ex.toString());
                lastRequest = System.currentTimeMillis();
                return new LastPrice(true, name, pair.getOrderCurrency(), null);
            }
        } else {
            LOG.warn("Wait " + (refreshMinTime - (System.currentTimeMillis() - lastRequest)) + " ms "
                    + "before making a new request. Now returning the last saved price\n\n");
            return lastPrice;
        }


    }

    private String getUrl(CurrencyPair pair) {
        //controls
        String currency = pair.getOrderCurrency().getCode().toLowerCase();
        return "http://coinmarketcap.northpole.ro/api/" + currency + ".json";
    }
}
