package org.openrefine.wikidata.commands;

import com.google.refine.commands.Command;
import org.wikidata.wdtk.wikibaseapi.OAuthApiConnection;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.ExecutionException;

import static org.openrefine.wikidata.commands.ConnectionManager.*;

/**
 * Command for OAuth callback.
 */
public class AuthorizedCommand extends Command {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String oauthVerifier = request.getParameter("oauth_verifier");
            OAuthApiConnection connection = ConnectionManager.getInstance().getOAuthApiConnection(request, oauthVerifier);
            // keep the connection in session
            request.getSession().setAttribute(CONNECTION_KEY, connection);

            // set cookies
            addCookie(response, ACCESS_TOKEN, connection.getAccessToken());
            addCookie(response, ACCESS_SECRET, connection.getAccessSecret());
        } catch (InterruptedException | ExecutionException | MediaWikiApiErrorException e) {
            e.printStackTrace();
        }

        // close current page
        // The frontend is able to tell whether the current page is closed or not,
        // when it's closed, a callback will be triggered.
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html");
        Writer writer = response.getWriter();
        writer.write("<script>window.close()</script>");
        writer.close();
    }
}
