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

public class GooglePriceFeed extends AbstractPriceFeed {

    private static final Logger LOG = LoggerFactory.getLogger(GooglePriceFeed.class.getName());

    public static final String name = FeedFacade.GooglePriceFeed;

    public GooglePriceFeed() {
        refreshMinTime = 8 * 60 * 60 * 1000; //8 hours
    }

    @Override
    public LastPrice getLastPrice(CurrencyPair pair) {
        long now = System.currentTimeMillis();
        long diff = now - lastRequest;
        if (diff >= refreshMinTime) {
            String url = getUrl(pair);
            String htmlString;
            try {
                htmlString = Utils.getHTML(url, true);
            } catch (IOException ex) {
                LOG.error(ex.toString());
                return new LastPrice(true, name, pair.getOrderCurrency(), null);
            }
            JSONParser parser = new JSONParser();
            try {
                //Sample asnwer : // [ { "id": "-2001" ,"t" : "GBPUSD" ,"e" : "CURRENCY" ,"l" : "1.5187" ,"l_fix" : "" ,"l_cur" : "" ,"s": "0" ,"ltt":"" ,"lt" : "Apr 25, 11:55AM GMT" ,"lt_dts" : "2015-04-25T11:55:00Z" ,"c" : "0.00000" ,"c_fix" : "" ,"cp" : "0.000" ,"cp_fix" : "" ,"ccol" : "chb" ,"pcls_fix" : "" } ]
                htmlString = htmlString.replace("//","").replace("[","").replace("]","");

                JSONObject httpAnswerJson = (JSONObject) (parser.parse(htmlString));
                double last = Utils.getDouble((String) httpAnswerJson.get("l"));
                last = Utils.round(last, 8);
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
        String from = pair.getOrderCurrency().getCode().toUpperCase();
        String to = pair.getPaymentCurrency().getCode().toUpperCase();
        return "http://www.google.com/finance/info?q=CURRENCY%3a"+from+""+to;
    }
}
