package org.openrefine.wikidata.commands;

import com.google.refine.commands.Command;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.openrefine.wikidata.commands.ConnectionManager.WIKIDATA_CLIENT_ID_ENV_KEY;
import static org.openrefine.wikidata.commands.ConnectionManager.WIKIDATA_CLIENT_SECRET_ENV_KEY;

/**
 * Command for checking if OpenRefine is run locally or in public.
 *
 * If OAuth client id/secret are configured, it's assumed that OpenRefine is run in public,
 * otherwise locally.
 */
public class ModeCommand extends Command {

    private static boolean isLocalMode;

    static {
        String clientId = System.getProperty(WIKIDATA_CLIENT_ID_ENV_KEY);
        String clientSecret = System.getProperty(WIKIDATA_CLIENT_SECRET_ENV_KEY);
        isLocalMode = clientId == null || clientSecret == null;
    }

    public static boolean isLocalMode() {
        return isLocalMode;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("mode", isLocalMode ? "local" : "public");
        respondJSON(response, jsonResponse);
    }
}
