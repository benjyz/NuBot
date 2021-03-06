package com.nubits.nubot.webui;


import com.google.gson.Gson;
import com.nubits.nubot.bot.Global;
import com.nubits.nubot.bot.SessionManager;
import com.nubits.nubot.global.Settings;
import com.nubits.nubot.options.NuBotOptions;
import com.nubits.nubot.options.ParseOptions;
import com.nubits.nubot.options.SaveOptions;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

/**
 * controller for changing Configurations.
 * GET from predefined file (which is the same as global.options)
 * POST user options. is loaded to global.options also
 */
public class ConfigController {

    //default config folder
    private String configDir = Settings.CONFIG_DIR;
    private String configfile;

    final static Logger LOG = LoggerFactory.getLogger(ConfigController.class);

    private void saveConfig(NuBotOptions newopt, String saveTo) {

        try {
            SaveOptions.backupOptions(this.configDir + File.separator + this.configfile);
        } catch (IOException e) {
            LOG.info("error with backup " + e);
        }


        String js = SaveOptions.jsonPretty(newopt);

        boolean savesuccess = true;
        try {
            SaveOptions.saveOptionsPretty(newopt, saveTo);
        } catch (Exception e) {
            LOG.info("error saving " + e);
            savesuccess = false;
        }

        if (savesuccess)
            Global.options = newopt;

    }

    public ConfigController(String configfile) {

        this.configfile = configfile;

        post("/configreset", (request, response) -> {
            LOG.debug("/configreset called");

            Global.currentOptionsFile = Settings.DEFAULT_CONFIG_FILE_PATH;
            boolean result = SaveOptions.optionsReset(Settings.DEFAULT_CONFIG_FILE_PATH);
            //return jsonString;

            Map opmap = new HashMap();
            opmap.put("success", result);
            String json = new Gson().toJson(opmap);
            return json;
        });

        get("/configfile", "application/json", (request, response) -> {
            LOG.debug("/configfile called");

            Map opmap = new HashMap();
            opmap.put("configfile", Global.currentOptionsFile);
            String json = new Gson().toJson(opmap);
            return json;
        });

        get("/config", "application/json", (request, response) -> {
            LOG.debug("GET /config called");

            //get from memory. any change in the file is reflected in the global options
            String jsonString = NuBotOptions.optionsToJson(Global.options);
            return jsonString;
        });

        post("/config", "application/json", (request, response) -> {
            //check if bot is running
            LOG.debug("POST /config called");

            boolean active = SessionManager.isSessionRunning();
            LOG.trace("session currently active " + active);

            if (active) {
                //if bot is running show an error
                Map opmap = new HashMap();
                opmap.put("success", false);
                opmap.put("error", "Session running: can't save config");
                String json = new Gson().toJson(opmap);
                return json;
            }

            LOG.info("config received post" + request);
            String json_body = request.body();

            JSONParser parser = new JSONParser();
            JSONObject postJson = null;
            try {
                postJson = (JSONObject) (parser.parse(json_body));
            } catch (ParseException e) {

            }

            boolean success = true;

            NuBotOptions newopt = null;
            Map opmap = new HashMap();
            String error = "none";
            try {
                newopt = ParseOptions.parsePost(postJson, false);
            } catch (Exception e) {
                LOG.error("error parsing " + postJson + "\n" + e);
                //handle errors
                success = false;
                error = "error parsing options. " + e;
            }

            opmap.put("success", success);

            if (success) {
                String saveTo = Global.currentOptionsFile;
                saveConfig(newopt, saveTo);
            }

            opmap.put("error", error);

            String json = new Gson().toJson(opmap);

            return json;

        });

    }
}
