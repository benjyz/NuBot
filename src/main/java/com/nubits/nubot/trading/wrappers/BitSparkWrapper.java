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

package com.nubits.nubot.trading.wrappers;


import com.nubits.nubot.bot.Global;
import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Settings;
import com.nubits.nubot.models.*;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.trading.ErrorManager;
import com.nubits.nubot.trading.ServiceInterface;
import com.nubits.nubot.trading.Ticker;
import com.nubits.nubot.trading.TradeInterface;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.utils.HttpUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


public class BitSparkWrapper implements TradeInterface {

    //It seems that BitSpark also have a reversed pairing for NBT_BTC.
    //to correctly return a pair balance the pair needs to be NBT_BTC instead of BTC_NBT
    private static final Logger LOG = LoggerFactory.getLogger(BitSparkWrapper.class.getName());
    //Class fields
    private ApiKeys keys;
    protected BitSparkService service;
    private Exchange exchange;
    private final int SPACING_BETWEEN_CALLS = 1100;
    private final int TIME_OUT = 15000;
    private long lastSentTonce = 0L;
    private boolean apiBusy = false;
    private final String SIGN_HASH_FUNCTION = "HmacSHA256";
    private final String ENCODING = "UTF-8";
    private String apiBaseUrl;
    public String checkConnectionUrl;
    private final String API_BASE_URL = "https://bitspark.io";
    private final String API_GET_INFO = "/api/v2/members/me"; //GET
    private final String API_TRADE = "/api/v2/orders"; //POST
    private final String API_ACTIVE_ORDERS = "/api/v2/orders"; //GET
    private final String API_ORDER = "/api/v2/order"; //GET
    private final String API_CANCEL_ORDER = "/api/v2/order/delete"; //POST
    private final String API_CLEAR_ORDERS = "/api/v2/orders/clear"; //POST
    private final String API_GET_TRADES = "/api/v2/trades/my.json"; //GET
    private final String TICKER_BASE = "/api/v2/tickers/";
    //For the ticker entry point, use getTicketPath(CurrencyPair pair)
    // Errors
    ErrorManager errors = new ErrorManager();
    private final String TOKEN_ERR = "error";
    private final String TOKEN_BAD_RETURN = "No Connection With Exchange";

    public BitSparkWrapper(ApiKeys keys, Exchange exchange) {
        this.keys = keys;
        this.exchange = exchange;
        this.apiBaseUrl = API_BASE_URL;
        this.checkConnectionUrl = API_BASE_URL;
        service = new BitSparkService(keys);
        setupErrors();

    }

    protected Long createNonce(String requester) {
        Long toReturn = 0L;
        if (!apiBusy) {
            toReturn = getNonceInternal(requester);
        } else {
            try {

                if (Global.options.isVerbose()) {
                    LOG.debug(System.currentTimeMillis() + " - Api is busy, I'll sleep and retry in a few ms (" + requester + ")");
                }

                Thread.sleep(Math.round(2.2 * SPACING_BETWEEN_CALLS));
                createNonce(requester);
            } catch (InterruptedException e) {
                LOG.error(e.toString());
            }
        }
        return toReturn;
    }

    private void setupErrors() {

        errors.setExchangeName(exchange);

    }

    private String getTickerPath(CurrencyPair pair) {
        return TICKER_BASE + pair.toString();
    }

    private ApiResponse getQuery(String url, String method, TreeMap<String, String> query_args, boolean needAuth, boolean isGet) {
        ApiResponse apiResponse = new ApiResponse();
        String queryResult = query(url, method, query_args, needAuth, isGet);
        if (queryResult == null) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.noConnectionError);
            return apiResponse;
        }
        JSONParser parser = new JSONParser();

        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
            if (httpAnswerJson.containsKey("error")) {
                JSONObject error = (JSONObject) httpAnswerJson.get("error");
                int code = Integer.parseInt(error.get("code").toString());
                String msg = error.get("message").toString();
                ApiError errorObj = errors.apiReturnError;
                errorObj.setDescription(msg);
                apiResponse.setError(errorObj);
                return apiResponse;
            } else {
                apiResponse.setResponseObject(httpAnswerJson);
            }
        } catch (ClassCastException cce) {
            //if casting to a JSON object failed, try a JSON Array
            try {
                JSONArray httpAnswerJson = (JSONArray) (parser.parse(queryResult));
                apiResponse.setResponseObject(httpAnswerJson);
            } catch (ParseException pe) {
                LOG.error("httpResponse: " + queryResult + " \n" + pe.toString());
                apiResponse.setError(errors.parseError);
            }
        } catch (ParseException pe) {
            LOG.error("httpResponse: " + queryResult + " \n" + pe.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }
        return apiResponse;
    }

    @Override
    public ApiResponse getAvailableBalances(CurrencyPair pair) {
        return getBalanceImpl(null, pair);

    }

    @Override
    public ApiResponse getAvailableBalance(Currency currency) {
        return getBalanceImpl(currency, null);
    }

    private ApiResponse getBalanceImpl(Currency currency, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        PairBalance balance = null;
        String url = API_BASE_URL;
        String method = API_GET_INFO;
        boolean isGet = true;
        TreeMap<String, String> query_args = new TreeMap<>();
        /*Params
         *
         */
        query_args.put("canonical_verb", "GET");
        query_args.put("canonical_uri", method);

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            Amount NBTonOrder = null,
                    NBTAvail = null,
                    PEGonOrder = null,
                    PEGAvail = null;
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONArray accounts = (JSONArray) httpAnswerJson.get("accounts");

            if (currency == null) { //Get all balances
                for (int i = 0; i < accounts.size(); i++) {
                    JSONObject balanceObj = (JSONObject) accounts.get(i);
                    String tempCurrency = balanceObj.get("currency").toString();

                    String nbtCurrencyCode = pair.getOrderCurrency().getCode();
                    String pegCurrencyCode = pair.getPaymentCurrency().getCode();

                    if (tempCurrency.equalsIgnoreCase(nbtCurrencyCode)) {
                        NBTAvail = new Amount(Double.parseDouble(balanceObj.get("balance").toString()), pair.getOrderCurrency());
                        NBTonOrder = new Amount(Double.parseDouble(balanceObj.get("locked").toString()), pair.getOrderCurrency());
                    }
                    if (tempCurrency.equalsIgnoreCase(pegCurrencyCode)) {
                        PEGAvail = new Amount(Double.parseDouble(balanceObj.get("balance").toString()), pair.getPaymentCurrency());
                        PEGonOrder = new Amount(Double.parseDouble(balanceObj.get("locked").toString()), pair.getPaymentCurrency());
                    }
                }
                if (NBTAvail != null && NBTonOrder != null
                        && PEGAvail != null && PEGonOrder != null) {
                    balance = new PairBalance(PEGAvail, NBTAvail, PEGonOrder, NBTonOrder);
                    //Pack it into the ApiResponse
                    apiResponse.setResponseObject(balance);
                } else {
                    apiResponse.setError(errors.nullReturnError);
                }
            } else {//return available balance for the specific currency
                boolean found = false;
                Amount amount = null;
                for (int i = 0; i < accounts.size(); i++) {
                    JSONObject balanceObj = (JSONObject) accounts.get(i);
                    String tempCurrency = balanceObj.get("currency").toString();

                    if (tempCurrency.equalsIgnoreCase(currency.getCode())) {
                        amount = new Amount(Double.parseDouble(balanceObj.get("balance").toString()), currency);

                        found = true;
                    }
                }

                if (found) {
                    apiResponse.setResponseObject(amount);
                } else {
                    apiResponse.setError(new ApiError(21341, "Can't find balance for"
                            + " specified currency: " + currency.getCode()));
                }
            }
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getLastPrice(CurrencyPair pair) {
        Ticker ticker = new Ticker();
        ApiResponse apiResponse = new ApiResponse();

        double last = -1;
        double ask = -1;
        double bid = -1;

        String ticker_url = API_BASE_URL + getTickerPath(pair);
        LOG.trace("get content from url " + ticker_url);
        String queryResult = null;
        try {
            queryResult = HttpUtils.getContentForGet(ticker_url, 5000);
        } catch (Exception e) {
            LOG.error("error getting content from " + ticker_url + " " + e);
        }

        /*Sample result
         * {"at":1398410899,
         * "ticker":
         *  {
         *      "buy":"3000.0",
         *      "sell":"3100.0",
         *      "low":"3000.0",
         *      "high":"3000.0",
         *      "last":"3000.0",
         *      "vol":"0.11"}}
         */

        JSONParser parser = new JSONParser();
        try {
            JSONObject httpAnswerJson = (JSONObject) parser.parse(queryResult);
            JSONObject tickerOBJ = (JSONObject) httpAnswerJson.get("ticker");

            last = new Double((String) tickerOBJ.get("last"));
            ask = new Double((String) tickerOBJ.get("buy"));
            bid = new Double((String) tickerOBJ.get("sell"));

            ticker.setAsk(ask);
            ticker.setBid(bid);
            ticker.setLast(last);

            apiResponse.setResponseObject(ticker);
        } catch (ParseException pe) {
            LOG.error("httpResponse: " + queryResult + " \n" + pe.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }
        return apiResponse;
    }

    @Override
    public ApiResponse sell(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.SELL, pair, amount, rate);
    }

    @Override
    public ApiResponse buy(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.BUY, pair, amount, rate);
    }

    public ApiResponse enterOrder(String type, CurrencyPair pair, double amount, double rate) {
        ApiResponse apiResponse = new ApiResponse();
        String order_id = "";
        String url = API_BASE_URL;
        String method = API_TRADE;
        boolean isGet = false;

        TreeMap<String, String> query_args = new TreeMap<>();

        query_args.put("side", type.toLowerCase());
        query_args.put("volume", Double.toString(amount));
        query_args.put("price", Double.toString(rate));
        query_args.put("market", pair.toString());
        query_args.put("canonical_verb", "POST");
        query_args.put("canonical_uri", method);

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            if (httpAnswerJson.containsKey("id")) {
                order_id = httpAnswerJson.get("id").toString();
                apiResponse.setResponseObject(order_id);
            }
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getActiveOrders() {
        ApiError err = errors.genericError;
        err.setDescription("For the BitSpark API you should specify the CurrencyPair"
                + "\n use getActiveOrders(CurrencyPair pair)");
        return new ApiResponse(false, null, err);
    }

    @Override
    public ApiResponse getActiveOrders(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Order> orderList = new ArrayList<Order>();
        String url = API_BASE_URL;
        String method = API_ACTIVE_ORDERS;
        boolean isGet = true;
        TreeMap<String, String> query_args = new TreeMap<>();

        /*Params
         * pair, default all pairs
         */

        query_args.put("canonical_verb", "GET");
        query_args.put("canonical_uri", method);
        query_args.put("market", pair.toString());
        query_args.put("limit", "999"); //default is 10 , max is 1000

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            JSONArray httpAnswerJson = (JSONArray) response.getResponseObject();
            for (Object anOrdersResponse : httpAnswerJson) {
                JSONObject orderResponse = (JSONObject) anOrdersResponse;
                Order tempOrder = parseOrder(orderResponse);
                if (!tempOrder.isCompleted()) //Do not add executed orders
                {
                    orderList.add(tempOrder);
                }
            }
            apiResponse.setResponseObject(orderList);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getOrderDetail(String orderID) {
        ApiResponse apiResponse = new ApiResponse();
        Order order = null;
        String url = API_BASE_URL;
        String method = API_ORDER;
        boolean isGet = true;


        TreeMap<String, String> query_args = new TreeMap<>();
        query_args.put("canonical_verb", "GET");
        query_args.put("canonical_uri", "/api/v2/order");
        query_args.put("id", orderID);

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            if (httpAnswerJson.containsKey("error")) {
                JSONObject error = (JSONObject) httpAnswerJson.get("error");
                int code = ~(Integer) error.get("code");
                String msg = error.get("message").toString();
                apiResponse.setError(new ApiError(code, msg));
                return apiResponse;
            }
            /*Sample result
             * {"id":7,"side":"sell","price":"3100.0","avg_price":"3101.2","state":"wait","market":"btccny","created_at":"2014-04-18T02:02:33Z","volume":"100.0","remaining_volume":"89.8","executed_volume":"10.2","trades":[{"id":2,"price":"3100.0","volume":"10.2","market":"btccny","created_at":"2014-04-18T02:04:49Z","side":"sell"}]}
             */
            apiResponse.setResponseObject(parseOrder(httpAnswerJson));
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse cancelOrder(String orderID, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL;
        String method = API_CANCEL_ORDER;
        boolean isGet = false;

        TreeMap<String, String> query_args = new TreeMap<>();

        query_args.put("id", orderID);
        query_args.put("canonical_verb", "POST");
        query_args.put("canonical_uri", method);

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            if (httpAnswerJson.containsKey("error")) {
                JSONObject error = (JSONObject) httpAnswerJson.get("error");
                int code = (Integer) error.get("code");
                String msg = error.get("message").toString();
                apiResponse.setError(new ApiError(code, msg));
                return apiResponse;
            }
            /*Sample result
             * Cancel order is an asynchronous operation. A success response only means your cancel
             * request has been accepted, it doesn't mean the order has been cancelled.
             * You should always use /api/v2/order or websocket api to get order's latest state.
             */

            apiResponse.setResponseObject(true);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse isOrderActive(String id) {
        boolean exists = false;
        ApiResponse existResponse = new ApiResponse();

        ApiResponse orderDetailResponse = getOrderDetail(id);
        if (orderDetailResponse.isPositive()) {
            Order order = (Order) orderDetailResponse.getResponseObject();
            if (order.isCompleted()) {
                exists = false;
            } else {
                exists = true;
            }
            existResponse.setResponseObject(exists);
        } else {
            ApiError err = orderDetailResponse.getError();
            if (err.getCode() == 2004) {
                exists = false; //Order has been canceled or is already completed
                existResponse.setResponseObject(exists);
            } else {
                existResponse.setError(err);
                LOG.error(existResponse.getError().toString());
            }
        }

        return existResponse;
    }

    @Override
    public ApiResponse getTxFee() {
        return getTxFeeImpl();
    }

    @Override
    public ApiResponse getTxFee(CurrencyPair pair) {
        return getTxFeeImpl();
    }

    private ApiResponse getTxFeeImpl() {

        return new ApiResponse(true, Global.options.getTxFee(), null);

    }

    @Override
    public ApiError getErrorByCode(int code) {
        return null;
    }

    @Override
    public String getUrlConnectionCheck() {
        return API_BASE_URL;
    }

    @Override
    public String query(String base, String method, AbstractMap<String, String> args, boolean needAuth, boolean isGet) {
        String queryResult = TOKEN_BAD_RETURN; //Will return this string in case it fails
        if (exchange.getLiveData().isConnected()) {
            if (exchange.isFree()) {
                exchange.setBusy();
                queryResult = service.executeQuery(base, method, args, needAuth, isGet);
                exchange.setFree();
            } else {
                //Another thread is probably executing a query. Init the retry procedure
                long sleeptime = Settings.RETRY_SLEEP_INCREMENT * 1;
                int counter = 0;
                long startTimeStamp = System.currentTimeMillis();
                LOG.debug(method + " blocked, another call is being processed ");
                boolean exit = false;
                do {
                    counter++;
                    sleeptime = counter * Settings.RETRY_SLEEP_INCREMENT; //Increase sleep time
                    sleeptime += (int) (Math.random() * 200) - 100;// Add +- 100 ms random to facilitate competition
                    LOG.debug("Retrying for the " + counter + " time. Sleep for " + sleeptime + "; Method=" + method);
                    try {
                        Thread.sleep(sleeptime);
                    } catch (InterruptedException e) {
                        LOG.error(e.toString());
                    }

                    //Try executing the call
                    if (exchange.isFree()) {
                        LOG.debug("Finally the exchange is free, executing query after " + counter + " attempt. Method=" + method);
                        exchange.setBusy();
                        queryResult = service.executeQuery(base, method, args, needAuth, isGet);
                        exchange.setFree();
                        break; //Exit loop
                    } else {
                        LOG.debug("Exchange still busy : " + counter + " .Will retry soon; Method=" + method);
                        exit = false;
                    }
                    if (System.currentTimeMillis() - startTimeStamp >= Settings.TIMEOUT_QUERY_RETRY) {
                        exit = true;
                        LOG.error("Method=" + method + " failed too many times and timed out. attempts = " + counter);
                    }
                } while (!exit);
            }
        } else {
            LOG.error("The bot will not execute the query, there is no connection with" + exchange.getName());
            queryResult = TOKEN_BAD_RETURN;
        }
        return queryResult;
    }


    private Order parseOrder(JSONObject jsonObject) {
        Order order = new Order();
        String status = jsonObject.get("state").toString();

        boolean executed = false;
        switch (status) {
            case "wait": {
                executed = false;
                break;
            }
            case "done": {
                executed = true;
                break;
            }
            case "cancel": {
                executed = true;
                break;
            }
        }

        //Create a CurrencyPair object
        CurrencyPair cp = CurrencyPair.getCurrencyPairFromString(jsonObject.get("market").toString(), "");

        order.setPair(cp);
        order.setCompleted(executed);
        order.setId("" + jsonObject.get("id"));
        order.setAmount(new Amount(Double.parseDouble(jsonObject.get("remaining_volume").toString()), cp.getOrderCurrency()));
        order.setPrice(new Amount(Double.parseDouble(jsonObject.get("price").toString()), cp.getPaymentCurrency()));

        order.setInsertedDate(parseDate(jsonObject.get("created_at").toString()));

        order.setType(jsonObject.get("side").toString());
        //Created at?

        return order;

    }


    private Date parseDate(String dateStr) {
        Date toRet = null;
        //Parse the date
        //Sample 2014-08-19T10:23:49+01:00

        //Remove the Timezone
        dateStr = dateStr.substring(0, dateStr.length() - 1);
        String datePattern = "yyyy-MM-dd'T'HH:mm:ss";
        DateFormat df = new SimpleDateFormat(datePattern, Locale.ENGLISH);
        try {
            toRet = df.parse(dateStr);
        } catch (java.text.ParseException ex) {
            LOG.error(ex.toString());
            toRet = new Date();
        }
        return toRet;
    }

    @Override
    public ApiResponse clearOrders(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL;
        String method = API_CLEAR_ORDERS;
        boolean isGet = false;

        TreeMap<String, String> query_args = new TreeMap<>();

        query_args.put("canonical_verb", "POST");
        query_args.put("canonical_uri", method);

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            apiResponse.setResponseObject(true);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    //DO NOT USE THIS METHOD DIRECTLY, use CREATENONCE
    private long getNonceInternal(String requester) {
        apiBusy = true;
        long currentTime = System.currentTimeMillis();

        if (Global.options.isVerbose()) {
            LOG.debug(currentTime + " Now apiBusy! req : " + requester);
        }

        long timeElapsedSinceLastCall = currentTime - lastSentTonce;
        if (timeElapsedSinceLastCall < SPACING_BETWEEN_CALLS) {
            try {
                long sleepTime = SPACING_BETWEEN_CALLS;
                Thread.sleep(sleepTime);
                currentTime = System.currentTimeMillis();
                if (Global.options != null) {
                    if (Global.options.isVerbose()) {
                        LOG.debug("Just slept " + sleepTime + "; req : " + requester);
                    }
                }
            } catch (InterruptedException e) {
                LOG.error(e.toString());
            }
        }

        lastSentTonce = currentTime;

        if (Global.options.isVerbose()) {
            LOG.debug("Final tonce to be sent: req : " + requester + " ; Tonce=" + lastSentTonce);
        }

        apiBusy = false;
        return lastSentTonce;
    }

    @Override
    public void setKeys(ApiKeys keys) {
        this.keys = keys;
    }

    @Override
    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        this.checkConnectionUrl = apiBaseUrl;

    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair) {
        return getLastTradesImpl(pair, 0);
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair, long startTime) {
        return getLastTradesImpl(pair, startTime);
    }

    public ApiResponse getLastTradesImpl(CurrencyPair pair, long startTime) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL;
        String method = API_GET_TRADES;
        boolean isGet = true;
        TreeMap<String, String> query_args = new TreeMap<>();
        ArrayList<Trade> tradeList = new ArrayList<>();

        query_args.put("canonical_verb", "GET");
        query_args.put("canonical_uri", method);
        query_args.put("market", pair.toString());
        query_args.put("limit", "1000");

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            LOG.debug("A maximum of 1000 trades can be returned from the BitSpark API");
            JSONArray httpAnswerJson = (JSONArray) response.getResponseObject();
            for (Iterator<JSONObject> trade = httpAnswerJson.iterator(); trade.hasNext(); ) {
                Trade thisTrade = parseTrade(trade.next());
                if (thisTrade.getDate().getTime() < (startTime * 1000L)) {
                    continue;
                }
                tradeList.add(thisTrade);
            }
            apiResponse.setResponseObject(tradeList);
        } else {
            apiResponse = response;
        }
        return apiResponse;
    }

    public Trade parseTrade(JSONObject in) {
        Trade out = new Trade();
        /*
         {
         "id":273,
         "market":"nbtbtc",
         "funds":"0.00005536",
         "price":"0.002768",
         "side":"ask",
         "volume":"0.02",
         "created_at":"2014-12-04T17:32:55+08:00"
         }
         */
        //set id and order_id
        out.setId(in.get("id").toString());
        out.setOrder_id(in.get("id").toString());
        //get and set currency pair
        CurrencyPair pair = CurrencyPair.getCurrencyPairFromString(in.get("market").toString(), "");
        out.setPair(pair);
        //set the type
        out.setType(in.get("side").toString().equals("bid") ? Constant.BUY : Constant.SELL);
        //get and set the price
        Amount price = new Amount(Double.parseDouble(in.get("price").toString()), pair.getPaymentCurrency());
        out.setPrice(price);
        //get and set the amount
        Amount amount = new Amount(Double.parseDouble(in.get("volume").toString()), pair.getOrderCurrency());
        out.setAmount(amount);
        //set the Date
        out.setDate(parseDate(in.get("created_at").toString()));
        //set the exchange name
        out.setExchangeName(exchange.getName());

        return out;
    }

    @Override
    public ApiResponse getOrderBook(CurrencyPair pair) {
        throw new UnsupportedOperationException("BitSparkWrapper.getOrderBook() not implemented yet.");
    }

    private class BitSparkService implements ServiceInterface {
        protected ApiKeys keys;

        public BitSparkService(ApiKeys keys) {
            this.keys = keys;
        }


        @Override
        public String executeQuery(String base, String method, AbstractMap<String, String> args, boolean needAuth, boolean isGet) {

            args.put("access_key", keys.getApiKey());

            String messageDbg = (String) args.get("canonical_verb") + " " + (String) args.get("canonical_uri");
            args.put("tonce", createNonce(messageDbg).toString());

            args.put("signature", getSign(args));

            String canonical_verb = (String) args.get("canonical_verb");
            args.remove("canonical_verb");
            String canonical_uri = (String) args.get("canonical_uri");
            args.remove("canonical_uri");
            //LOG.debug("Calling " + canonical_uri + " with params:" + args);
            Document doc;
            String response = null;
            try {
                String url = base + canonical_uri;
                //LOG.debug("url = " + url);
                Connection connection = HttpUtils.getConnectionForPost(url, args).timeout(TIME_OUT);


                connection.ignoreHttpErrors(true);
                if ("post".equalsIgnoreCase(canonical_verb)) {
                    doc = connection.ignoreContentType(true).post();
                } else {
                    doc = connection.ignoreContentType(true).get();
                }
                response = doc.body().text();

                //possible errors
                // {"error":{"code":2002,"message":"Failed to create order. Reason: invalid price"}}
                return response;
            } catch (Exception e) {
                LOG.error(e.toString());
                return null;
            } finally {
                LOG.debug("result:{}" + response);
            }
        }

        private String getSign(AbstractMap<String, String> parameters) {
            if (parameters.containsKey("signature")) {
                parameters.remove("signature");
            }

            StringBuilder parameter = new StringBuilder();
            for (Map.Entry entry : parameters.entrySet()) {
                if (entry.getKey().equals("canonical_verb") || entry.getKey().equals("canonical_uri")) {
                    continue;
                }

                parameter.append("&").append(entry.getKey()).append("=").append(entry.getValue());
            }
            if (parameter.length() > 0) {
                parameter.deleteCharAt(0);
            }
            String canonical_verb = parameters.get("canonical_verb");
            String canonical_uri = parameters.get("canonical_uri");

            String signStr = String.format("%s|%s|%s", canonical_verb, canonical_uri, parameter.toString());
            try {
                Mac mac = Mac.getInstance(SIGN_HASH_FUNCTION);
                SecretKeySpec keyspec = new SecretKeySpec(keys.getPrivateKey().getBytes(ENCODING), SIGN_HASH_FUNCTION);
                mac.init(keyspec);
                mac.update(signStr.getBytes(ENCODING));
                return String.format("%064x", new BigInteger(1, mac.doFinal()));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
