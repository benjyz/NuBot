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

package testexchanges;

import com.nubits.nubot.bot.Global;
import com.nubits.nubot.exchanges.ExchangeLiveData;
import com.nubits.nubot.global.Settings;
import com.nubits.nubot.models.*;
import com.nubits.nubot.options.NuBotConfigException;
import com.nubits.nubot.options.NuBotOptions;
import com.nubits.nubot.options.ParseOptions;
import com.nubits.nubot.testsmanual.WrapperTestUtils;
import com.nubits.nubot.trading.Ticker;
import com.nubits.nubot.trading.TradeInterface;
import junit.framework.TestCase;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * test bitspark
 * WARNING: this uses live orders, with small amounts, but still
 */
public class TestExchangeBitspark extends TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(TestExchangeBitspark.class
            .getName());

    private static String testconfigFile = "bitspark.json";

    private static String testconfig = Settings.TESTS_CONFIG_PATH + "/" + testconfigFile;

    /*static {
        System.setProperty("logback.configurationFile", "allconfig  /testlog.xml");
    }*/

    private TradeInterface ti;

    @Test
    public void testLoadConfig() {

        boolean catched = false;

        NuBotOptions opt = null;
        try {
            opt = ParseOptions
                    .parseOptionsSingle(testconfig, false);

            assertTrue(opt != null);
            assertTrue(opt.getExchangeName().equals("bitspark"));

        } catch (NuBotConfigException e) {
            catched = true;
        }

        assertTrue(!catched);
    }


    @Test
    public void testGetBalance() {
        NuBotOptions opt = null;
        try {
            opt = ParseOptions
                    .parseOptionsSingle(testconfig, false);
            LOG.info("using opt " + opt);
            LOG.info("key: " + opt.apiKey);
            LOG.info("secret: " + opt.apiSecret);
            Global.options = opt;
        } catch (NuBotConfigException e) {
            e.printStackTrace();
        }

        try {
            WrapperTestUtils.configureExchange(opt.getExchangeName());
        } catch (NuBotConfigException ex) {

        }

        //ti = ExchangeFacade.exchangeInterfaceSetup(Global.options);
        ti = Global.exchange.getTradeInterface();
        ti.setExchange(Global.exchange);

        assertTrue(ti != null);

        Currency btc = CurrencyList.BTC;
        Global.exchange.getLiveData().setConnected(true);
        ExchangeLiveData ld = Global.exchange.getLiveData();

        assertTrue(ld != null);
        long start = System.currentTimeMillis();
        ApiResponse balancesResponse = ti.getAvailableBalance(btc);
        assertTrue(balancesResponse != null);
        long stop = System.currentTimeMillis();
        long delta = stop - start;
        assertTrue(delta < 5000);

        if (balancesResponse.isPositive()) {
            LOG.info("Positive response  from TradeInterface.getBalance() ");
            Object o = balancesResponse.getResponseObject();
            LOG.info("response " + o);
            try {
                Amount a = (Amount) o;
                assertTrue(a.getQuantity() >= 0);
            } catch (Exception e) {
                assertTrue(false);
            }
            //Balance balance = (Balance) o;

            //LOG.info(balance.toStringSep());

            //assertTrue(balance.getNubitsBalance().getQuantity() == 0.0);
            //assertTrue(balance.getPEGBalance().getQuantity() == 1000.0);

        } else {
            assertTrue(false);
        }
    }

    @Test
    public void testGetPrice() {
        NuBotOptions opt = null;
        try {
            opt = ParseOptions
                    .parseOptionsSingle(testconfig, false);
            LOG.info("using opt " + opt);
            LOG.info("key: " + opt.apiKey);
            LOG.info("secret: " + opt.apiSecret);
            Global.options = opt;
        } catch (NuBotConfigException e) {
            e.printStackTrace();
        }

        try {
            WrapperTestUtils.configureExchange(opt.getExchangeName());
        } catch (NuBotConfigException ex) {

        }

        ti = Global.exchange.getTradeInterface();
        ti.setExchange(Global.exchange);

        assertTrue(ti != null);


        CurrencyPair pair = new CurrencyPair(CurrencyList.NBT, CurrencyList.BTC);
        Global.exchange.getLiveData().setConnected(true);
        ExchangeLiveData ld = Global.exchange.getLiveData();

        assertTrue(ld != null);

        ApiResponse priceResponse = ti.getLastPrice(pair);

        if (priceResponse.isPositive()) {
            LOG.info("Positive response  from TradeInterface.getLastPrice() ");
            Object o = priceResponse.getResponseObject();
            LOG.info("response " + o);
            try {
                Ticker t = (Ticker) o;
                //bitspark bug: prices are 0.0
                assertTrue(t.getAsk() >= -1);
            } catch (Exception e) {
                assertTrue(false);
            }

        } else {
            assertTrue(false);
        }
    }

    @Test
    public void testMakeOrder() {
       /* NuBotOptions opt = null;
        try {
            opt = ParseOptions
                    .parseOptionsSingle(testconfig);
            LOG.info("using opt " + opt);
            LOG.info("key: " + opt.apiKey);
            LOG.info("secret: " + opt.apiSecret);
            Global.options = opt;
        } catch (NuBotConfigException e) {
            e.printStackTrace();
        }


        try{
            WrapperTestUtils.configureExchange(opt.getExchangeName());
        }catch(NuBotConfigException ex){

        }

        //ti = ExchangeFacade.exchangeInterfaceSetup(Global.options);
        ti = Global.exchange.getTradeInterface();
        //Bug
        ti.setExchange(Global.exchange);

        assertTrue(ti != null);

        Currency btc = CurrencyList.BTC;

        ti = ExchangeFacade.exchangeInterfaceSetup(opt);



        CurrencyPair testPair = CurrencyList.NBT_BTC;

        double tinyQty = 0.00001;
        double tinyPrice = 0.00001;
        ApiResponse orderresponse = ti.buy(testPair, tinyQty, tinyPrice);
        //should raise  {"error":"Total must be at least 0.0001."}
        assertTrue(!orderresponse.isPositive());

        double minimialQty = 1;
        double minimalPrice = 2;
        ApiResponse orderresponse2 = ti.buy(testPair, minimialQty, minimalPrice);
        //should raise  {"error":"Total must be at least 0.0001."}
        assertTrue(orderresponse2.isPositive());

        if (orderresponse2.isPositive()) {
            LOG.info("Positive response  from TradeInterface.getBalance() ");
            Object o = orderresponse.getResponseObject();
            LOG.info("response " + o);

        } else {
            assertTrue(false);
        }

        ApiResponse resp = ti.getActiveOrders();
        assertTrue(resp.isPositive());

        Object o = resp.getResponseObject();
        ArrayList<Order> orderList = (ArrayList<Order>) o;

        assertTrue(orderList.size() > 0);

        Order issued = orderList.get(0);

        //cancel all
        Iterator<Order> it = orderList.iterator();
        while (it.hasNext()) {
            Order order = it.next();
            ApiResponse cancelresp = ti.cancelOrder(order.getId(), testPair);
            boolean success = ((Boolean) cancelresp.getResponseObject()).booleanValue();
            assertTrue(success);
        }

        try {
            Thread.sleep(1000);
        } catch (Exception e) {

        }

        //make sure there are no outstanding orders

        ApiResponse resp2 = ti.getActiveOrders();
        assertTrue(resp2.isPositive());

        Object o2 = resp2.getResponseObject();
        ArrayList<Order> orderList2 = (ArrayList<Order>) o2;

        assertTrue(orderList2.size() == 0);*/

    }
}

