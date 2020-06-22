package org.openrefine.wikidata.commands;

import com.google.refine.commands.Command;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Command for redirecting the user to the authorization page.
 */
public class AuthorizeCommand extends Command {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            if (!ModeCommand.isLocalMode()) {
                String authorizationUrl = ConnectionManager.getInstance().getAuthorizationUrl(request);
                response.sendRedirect(authorizationUrl);
            } else {
                throw new IllegalStateException("You must configure Wikidata OAuth client id/secret in refine.ini " +
                        "in order to use OAuth to login");
            }
        } catch (Exception e) {
            respondWithErrorPage(request, response, e.getMessage(), e);
        }
    }
}
