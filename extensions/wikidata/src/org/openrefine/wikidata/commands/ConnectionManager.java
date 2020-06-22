package org.openrefine.wikidata.commands;

import com.github.scribejava.apis.MediaWikiApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.oauth.OAuth10aService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.wikibaseapi.ApiConnection;
import org.wikidata.wdtk.wikibaseapi.BasicApiConnection;
import org.wikidata.wdtk.wikibaseapi.LoginFailedException;
import org.wikidata.wdtk.wikibaseapi.OAuthApiConnection;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public final class ConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    static final String CONNECTION_KEY = "wb-connection";

    static final String USERNAME = "wb-username";
    static final String PASSWORD = "wb-password";

    static final String CLIENT_ID = "wb-client-id";
    static final String CLIENT_SECRET = "wb-client-secret";
    static final String ACCESS_TOKEN = "wb-access-token";
    static final String ACCESS_SECRET = "wb-access-secret";

    static final String WIKIDATA_CLIENT_SECRET_ENV_KEY = "ext.wikidata.clientsecret";
    static final String WIKIDATA_CLIENT_ID_ENV_KEY = "ext.wikidata.clientid";

    private static final String ACCESS_TOKEN_SESSION_KEY = "wb-request-token-secret";

    private final OAuth10aService mediaWikiService;

    private static final ConnectionManager instance = new ConnectionManager();

    public static ConnectionManager getInstance() {
        return instance;
    }

    private ConnectionManager() {
        // We don't specify the callback URL here.
        // The user must specify it when registering the consumer.
        // So OpenRefine doesn't need to know its public hostname.
        String clientId = System.getProperty(WIKIDATA_CLIENT_ID_ENV_KEY);
        String clientSecret = System.getProperty(WIKIDATA_CLIENT_SECRET_ENV_KEY);
        if (clientId == null || clientSecret == null) {
            mediaWikiService = null;
        } else {
            mediaWikiService = new ServiceBuilder(clientId)
                    .apiSecret(clientSecret)
                    .build(MediaWikiApi.instance());
        }
    }

    /**
     * Fetches a request token first, then use the token to generate to authorization url.
     */
    public String getAuthorizationUrl(HttpServletRequest request) throws InterruptedException, ExecutionException, IOException {
        OAuth1RequestToken requestToken = mediaWikiService.getRequestToken();
        request.getSession().setAttribute(ACCESS_TOKEN_SESSION_KEY, requestToken);
        return mediaWikiService.getAuthorizationUrl(requestToken);
    }

    /**
     * Looks up the corresponding {@link OAuth1RequestToken} instance with the oauthToken first.
     * Then uses the oauthToken and oauthVerifier to fetch the access token/secret.
     * Finally create an {@link OAuthApiConnection} instance with the consumer id/secret and access token/secret.
     *
     * @return an {@link OAuthApiConnection} instance if the OAuth credentials are valid, or null otherwise.
     */
    public OAuthApiConnection getOAuthApiConnection(HttpServletRequest request, String oauthVerifier) throws InterruptedException, ExecutionException, IOException, MediaWikiApiErrorException {
        OAuth1RequestToken requestToken = (OAuth1RequestToken) request.getSession().getAttribute(ACCESS_TOKEN_SESSION_KEY);
        OAuth1AccessToken accessToken = mediaWikiService.getAccessToken(requestToken, oauthVerifier);
        return getOAuthApiConnection(accessToken.getToken(), accessToken.getTokenSecret());

    }

    public OAuthApiConnection getOAuthApiConnection(String accessToken, String accessSecret) throws IOException, MediaWikiApiErrorException {
        return getOAuthApiConnection(mediaWikiService.getApiKey(), mediaWikiService.getApiSecret(),
                accessToken, accessSecret);
    }

    /**
     * Creates an {@link OAuthApiConnection} instance with the given owner-only credentials.
     */
    public OAuthApiConnection getOAuthApiConnection(String clientId, String clientSecret,
                                                    String accessToken, String accessSecret) throws IOException, MediaWikiApiErrorException {
        OAuthApiConnection connection = new OAuthApiConnection(ApiConnection.URL_WIKIDATA_API,
                clientId, clientSecret, accessToken, accessSecret);
        setupConnection(connection);
        // checks if the OAuth credentials are valid
        connection.checkCredentials();
        return connection;
    }

    public BasicApiConnection getBasicApiConnection() {
        return BasicApiConnection.getWikidataApiConnection();
    }

    /**
     * Creates an {@link BasicApiConnection} instance and then login with the given username and password.
     */
    public BasicApiConnection getBasicApiConnection(String username, String password) throws LoginFailedException {
        BasicApiConnection connection = BasicApiConnection.getWikidataApiConnection();
        connection.login(username, password);
        return connection;
    }

    public void setupConnection(ApiConnection connection) {
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
    }

    public static void addCookie(HttpServletResponse response, String cookieKey, String cookieValue) {
        Cookie cookie = new Cookie(cookieKey, cookieValue);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365); // a year
        response.addCookie(cookie);
    }

    public static void removeCookie(HttpServletResponse response, String cookieKey) {
        Cookie cookie = new Cookie(cookieKey, "");
        cookie.setPath("/");
        cookie.setMaxAge(0); // remove immediately
        response.addCookie(cookie);
    }
}
