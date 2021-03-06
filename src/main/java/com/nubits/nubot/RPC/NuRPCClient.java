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

package com.nubits.nubot.RPC;

import com.nubits.nubot.bot.Global;
import com.nubits.nubot.bot.SessionManager;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.models.CurrencyPair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


/**
 * RPC Client for Nu Wallet
 */
public class NuRPCClient {

    public static final String USDchar = "B";
    private static final Logger LOG = LoggerFactory.getLogger(NuRPCClient.class.getName());
    private static final String COMMAND_GET_INFO = "getinfo";
    private static final String COMMAND_LIQUIDITYINFO = "liquidityinfo";
    private static final String COMMAND_GETLIQUIDITYINFO = "getliquidityinfo";
    private String ip;
    private int port;
    private String rpcUsername;
    private String rpcPassword;
    private boolean connected;
    private boolean useIdentifier;
    private String custodianPublicAddress;
    private String exchangeName;
    private CurrencyPair pair;


    public NuRPCClient(String ip, int port, String rpcUser, String rpcPass, boolean useIdentifier, String custodianPublicAddress, CurrencyPair pair, String exchangeName) {
        this.ip = ip;
        this.port = port;
        this.rpcPassword = rpcPass;
        this.rpcUsername = rpcUser;
        this.useIdentifier = useIdentifier;
        this.custodianPublicAddress = custodianPublicAddress;
        this.pair = pair;
        this.exchangeName = exchangeName;

    }

    //Public Methods
    public JSONObject submitLiquidityInfo(String currencyChar, double buyamount, double sellamount, int tier) {

        /*
         * String[] params = { USDchar,buyamount,sellamount,custodianPublicAddress, identifier* };
         * identifier default empty string
         */


        List params;
        if (useIdentifier) {
            params = Arrays.asList(currencyChar, buyamount, sellamount, custodianPublicAddress, generateIdentifier(tier));
        } else {
            params = Arrays.asList(currencyChar, buyamount, sellamount, custodianPublicAddress);
        }


        LOG.debug("RPC parameters " + params.toString());

        JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_LIQUIDITYINFO, params);
        if (json != null) {

            if (json.get("null") == null) {
                //Correct answer, try to getliquidityinfo
                LOG.debug("RPC : Liquidity info submitted correctly.");
                JSONObject jo = new JSONObject();
                jo.put("submitted", true);
                return jo;
            } else if ((JSONObject) json.get("result") != null) {
                return (JSONObject) json.get("result"); //Correct answer
            } else {
                return (JSONObject) json.get("error");
            }

        } else {
            return new JSONObject();
        }
    }

    public JSONObject getLiquidityInfo(String currency) {

        List params = Arrays.asList(currency);

        JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GETLIQUIDITYINFO, params);
        if (json != null) {

            if ((JSONObject) json.get("result") != null) {
                return (JSONObject) json.get("result"); //Some answer
            } else {
                return (JSONObject) json.get("error");
            }

        } else {
            return new JSONObject();
        }
    }

    public double getLiquidityInfo(String currency, String type, String address) {
        JSONObject toReturn = null;


        //String[] params = { USDchar,buyamount,sellamount,custodianPublicAddress };
        List params = Arrays.asList(currency);

        JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GETLIQUIDITYINFO, params);
        if (json != null) {

            if ((JSONObject) json.get("result") != null) {
                JSONObject result = (JSONObject) json.get("result");
                JSONObject total = (JSONObject) result.get(address);
                double toRet = -1;
                if (type.equalsIgnoreCase(Constant.SELL)) {
                    toRet = (double) total.get("sell");
                } else if (type.equalsIgnoreCase(Constant.BUY)) {
                    toRet = (double) total.get("buy");
                } else {
                    LOG.error("The type can be either buy or sell");
                }
                return toRet;
            } else {
                LOG.error(((JSONObject) json.get("error")).toString());
                return 0;
            }

        } else {
            LOG.error("getliquidityinfo returned null");
            return -1;
        }
    }

    /*
     public Double getBalance(String account) {
     String[] params = { account };
     JSONObject json = invokeRPC(UUID.randomUUID().toStringSep(), COMMAND_GET_BALANCE, Arrays.asList(params));
     return (Double)json.get("result");
     }

     public String getNewAddress(String account) {
     String[] params = { account };
     JSONObject json = invokeRPC(UUID.randomUUID().toStringSep(), COMMAND_GET_NEW_ADDRESS, Arrays.asList(params));
     return (String)json.get("result");
     }
     */

    public JSONObject getInfo() {
        JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_INFO, null);
        if (json != null) {
            return (JSONObject) json.get("result");
        } else {
            return new JSONObject();
        }

    }

    public void checkConnection() {
        boolean conn = false;
        JSONObject responseObject = this.getInfo();
        if (responseObject.get("blocks") != null) {
            conn = true;
        }
        boolean locked = false;
        if (responseObject.containsKey("unlocked_until")) {
            long lockedUntil = (long) responseObject.get("unlocked_until");
            if (lockedUntil == 0) {
                LOG.warn("Nu client is locked and will not be able to submit liquidity info."
                        + "\nUse walletpassphrase <yourpassphrase> 9999999 to unlock it");
            }
        }
        this.setConnected(conn);
    }

    public boolean isConnected() {
        return this.connected;
    }

    private void setConnected(boolean connected) {
        this.connected = connected;
    }

    //Private methods
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;

    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;

    }

    public String getRpcUsername() {
        return rpcUsername;
    }

    public void setRpcUsername(String rpcUsername) {
        this.rpcUsername = rpcUsername;

    }

    public String getRpcPassword() {
        return rpcPassword;
    }

    public void setRpcPassword(String rpcPassword) {
        this.rpcPassword = rpcPassword;

    }

    private JSONObject invokeRPC(String id, String method, List params) {
        DefaultHttpClient httpclient = new DefaultHttpClient();

        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("method", method);
        if (null != params) {
            JSONArray array = new JSONArray();
            array.addAll(params);
            json.put("params", params);
        }
        JSONObject responseJsonObj = null;
        try {
            httpclient.getCredentialsProvider().setCredentials(new AuthScope(this.ip, this.port),
                    new UsernamePasswordCredentials(this.rpcUsername, this.rpcPassword));
            StringEntity myEntity = new StringEntity(json.toJSONString());
            if (Global.options.verbose) {
                LOG.info("RPC : " + json.toString());
            }
            HttpPost httppost = new HttpPost("http://" + this.ip + ":" + this.port);
            httppost.setEntity(myEntity);

            if (Global.options.verbose) {
                LOG.info("RPC executing request :" + httppost.getRequestLine());
            }
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            if (Global.options.verbose) {
                LOG.info("RPC----------------------------------------");
                LOG.info("" + response.getStatusLine());

                if (entity != null) {
                    LOG.info("RPC : Response content length: " + entity.getContentLength());
                }
            }
            JSONParser parser = new JSONParser();
            String entityString = EntityUtils.toString(entity);
            LOG.debug("Entity = " + entityString);
            /* TODO In case of wrong username/pass the response would be the following .Consider parsing it.
            Entity = <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
                        "http://www.w3.org/TR/1999/REC-html401-19991224/loose.dtd">
                        <HTML>
                        <HEAD>
                        <TITLE>Error</TITLE>
                        <META HTTP-EQUIV='Content-Type' CONTENT='text/html; charset=ISO-8859-1'>
                        </HEAD>
                        <BODY><H1>401 Unauthorized.</H1></BODY>
                        </HTML>
             */
            responseJsonObj = (JSONObject) parser.parse(entityString);
        } catch (ClientProtocolException e) {
            LOG.error("Nud RPC Connection problem:" + e.toString());
            this.connected = false;
        } catch (IOException e) {
            LOG.error("Nud RPC Connection problem:" + e.toString());
            this.connected = false;
        } catch (ParseException ex) {
            LOG.error("Nud RPC Connection problem:" + ex.toString());
            this.connected = false;
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }
        return responseJsonObj;
    }

    public String generateIdentifier(int tier) {
        //tier:pair:exchange:sessionid
        //Example : 2:BTCNBT:ccedk:0.1.5|1424193501841|d5ef77

        String separator = ":";
        String identifier = tier + separator
                + pair.toString().toUpperCase() + separator
                + exchangeName + separator
                + SessionManager.sessionId;
        LOG.debug("liquidity identifier = " + identifier);
        //TODO limit identifier to 250bytes
        //TODO limit to this charset https://en.wikipedia.org/wiki/ASCII#ASCII_printable_characters
        return identifier;
    }
}
