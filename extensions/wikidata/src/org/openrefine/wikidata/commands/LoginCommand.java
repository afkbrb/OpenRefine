/*******************************************************************************
 * MIT License
 *
 * Copyright (c) 2018 Antonin Delpeuch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package org.openrefine.wikidata.commands;

import com.google.refine.commands.Command;
import org.wikidata.wdtk.wikibaseapi.ApiConnection;
import org.wikidata.wdtk.wikibaseapi.LoginFailedException;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.openrefine.wikidata.commands.ConnectionManager.*;

public class LoginCommand extends Command {

    /**
     * Logs in with username/password or owner-only consumer credentials.
     * <p>
     * Can also be used to logout.
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!hasValidCSRFToken(request)) {
            respondCSRFError(response);
            return;
        }

        // login with username/password
        String username = request.getParameter(USERNAME);
        String password = request.getParameter(PASSWORD);

        // login with owner-only consumer credentials
        String clientId = request.getParameter(CLIENT_ID);
        String clientSecret = request.getParameter(CLIENT_SECRET);
        String accessToken = request.getParameter(ACCESS_TOKEN);
        String accessSecret = request.getParameter(ACCESS_SECRET);

        // If the user chooses to remember credentials, credentials will be stored in cookies.
        // We don't read the cookie here, the frontend is responsible for reading the credentials
        // from cookies (if the credentials cookies are set) and set them as request parameters.
        boolean remember = "on".equals(request.getParameter("remember-credentials"));

        ConnectionManager manager = ConnectionManager.getInstance();
        ApiConnection connection = null;
        HttpSession session = request.getSession();
        boolean triedLogin = false;
        try {
            if (username != null && password != null) {
                triedLogin = true;
                // login with username/password
                connection = manager.getBasicApiConnection(username, password);
                session.setAttribute(CONNECTION_KEY, connection);
                if (remember) {
                    addCookie(response, USERNAME, username);
                    addCookie(response, PASSWORD, password);
                } else {
                    removeUsernamePasswordCookies(response);
                }
                removeOwnerOnlyConsumerCookies(response);
            } else if (clientId != null && clientSecret != null && accessToken != null && accessSecret != null) {
                triedLogin = true;
                // login with owner-only consumer credentials
                connection = manager.getOAuthApiConnection(clientId, clientSecret, accessToken, accessSecret);
                session.setAttribute(CONNECTION_KEY, connection);
                if (remember) {
                    addCookie(response, CLIENT_ID, clientId);
                    addCookie(response, CLIENT_SECRET, clientSecret);
                    addCookie(response, ACCESS_TOKEN, accessToken);
                    addCookie(response, ACCESS_SECRET, accessSecret);
                } else {
                    removeOwnerOnlyConsumerCookies(response);
                }
                removeUsernamePasswordCookies(response);
            } else if (!ModeCommand.isLocalMode() && accessToken != null && accessSecret != null) {
                triedLogin = true;
                // public mode, login with access token/secret
                connection = manager.getOAuthApiConnection(accessToken, accessSecret);
                session.setAttribute(CONNECTION_KEY, connection);

                // in public mode, access token/secret are always stored in cookies
                addCookie(response, ACCESS_TOKEN, accessToken);
                addCookie(response, ACCESS_SECRET, accessSecret);

                removeUsernamePasswordCookies(response);
                removeCookie(response, CLIENT_ID);
                removeCookie(response, CLIENT_SECRET);
            } else if ("true".equals(request.getParameter("logout"))) {
                connection = (ApiConnection) session.getAttribute(CONNECTION_KEY);
                if (connection != null) {
                    connection.logout();
                }
                session.removeAttribute(CONNECTION_KEY);
                removeUsernamePasswordCookies(response);
                removeOwnerOnlyConsumerCookies(response);
            }
        } catch (MediaWikiApiErrorException | LoginFailedException e) {
            System.out.println("err msg: " + e.getMessage());
            e.printStackTrace();
            if (triedLogin) {
                // If the credentials are invalid, clear the cookies,
                // in case the credentials are sent again.
                removeUsernamePasswordCookies(response);
                removeOwnerOnlyConsumerCookies(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
            connection = null;
        }

        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("logged_in", connection != null && connection.isLoggedIn());
        jsonResponse.put("username", connection == null ? null : connection.getCurrentUser());
        respondJSON(response, jsonResponse);
    }

    /**
     * Returns the login status and username of the current connection (if exists).
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession();
        ApiConnection connection = (ApiConnection) session.getAttribute(CONNECTION_KEY);
        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("logged_in", connection != null && connection.isLoggedIn());
        jsonResponse.put("username", connection == null ? null : connection.getCurrentUser());
        respondJSON(response, jsonResponse);
    }

    public static void removeUsernamePasswordCookies(HttpServletResponse response) {
        removeCookie(response, USERNAME);
        removeCookie(response, PASSWORD);
    }

    public static void removeOwnerOnlyConsumerCookies(HttpServletResponse response) {
        removeCookie(response, CLIENT_ID);
        removeCookie(response, CLIENT_SECRET);
        removeCookie(response, ACCESS_TOKEN);
        removeCookie(response, ACCESS_SECRET);
    }

}
