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
import com.nubits.nubot.exchanges.ExchangeFacade;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Settings;
import com.nubits.nubot.models.*;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.trading.ErrorManager;
import com.nubits.nubot.trading.ServiceInterface;
import com.nubits.nubot.trading.TradeInterface;
import com.nubits.nubot.trading.TradeUtils;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.utils.Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class PoloniexWrapper implements TradeInterface {

    private static final Logger LOG = LoggerFactory.getLogger(PoloniexWrapper.class.getName());
    private ApiKeys keys;
    protected PoloniexService service;
    private Exchange exchange;
    private String checkConnectionUrl = "http://poloniex.com/";
    private final String SIGN_HASH_FUNCTION = "HmacSHA512";
    private final String ENCODING = "UTF-8";
    //Entry points
    private final String API_BASE_URL = "https://poloniex.com/tradingApi";
    private final String API_GET_BALANCES = "returnCompleteBalances";
    private final String API_GET_ORDERS = "returnOpenOrders";
    private final String API_GET_TRADES = "returnTradeHistory";
    private final String API_SELL = "sell";
    private final String API_BUY = "buy";
    private final String API_CANCEL_ORDER = "cancelOrder";
    //Errors
    private ErrorManager errors = new ErrorManager();
    private final String TOKEN_ERR = "error";
    private final String TOKEN_BAD_RETURN = "No Connection With Exchange";

    private long nonceCount = new Long(System.currentTimeMillis() / 100000).longValue();
    private boolean fixNonce = false;

    public PoloniexWrapper(ApiKeys keys, Exchange exchange) {
        this.keys = keys;
        this.exchange = exchange;
        service = new PoloniexService(keys);
        setupErrors();
    }

    private void setupErrors() {
        errors.setExchangeName(exchange);
    }

    private ApiResponse getQuery(String url, String method, HashMap<String, String> query_args, boolean needAuth, boolean isGet) {

        int maxRetry = 10;
        boolean done = false;
        int i = 0;
        ApiResponse response = null;
        while (!done) {
            response = getQueryMain(url, method, query_args, needAuth, isGet);
            if (!response.isPositive()) {
                String errMsg = response.getError().getDescription();
                if (errMsg.contains("Nonce must be greater than ")) {
                    //handle nonce exception. get the nonce they want and add some
                    String stmp = "Nonce must be greater than ";
                    int k = errMsg.indexOf(stmp);
                    int q = errMsg.indexOf(". You");
                    String subs = errMsg.substring(k + stmp.length(), q);
                    fixNonce = true;
                    long greaterNonce = new Long(subs);
                    int addNonce = 5;
                    this.nonceCount = greaterNonce + addNonce;
                    LOG.debug("retry with corrected nonce " + this.nonceCount);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {

                    }

                }

            } else
                return response;
            i++;
            if (i > maxRetry) {
                LOG.error("nonce reset failed");
                return response;
            }
        }
        return response;
    }

    private ApiResponse getQueryMain(String url, String method, HashMap<String, String> query_args, boolean needAuth, boolean isGet) {
        ApiResponse apiResponse = new ApiResponse();
        String queryResult = query(url, method, query_args, needAuth, false);
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
                String errorMessage = (String) httpAnswerJson.get("error");
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(errorMessage);
                LOG.debug("Poloniex API returned an error: " + errorMessage);

                apiResponse.setError(apiErr);
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
        } catch (ParseException ex) {
            LOG.error("httpresponse: " + queryResult + " \n" + ex.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }
        return apiResponse;
    }

    @Override
    public ApiResponse getAvailableBalances(CurrencyPair pair) {
        return getBalanceImpl(pair, null);
    }

    @Override
    public ApiResponse getAvailableBalance(Currency currency) {
        return getBalanceImpl(null, currency);
    }

    private ApiResponse getBalanceImpl(CurrencyPair pair, Currency currency) {
        LOG.trace("get balance");

        //Swap the pair for the request
        ApiResponse apiResponse = new ApiResponse();

        String url = API_BASE_URL;
        String method = API_GET_BALANCES;
        HashMap<String, String> query_args = new HashMap<>();
        boolean isGet = false;

        LOG.trace("get from " + url);
        LOG.trace("method " + method);
        ApiResponse response = getQuery(url, method, query_args, true, isGet);

        LOG.trace("response " + response);
        if (!response.isPositive()) {
            return response;
        }

        JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
        LOG.trace("balance answer " + httpAnswerJson);

        if (currency != null) {
            //looking for a specific currency
            String lookingFor = currency.getCode().toUpperCase();
            if (httpAnswerJson.containsKey(lookingFor)) {
                JSONObject balanceJSON = (JSONObject) httpAnswerJson.get(lookingFor);
                double balanceD = Utils.getDouble(balanceJSON.get("available"));
                LOG.trace("balance double : " + balanceD);
                apiResponse.setResponseObject(new Amount(balanceD, currency));
            } else {
                String errorMessage = "Cannot find a balance for currency " + lookingFor;
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(errorMessage);
                apiResponse.setError(apiErr);
            }
        } else {
            //get all balances for the pair
            boolean foundNBTavail = false;
            boolean foundPEGavail = false;
            Amount NBTAvail = new Amount(0, pair.getOrderCurrency());
            Amount PEGAvail = new Amount(0, pair.getPaymentCurrency());

            Amount PEGonOrder = new Amount(0, pair.getPaymentCurrency());
            Amount NBTonOrder = new Amount(0, pair.getOrderCurrency());

            String NBTcode = pair.getOrderCurrency().getCode().toUpperCase();
            String PEGcode = pair.getPaymentCurrency().getCode().toUpperCase();

            if (httpAnswerJson.containsKey(NBTcode)) {
                JSONObject balanceJSON = (JSONObject) httpAnswerJson.get(NBTcode);
                double tempAvailablebalance = Utils.getDouble(balanceJSON.get("available"));
                double tempLockedebalance = Utils.getDouble(balanceJSON.get("onOrders"));
                NBTAvail = new Amount(tempAvailablebalance, pair.getOrderCurrency());
                NBTonOrder = new Amount(tempLockedebalance, pair.getOrderCurrency());
                foundNBTavail = true;
            }
            if (httpAnswerJson.containsKey(PEGcode)) {
                JSONObject balanceJSON = (JSONObject) httpAnswerJson.get(PEGcode);
                double tempAvailablebalance = Utils.getDouble(balanceJSON.get("available"));
                double tempLockedebalance = Utils.getDouble(balanceJSON.get("onOrders"));
                PEGAvail = new Amount(tempAvailablebalance, pair.getPaymentCurrency());
                PEGonOrder = new Amount(tempLockedebalance, pair.getPaymentCurrency());
                foundPEGavail = true;
            }
            PairBalance balance = new PairBalance(PEGAvail, NBTAvail, PEGonOrder, NBTonOrder);
            apiResponse.setResponseObject(balance);
            if (!foundNBTavail || !foundPEGavail) {
                LOG.warn("Cannot find a balance for currency with code "
                        + "" + NBTcode + " or " + PEGcode + " in your balance. "
                        + "NuBot assumes that balance is 0");
            }
        }


        return apiResponse;
    }

    @Override
    public ApiResponse getLastPrice(CurrencyPair pair) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ApiResponse sell(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.SELL, pair, amount, rate);
    }

    @Override
    public ApiResponse buy(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.BUY, pair, amount, rate);
    }

    private ApiResponse enterOrder(String type, CurrencyPair pair, double amount, double rate) {
        ApiResponse apiResponse = new ApiResponse();
        boolean isGet = false;
        String url = API_BASE_URL;
        String method;
        if (type.equals(Constant.SELL)) {
            method = API_SELL;
        } else {
            method = API_BUY;
        }

        HashMap<String, String> query_args = new HashMap<>();

        //Swap the pair for the request
        pair = CurrencyPair.swap(pair);
        /*Params
         */
        query_args.put("currencyPair", pair.toStringSep().toUpperCase());
        query_args.put("amount", Double.toString(amount));
        query_args.put("rate", Double.toString(rate));

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            String order_id = (String) httpAnswerJson.get("orderNumber");
            apiResponse.setResponseObject(order_id);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getActiveOrders() {
        return getOrdersImpl(null);
    }

    @Override
    public ApiResponse getActiveOrders(CurrencyPair pair) {
        return getOrdersImpl(pair);
    }

    private ApiResponse getOrdersImpl(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Order> orderList = new ArrayList<Order>();
        boolean isGet = false;
        String url = API_BASE_URL;
        String method = API_GET_ORDERS;
        HashMap<String, String> query_args = new HashMap<>();

        String pairString = "all";
        if (pair != null) {
            pair = CurrencyPair.swap(pair);
            pairString = pair.toStringSep().toUpperCase();
        }

        query_args.put("currencyPair", pairString);

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            if (pairString.equals("all")) {
                JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
                Set<String> set = httpAnswerJson.keySet();
                for (String key : set) {
                    JSONArray tempArray = (JSONArray) httpAnswerJson.get(key);
                    for (int i = 0; i < tempArray.size(); i++) {
                        CurrencyPair cp = CurrencyPair.getCurrencyPairFromString(key, "_");
                        JSONObject orderObject = (JSONObject) tempArray.get(i);
                        Order tempOrder = parseOrder(orderObject, cp);
                        orderList.add(tempOrder);
                    }
                }
            } else {
                JSONArray httpAnswerJson = (JSONArray) response.getResponseObject();
                for (int i = 0; i < httpAnswerJson.size(); i++) {
                    JSONObject orderObject = (JSONObject) httpAnswerJson.get(i);
                    Order tempOrder = parseOrder(orderObject, pair);
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
        ApiResponse apiResp = new ApiResponse();
        Order order = null;

        ApiResponse listApiResp = getActiveOrders();
        if (listApiResp.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) listApiResp.getResponseObject();
            boolean found = false;
            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);
                if (orderID.equals(tempOrder.getId())) {
                    found = true;
                    apiResp.setResponseObject(tempOrder);
                    return apiResp;
                }
            }
            if (!found) {
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription("Cannot find the order with id " + orderID);
                apiResp.setError(apiErr);
                return apiResp;

            }
        } else {
            return listApiResp;
        }

        return apiResp;
    }

    @Override
    public ApiResponse cancelOrder(String orderID, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL;
        boolean isGet = false;
        String method = API_CANCEL_ORDER;
        HashMap<String, String> query_args = new HashMap<>();

        pair = CurrencyPair.swap(pair);
        query_args.put("currencyPair", pair.toStringSep().toUpperCase());
        query_args.put("orderNumber", orderID);

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            apiResponse.setResponseObject(true);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getTxFee() {

        return new ApiResponse(true, Global.options.getTxFee(), null);

    }

    @Override
    public ApiResponse getTxFee(CurrencyPair pair) {
        LOG.debug("Poloniex uses global TX fee, currency pair not supprted. \n" + "now calling getTxFee()");
        return getTxFee();
    }

    @Override
    public ApiResponse isOrderActive(String id) {
        ApiResponse existResponse = new ApiResponse();

        ApiResponse orderDetailResponse = getOrderDetail(id);
        if (orderDetailResponse.isPositive()) {
            Order order = (Order) orderDetailResponse.getResponseObject();
            existResponse.setResponseObject(true);
        } else {
            ApiError err = orderDetailResponse.getError();
            if (err.getDescription().contains("Cannot find the order")) {
                existResponse.setResponseObject(false);

            } else {
                existResponse.setError(err);
            }
        }
        return existResponse;
    }

    @Override
    public ApiResponse clearOrders(CurrencyPair pair) {
        //Since there is no API entry point for that, this call will iterate over actie
        ApiResponse toReturn = new ApiResponse();
        boolean ok = true;

        ApiResponse activeOrdersResponse = getActiveOrders();
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();
            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);

                ApiResponse deleteOrderResponse = cancelOrder(tempOrder.getId(), pair);
                if (deleteOrderResponse.isPositive()) {
                    boolean deleted = (boolean) deleteOrderResponse.getResponseObject();

                    if (deleted) {
                        LOG.warn("Order " + tempOrder.getId() + " deleted succesfully");
                    } else {
                        LOG.warn("Could not delete order " + tempOrder.getId() + "");
                        ok = false;
                    }

                } else {
                    LOG.error(deleteOrderResponse.getError().toString());
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    LOG.error(ex.toString());
                }

            }
            toReturn.setResponseObject(ok);
        } else {
            LOG.error(activeOrdersResponse.getError().toString());
            toReturn.setError(activeOrdersResponse.getError());
            return toReturn;
        }

        return toReturn;
    }

    @Override
    public ApiError getErrorByCode(int code) {
        return null;
    }

    @Override
    public String getUrlConnectionCheck() {
        return checkConnectionUrl;
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
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair) {
        return getTradesImpl(pair, 0);
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair, long startTime) {
        return getTradesImpl(pair, startTime);
    }

    private ApiResponse getTradesImpl(CurrencyPair pair, long startTime) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Trade> tradeList = new ArrayList<Trade>();
        boolean isGet = false;
        String url = API_BASE_URL;
        String method = API_GET_TRADES;
        HashMap<String, String> query_args = new HashMap<>();

        String startDateArg;
        if (startTime == 0) {
            long now = System.currentTimeMillis();
            long yesterday = Math.round((now - Utils.getOneDayInMillis()) / 1000);
            startDateArg = Long.toString(yesterday); //24hours
        } else {
            startDateArg = Long.toString(startTime);
        }

        pair = CurrencyPair.swap(pair);
        query_args.put("currencyPair", pair.toStringSep().toUpperCase());
        query_args.put("start", startDateArg);

        ApiResponse response = getQuery(url, method, query_args, true, isGet);
        if (response.isPositive()) {
            JSONArray httpAnswerJson = (JSONArray) response.getResponseObject();
            for (int i = 0; i < httpAnswerJson.size(); i++) {
                JSONObject tradesObject = (JSONObject) httpAnswerJson.get(i);
                Trade tempTrade = parseTrade(tradesObject, pair);
                tradeList.add(tempTrade);
            }
            apiResponse.setResponseObject(tradeList);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    private Order parseOrder(JSONObject orderObject, CurrencyPair pair) {
        /* {"orderNumber":"120466","type":"sell","rate":"0.025","amount":"100","total":"2.5" */
        Order order = new Order();

        order.setType(((String) orderObject.get("type")).toUpperCase());
        order.setId((String) orderObject.get("orderNumber"));
        order.setAmount(new Amount(Utils.getDouble(orderObject.get("amount")), pair.getPaymentCurrency()));
        order.setPrice(new Amount(Utils.getDouble(orderObject.get("rate")), pair.getOrderCurrency()));
        order.setCompleted(false);
        order.setPair(pair);
        order.setInsertedDate(new Date()); //Not provided

        return order;

    }

    private Trade parseTrade(JSONObject tradeObj, CurrencyPair pair) {
        /* {"date":"2014-02-19 04:55:44","rate":"0.0015","amount":"100","fee":"0.02","total":"0.15","orderNumber":"3048903","type":"sell"}*/
        Trade trade = new Trade();
        trade.setOrder_id((String) tradeObj.get("orderNumber"));

        trade.setExchangeName(ExchangeFacade.POLONIEX);
        trade.setPair(pair);

        trade.setType(((String) tradeObj.get("type")).toUpperCase());
        trade.setAmount(new Amount(Utils.getDouble(tradeObj.get("amount")), pair.getPaymentCurrency()));
        trade.setPrice(new Amount(Utils.getDouble(tradeObj.get("rate")), pair.getOrderCurrency()));
        trade.setFee(new Amount(0, pair.getPaymentCurrency()));

        String date = (String) tradeObj.get("date");
        trade.setDate(parseDate(date));

        return trade;
    }

    private Date parseDate(String dateStr) {
        Date toRet = null;
        //Parse the date
        //Sample 2014-02-19 04:55:44

        String datePattern = "yyyy-MM-dd HH:mm:ss";
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
    public ApiResponse getOrderBook(CurrencyPair pair) {
        throw new UnsupportedOperationException("PoloniexWrapper.getOrderBook() not implemented yet.");
    }

    private class PoloniexService implements ServiceInterface {

        protected ApiKeys keys;

        private PoloniexService(ApiKeys keys) {
            this.keys = keys;
        }

        private PoloniexService() {
            //Used for ticker, does not require auth
        }

        @Override
        public String executeQuery(String base, String method, AbstractMap<String, String> args, boolean needAuth, boolean isGet) {
            String answer = "";
            String signature = "";
            String post_data = "";
            String url = base + method;
            boolean httpError = false;
            HttpsURLConnection connection = null;
            URL queryUrl = null;

            try {
                // build URL

                if (needAuth) {
                    queryUrl = new URL(base);
                } else {
                    queryUrl = new URL(url);
                }
                LOG.trace("Query " + queryUrl);
                connection = (HttpsURLConnection) queryUrl.openConnection();
                connection.setRequestMethod("POST");
                LOG.trace("connection " + connection);
            } catch (IOException e) {
                LOG.error("can't connect to " + queryUrl);
            }


            try {
                // add nonce and build arg list
                if (needAuth) {
                    String nonce = createNonce();
                    LOG.trace("nonce used " + nonce);
                    args.put("nonce", nonce);
                    args.put("command", method);

                    post_data = TradeUtils.buildQueryString(args, ENCODING);

                    // args signature with apache cryptographic tools
                    String toHash = post_data;

                    signature = TradeUtils.signRequest(keys.getPrivateKey(), toHash, SIGN_HASH_FUNCTION, ENCODING);
                }


                // create and setup a HTTP connection

                connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("User-Agent", Settings.APP_NAME);

                if (needAuth) {
                    connection.setRequestProperty("Key", keys.getApiKey());
                    connection.setRequestProperty("Sign", signature);
                }

                connection.setDoOutput(true);
                connection.setDoInput(true);

                //Read the response

                DataOutputStream os = new DataOutputStream(connection.getOutputStream());
                os.writeBytes(post_data);
                os.close();

                BufferedReader br = null;
                boolean toLog = false;
                if (connection.getResponseCode() >= 400) {
                    httpError = true;
                    br = new BufferedReader(new InputStreamReader((connection.getErrorStream())));
                    toLog = true;
                } else {
                    br = new BufferedReader(new InputStreamReader((connection.getInputStream())));
                }

                String output;

                if (httpError) {
                    LOG.error("Post Data: " + post_data);
                }
                LOG.trace("Query to :" + base + "(method=" + method + ")" + " , HTTP response : \n"); //do not log unless is error > 400
                while ((output = br.readLine()) != null) {
                    LOG.trace(output);
                    answer += output;
                }

                if (httpError) {
                    JSONParser parser = new JSONParser();
                    try {
                        JSONObject obj2 = (JSONObject) (parser.parse(answer));
                        answer = (String) obj2.get(TOKEN_ERR);

                    } catch (ParseException ex) {
                        LOG.error(ex.toString());
                        return null;
                    }
                }
            } //Capture Exceptions
            //2ERROR - Poloniex API returned an error: Nonce must be greater than 14296103443350000. You provided 2. [c.n.n.t.w.PoloniexWrapper:106]

            catch (IllegalStateException ex) {
                LOG.error("IllegalStateException: " + ex.toString());
                return null;
            } catch (NoRouteToHostException | UnknownHostException ex) {
                //Global.BtceExchange.setConnected(false);
                LOG.error("NoRouteToHostException: " + ex.toString());

                answer = TOKEN_BAD_RETURN;
            } catch (IOException ex) {
                LOG.error("IOException: " + ex.toString());
                return null;
            } finally {
                //close the connection, set all objects to null
                connection.disconnect();
            }
            return answer;
        }


        private String createNonce() {

            //potential FIX: add some time to the nonce, since time sync has issues
            //long fixtime = 1000;
            if (!fixNonce)
                return "" + System.currentTimeMillis();
            else {
                nonceCount++;
                LOG.trace("nonce used " + nonceCount);
                return "" + nonceCount;
            }
        }
    }
}
