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
package com.nubits.nubot.pricefeeds;

import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.LastPrice;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.nubits.nubot.options.NuBotConfigException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Manager for a selected list of price feeds
 * see also Feeds, which manages all existing feeds
 */
public class PriceFeedManager {

    private static final Logger LOG = LoggerFactory.getLogger(PriceFeedManager.class.getName());
    //private AbstractPriceFeed mainfeed;
    private ArrayList<AbstractPriceFeed> feedList = new ArrayList<>();
    private CurrencyPair pair;


    public PriceFeedManager(String mainFeed, ArrayList<String> backupFeedList, CurrencyPair pair) throws NuBotConfigException {
        Feeds.initValidFeeds();
        this.pair = pair;

        feedList.add(Feeds.getFeed(mainFeed)); //add the main feed at index 0
        //this.mainfeed = getFeed(mainFeed);

        for (int i = 0; i < backupFeedList.size(); i++) {
            feedList.add(Feeds.getFeed(backupFeedList.get(i)));
        }
    }

    /**
     * trigger fetches from all feeds
     * @return
     */
    public LastPriceResponse getLastPrices() {
        LastPriceResponse response = new LastPriceResponse();
        boolean isMainFeedValid = false;
        ArrayList<LastPrice> prices = new ArrayList<>();
        for (int i = 0; i < feedList.size(); i++) {
            AbstractPriceFeed tempFeed = feedList.get(i);

            LastPrice lastPrice = tempFeed.getLastPrice(pair);
            if (lastPrice != null && !lastPrice.isError()) {
                prices.add(lastPrice);
                if (i == 0) {
                    isMainFeedValid = true;
                }
            } else {
                LOG.warn("Error (null) while updating " + pair.getOrderCurrency().getCode() + ""
                        + " price from " + tempFeed.name);
            }
        }
        response.setMainFeedValid(isMainFeedValid);
        response.setPrices(prices);
        return response;
    }



    public ArrayList<AbstractPriceFeed> getFeedList() {
        return feedList;
    }


    public CurrencyPair getPair() {
        return pair;
    }

    public void setPair(CurrencyPair pair) {
        this.pair = pair;
    }

    /**
     * class to wrap results from getLastPrices
     */
    public class LastPriceResponse {

        private boolean mainFeedValid;
        private ArrayList<LastPrice> prices;

        public LastPriceResponse() {
        }

        public boolean isMainFeedValid() {
            return mainFeedValid;
        }

        public void setMainFeedValid(boolean mainFeedValid) {
            this.mainFeedValid = mainFeedValid;
        }

        public ArrayList<LastPrice> getPrices() {
            return prices;
        }

        public void setPrices(ArrayList<LastPrice> prices) {
            this.prices = prices;
        }
    }
}