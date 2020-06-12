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
import org.openrefine.wikidata.editing.ConnectionManager;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Handles login.
 * <p>
 * Both logging in with username/password or owner-only consumer are supported.
 * <p>
 * This command also manages cookies of login credentials.
 */
public class LoginCommand extends Command {

    static final String USERNAME = "wb-username";
    static final String PASSWORD = "wb-password";

    static final String CONSUMER_TOKEN = "wb-consumer-token";
    static final String CONSUMER_SECRET = "wb-consumer-secret";
    static final String ACCESS_TOKEN = "wb-access-token";
    static final String ACCESS_SECRET = "wb-access-secret";

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!hasValidCSRFToken(request)) {
            respondCSRFError(response);
            return;
        }

        ConnectionManager manager = ConnectionManager.getInstance();

        if ("true".equals(request.getParameter("logout"))) {
            manager.logout();
            removeUsernamePasswordCookies(response);
            removeOwnOnlyConsumerCookies(response);
            respond(request, response);
            return; // return directly
        }

        boolean remember = "on".equals(request.getParameter("remember-credentials"));

        Cookie[] cookies = request.getCookies();

        // Credentials from parameters have higher priority than those from cookies.
        String username = request.getParameter(USERNAME);
        String password = request.getParameter(PASSWORD);

        if (isBlank(username) || isBlank(password)) {
            for (Cookie cookie : cookies) {
                String value = cookie.getValue();
                switch (cookie.getName()) {
                    case USERNAME:
                        username = value;
                        break;
                    case PASSWORD:
                        password = value;
                        break;
                    default:
                        break;
                }
            }
            if (isNotBlank(username) && isNotBlank(password)) {
                // If the credentials are read from cookies, we must remember it.
                remember = true;
            }
        }

        String consumerToken = request.getParameter(CONSUMER_TOKEN);
        String consumerSecret = request.getParameter(CONSUMER_SECRET);
        String accessToken = request.getParameter(ACCESS_TOKEN);
        String accessSecret = request.getParameter(ACCESS_SECRET);

        if (isBlank(consumerToken)|| isBlank(consumerSecret) || isBlank(accessToken) || isBlank(accessSecret)) {
            for (Cookie cookie : cookies) {
                String value = cookie.getValue();
                switch (cookie.getName()) {
                    case CONSUMER_TOKEN:
                        consumerToken = value;
                        break;
                    case CONSUMER_SECRET:
                        consumerSecret = value;
                        break;
                    case ACCESS_TOKEN:
                        accessToken = value;
                        break;
                    case ACCESS_SECRET:
                        accessSecret = value;
                        break;
                    default:
                        break;
                }
            }
            if (isNotBlank(consumerToken) && isNotBlank(consumerSecret) && isNotBlank(accessToken) && isNotBlank(accessSecret)) {
                remember = true;
            }
        }

        if (isNotBlank(username) && isNotBlank(password)) {
            manager.login(username, password);
            // Once logged in with new credentials,
            // the old credentials in cookies should be cleared.
            if (manager.getConnection() != null && remember) {
                setCookie(response, USERNAME, username);
                setCookie(response, PASSWORD, password);
            } else {
                removeUsernamePasswordCookies(response);
            }
            removeOwnOnlyConsumerCookies(response);
        } else if (isNotBlank(consumerToken) && isNotBlank(consumerSecret) && isNotBlank(accessToken) && isNotBlank(accessSecret)) {
            manager.login(consumerToken, consumerSecret, accessToken, accessSecret);
            if (manager.getConnection() != null && remember) {
                setCookie(response, CONSUMER_TOKEN, consumerToken);
                setCookie(response, CONSUMER_SECRET, consumerSecret);
                setCookie(response, ACCESS_TOKEN, accessToken);
                setCookie(response, ACCESS_SECRET, accessSecret);
            } else {
                removeOwnOnlyConsumerCookies(response);
            }
            removeUsernamePasswordCookies(response);
        }

        respond(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        respond(request, response);
    }

    protected void respond(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ConnectionManager manager = ConnectionManager.getInstance();
        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("logged_in", manager.isLoggedIn());
        jsonResponse.put("username", manager.getUsername());
        respondJSON(response, jsonResponse);
    }

    private static void removeUsernamePasswordCookies(HttpServletResponse response) {
        removeCookie(response, USERNAME);
        removeCookie(response, PASSWORD);
    }

    private static void removeOwnOnlyConsumerCookies(HttpServletResponse response) {
        removeCookie(response, CONSUMER_TOKEN);
        removeCookie(response, CONSUMER_SECRET);
        removeCookie(response, ACCESS_TOKEN);
        removeCookie(response, ACCESS_SECRET);
    }

    private static void setCookie(HttpServletResponse response, String key, String value) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(60 * 60 * 24 * 365); // a year
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    private static void removeCookie(HttpServletResponse response, String key) {
        Cookie cookie = new Cookie(key, "");
        cookie.setMaxAge(0); // 0 causes the cookie to be deleted
        cookie.setPath("/");
        response.addCookie(cookie);
    }
}
