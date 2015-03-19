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

package functions;


import com.nubits.nubot.options.NuBotConfigException;
import com.nubits.nubot.options.NuBotOptions;
import com.nubits.nubot.options.ParseOptions;
import com.nubits.nubot.utils.Utils;
import junit.framework.TestCase;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestOptionsSample extends TestCase {

    private static String testconfigFile = "sample.json";
    private static String testconfigdir = "config/sampleconfig";
    private static String testconfig = testconfigdir + "/" + testconfigFile;

    @Override
    public void setUp() {
        try{
            Utils.loadProperties("settings.properties");
        }catch(IOException e){

        }
    }

    @Test
    public void testConfigExists() {
        Path currentRelativePath = Paths.get("");
        String s = currentRelativePath.toAbsolutePath().toString();
        final String wdir = System.getProperty("user.dir");
        System.out.println("wdir: " + wdir);

        File f = new File(testconfig);
        System.out.println(">>> " + f.getAbsolutePath() + " exists : " + f.exists());
        assertTrue(f.exists());
    }

    @Test
    public void testLoadconfig() {
        try {
            JSONObject inputJSON = ParseOptions.parseSingleJsonFile(testconfig);
            System.out.println(inputJSON);
            assertTrue(inputJSON.keySet().size() > 0);
        } catch (ParseException e) {
            // e.printStackTrace();
        }
    }

    @Test
    public void testJson() {
        try {
            JSONObject inputJSON = ParseOptions.parseSingleJsonFile(testconfig);

            assertTrue(inputJSON.containsKey("exchangename"));
        } catch (ParseException e) {
            // e.printStackTrace();
        }
    }



    @Test
    public void testLoadConfig() {
        try {
            NuBotOptions nuo = ParseOptions
                    .parseOptionsSingle(testconfig);

            assertTrue(nuo != null);

            assertTrue(nuo.exchangeName.equals("peatio"));

            //assertTrue(nuo.getPair() != null);

            //assertTrue(nuo.getSecondaryPegOptions() != null);
            //.getSpread())

        } catch (NuBotConfigException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testLoadComplete() {

        String testconfigFile = "test.json";
        String testconfig = testconfigdir + "/" + testconfigFile;
        boolean catched = false;
        JSONObject inputJSON = null;
        try {
            inputJSON = ParseOptions.parseSingleJsonFile(testconfig);
            //assertTrue(inputJSON.containsKey("options"));
            //JSONObject optionsJSON = ParseOptions.getOptionsKey(inputJSON);
            assertTrue(inputJSON.containsKey("exchangename"));
        } catch (Exception e) {

        }
        assertTrue(!catched);

        assertTrue(inputJSON.containsKey("exchangename"));
        assertTrue(inputJSON.containsKey("apikey"));
        assertTrue(inputJSON.containsKey("spread"));
        assertTrue(inputJSON.containsKey("hipchat"));
        assertTrue(inputJSON.containsKey("pair"));
    }

    @Test
    public void testLoadOptionsAll(){


        boolean catched = false;
        NuBotOptions opt = null;
        try {
             opt = ParseOptions.parseOptionsSingle(testconfig);

        } catch (NuBotConfigException e) {
            System.out.println("could not parse config");
            System.out.println(e);
            catched = true;
        }

        assertTrue(!catched);

        /*String nudIp = NuBotOptionsDefault.nudIp;
        String sendMails = NuBotOptionsDefault.sendMails;
        boolean submitLiquidity = NuBotOptionsDefault.submitLiquidity;
        boolean executeOrders = NuBotOptionsDefault.executeOrders;
        boolean verbose = NuBotOptionsDefault.verbose;
        boolean sendHipchat = NuBotOptionsDefault.sendHipchat;
        boolean multipleCustodians = NuBotOptionsDefault.multipleCustodians;
        int executeStrategyInterval = NuBotOptionsDefault.executeStrategyInterval;
        double txFee = NuBotOptionsDefault.txFee;
        double priceIncrement = NuBotOptionsDefault.priceIncrement;
        double keepProceeds = NuBotOptionsDefault.keepProceeds;
        double maxSellVolume = NuBotOptionsDefault.maxSellVolume;
        double maxBuyVolume = NuBotOptionsDefault.maxBuyVolume;
        int emergencyTimeout = NuBotOptionsDefault.emergencyTimeout;
        boolean distributeLiquidity = NuBotOptionsDefault.distributeLiquidity;*/

        assertTrue(opt.getPair()!=null);
        assertTrue(opt.getExchangeName()!=null);



    }

    @Test
    public void testParseFeeds(){

        String testconfigFile = "peatio.json";
        String testconfig = testconfigdir + "/" + testconfigFile;
        boolean catched = false;
        try {
            NuBotOptions opt = ParseOptions.parseOptionsSingle(testconfig);
            assertTrue(opt.mainFeed!=null);
            assertTrue(opt.mainFeed.equals("btce"));
            System.out.println(opt.backupFeedNames.size());
            assertTrue(opt.backupFeedNames.size()==2);
            assertTrue(opt.backupFeedNames.get(0).equals("coinbase"));
            assertTrue(opt.backupFeedNames.get(1).equals("blockchain"));

        } catch (NuBotConfigException e) {
            System.out.println("could not parse config");
            System.out.println(e);
            catched = true;
        }

        assertTrue(!catched);


    }



    /*@Test
    public void testInvalidFeed(){

        //

    }*/






    // @Test
    // public void testDefaultOptions() {
    // // TODO
    // assert (false);
    // }
    //
    // @Test
    // public void testcompulsory() {
    // assert (false);
    // }
    //
    // @Test
    // public void testWrongConfig() {
    // // TODO
    // // if wrongly configured throws ParseError
    // assert (false);
    // }
}