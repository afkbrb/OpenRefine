var ManageAccountDialog = {};

ManageAccountDialog.firstLogin = true;

ManageAccountDialog.display = function (logged_in_username, callback) {
  if (logged_in_username == null) {
    logged_in_username = ManageAccountDialog.tryLoginWithCookie(callback);
  }

  if (logged_in_username != null) {
    ManageAccountDialog.displayLoggedIn(logged_in_username, callback);
  } else {
    // check which mode OpenRefine is running at
    if (ManageAccountDialog.getRunningMode() === "local") {
      ManageAccountDialog.displayPassword(logged_in_username, callback);
    } else {
      ManageAccountDialog.displayOAuth(logged_in_username, callback);
    }
  }
};

ManageAccountDialog.tryLoginWithCookie = function (callback) {
  let logged_in_username = null;
  let request = null;
  const isLocalMode = ManageAccountDialog.getRunningMode() === "local";

  const username = ManageAccountDialog.getCookie("wb-username");
  const password = ManageAccountDialog.getCookie("wb-password");
  const clientId = ManageAccountDialog.getCookie("wb-client-id");
  const clientSecret = ManageAccountDialog.getCookie("wb-client-secret");
  const accessToken = ManageAccountDialog.getCookie("wb-access-token");
  const accessSecret = ManageAccountDialog.getCookie("wb-access-secret");
  if (isLocalMode) {
    if (username != null && password != null) {
      request = "wb-username=" + encodeURIComponent(username) + "&wb-password=" + encodeURIComponent(password) +
          "&remember-credentials=on";
    } else {
      if (clientId != null && clientSecret != null && accessToken != null && accessSecret != null) {
        request = "wb-client-id=" + encodeURIComponent(clientId) + "&wb-client-secret=" + encodeURIComponent(clientSecret) +
            "&wb-access-token=" + encodeURIComponent(accessToken) + "&wb-access-secret=" + encodeURIComponent(accessSecret) +
            "&remember-credentials=on";
      }
    }
  } else {
    if (accessToken != null && accessSecret != null) {
      request = "wb-access-token=" + encodeURIComponent(accessToken) + "&wb-access-secret=" + encodeURIComponent(accessSecret);
    }
  }

  if (request != null) {
    $.ajaxSetup({async: false});
    Refine.postCSRF(
        "command/wikidata/login",
        request,
        function (data) {
          if (data.logged_in) {
            callback(data.username);
            logged_in_username = data.username;
          }
        });
    $.ajaxSetup({async: true});
  } else {
    logged_in_username = null;
  }

  return logged_in_username;
};

ManageAccountDialog.initCommon = function (elmts) {
  elmts.dialogHeader.text($.i18n('wikidata-account/dialog-header'));
  elmts.explainLogIn.html($.i18n('wikidata-account/explain-log-in'));
  elmts.dialogHeader.text($.i18n('wikidata-account/dialog-header'));
  elmts.cancelButton.text($.i18n('wikidata-account/close'));
};

ManageAccountDialog.displayLoggedIn = function (logged_in_username, callback) {
  var frame = $(DOM.loadHTML("wikidata", "scripts/dialogs/logged-in-dialog.html"));
  var elmts = DOM.bind(frame);
  ManageAccountDialog.initCommon(elmts);
  elmts.loggedInAs.text($.i18n('wikidata-account/logged-in-as'));
  elmts.logoutButton.text($.i18n('wikidata-account/log-out'));

  var level = DialogSystem.showDialog(frame);
  var dismiss = function () {
    DialogSystem.dismissUntil(level - 1);
  };

  elmts.loggedInUsername
      .text(logged_in_username)
      .attr('href', 'https://www.wikidata.org/wiki/User:' + logged_in_username);

  elmts.cancelButton.click(function (e) {
    dismiss();
    callback(null);
  });

  elmts.logoutButton.click(function () {
    frame.hide();
    Refine.postCSRF(
        "command/wikidata/login",
        "logout=true",
        function (data) {
          frame.show();
          if (!data.logged_in) {
            dismiss();
            callback(null);
          }
        });
  });
};

ManageAccountDialog.displayOAuth = function (logged_in_username, callback) {
  var frame = $(DOM.loadHTML("wikidata", "scripts/dialogs/oauth-login-dialog.html"));
  var elmts = DOM.bind(frame);
  ManageAccountDialog.initCommon(elmts);
  elmts.loginButton.val($.i18n('wikidata-account/log-in'));

  var level = DialogSystem.showDialog(frame);
  var dismiss = function () {
    DialogSystem.dismissUntil(level - 1);
  };

  elmts.cancelButton.click(function (e) {
    dismiss();
    callback(null);
  });

  elmts.loginForm.submit(function (e) {
    frame.hide();
    ManageAccountDialog.oauth(
        "command/wikidata/authorize",
        function () {
          $.get(
              "command/wikidata/login",
              function (data) {
                if (data.logged_in) {
                  dismiss();
                  callback(data.username);
                } else {
                  frame.show();
                  elmts.invalidCredentials.text("Invalid credentials.");
                }
              });
        });
    e.preventDefault();
  });
};

ManageAccountDialog.displayPassword = function (logged_in_username, callback) {
  const frame = $(DOM.loadHTML("wikidata", "scripts/dialogs/password-login-dialog.html"));
  const elmts = DOM.bind(frame);

  ManageAccountDialog.initCommon(elmts);
  elmts.youCanAlso.text($.i18n('wikidata-account/you-can-also'));
  elmts.ownerOnlyConsumerLogin.text($.i18n('wikidata-account/owner-only-consumer-login'));
  elmts.usernameLabel.text($.i18n('wikidata-account/username-label'));
  elmts.usernameInput.attr("placeholder", $.i18n('wikidata-account/username-placeholder'));
  elmts.passwordLabel.text($.i18n('wikidata-account/password-label'));
  elmts.passwordInput.attr("placeholder", $.i18n('wikidata-account/password-placeholder'));
  elmts.rememberCredentialsLabel.text($.i18n('wikidata-account/remember-credentials-label'));
  elmts.usernameInput.focus();

  var level = DialogSystem.showDialog(frame);
  var dismiss = function () {
    DialogSystem.dismissUntil(level - 1);
  };
  elmts.cancelButton.click(function (e) {
    dismiss();
    callback(null);
  });

  elmts.ownerOnlyConsumerLogin.click(function (e) {
    dismiss();
    ManageAccountDialog.displayOwnerOnlyConsumer(logged_in_username, callback);
  });

  elmts.loginForm.submit(function (e) {
    frame.hide();
    Refine.postCSRF(
        "command/wikidata/login",
        elmts.loginForm.serialize(),
        function (data) {
          if (data.logged_in) {
            dismiss();
            callback(data.username);
          } else {
            frame.show();
            elmts.invalidCredentials.text("Invalid credentials.");
          }
        });
    e.preventDefault();
  });
};

ManageAccountDialog.displayOwnerOnlyConsumer = function (logged_in_username, callback) {
  var frame = $(DOM.loadHTML("wikidata", "scripts/dialogs/owner-only-consumer-login-dialog.html"));
  var elmts = DOM.bind(frame);
  ManageAccountDialog.initCommon(elmts);
  elmts.youCanAlso.text($.i18n('wikidata-account/you-can-also'));
  elmts.passwordLogin.text($.i18n('wikidata-account/password-login'));
  elmts.clientIdLabel.text($.i18n('wikidata-account/client-id-label'));
  elmts.clientIdInput.attr("placeholder", $.i18n('wikidata-account/client-id-placeholder'));
  elmts.clientSecretLabel.text($.i18n('wikidata-account/client-secret-label'));
  elmts.clientSecretInput.attr("placeholder", $.i18n('wikidata-account/client-secret-placeholder'));
  elmts.accessTokenLabel.text($.i18n('wikidata-account/access-token-label'));
  elmts.accessTokenInput.attr("placeholder", $.i18n('wikidata-account/access-token-placeholder'));
  elmts.accessSecretLabel.text($.i18n('wikidata-account/access-secret-label'));
  elmts.accessSecretInput.attr("placeholder", $.i18n('wikidata-account/access-secret-placeholder'));
  elmts.rememberCredentialsLabel.text($.i18n('wikidata-account/remember-credentials-label'));
  elmts.clientIdInput.focus();

  var level = DialogSystem.showDialog(frame);
  var dismiss = function () {
    DialogSystem.dismissUntil(level - 1);
  };

  elmts.cancelButton.click(function (e) {
    dismiss();
    callback(null);
  });

  elmts.passwordLogin.click(function (e) {
    dismiss();
    ManageAccountDialog.displayPassword(logged_in_username, callback);
  });

  elmts.loginForm.submit(function (e) {
    frame.hide();
    Refine.postCSRF(
        "command/wikidata/login",
        elmts.loginForm.serialize(),
        function (data) {
          if (data.logged_in) {
            dismiss();
            callback(data.username);
          } else {
            frame.show();
            elmts.invalidCredentials.text("Invalid credentials.");
          }
        });
    e.preventDefault();
  });
};

ManageAccountDialog.getCookie = function (cookieName) {
  var name = cookieName + "=";
  var cookies = document.cookie.split(';');
  for (var i = 0; i < cookies.length; i++) {
    var cookie = cookies[i];
    while (cookie.charAt(0) === ' ') {
      cookie = cookie.substring(1);
    }
    if (cookie.indexOf(name) === 0) {
      var result = cookie.substring(name.length, cookie.length);
      // trim double quotes
      if (result.length >= 2 && result.charAt(0) === '"' && result.charAt(result.length - 1) === '"') {
        result = result.substring(1, result.length - 1);
      }
      return result;
    }
  }
  return null;
};

ManageAccountDialog.getRunningMode = function () {
  var running_mode = false;
  // must sync
  $.ajaxSetup({async: false});
  $.get(
      "command/wikidata/mode",
      function (data) {
        running_mode = data.mode;
      }
  );
  $.ajaxSetup({async: true});
  return running_mode;
};

ManageAccountDialog.oauth = function (path, callback) {
  var self = this;
  self.oauthWindow = window.open(path);
  self.oauthInterval = window.setInterval(function () {
    if (self.oauthWindow.closed) {
      window.clearInterval(self.oauthInterval);
      callback();
    }
  }, 1000);
};

ManageAccountDialog.isLoggedIn = function (callback) {
  var discardWaiter = function () {
  };
  if (ManageAccountDialog.firstLogin) {
    discardWaiter = DialogSystem.showBusy($.i18n('wikidata-account/connecting-to-wikidata'));
  }
  $.get(
      "command/wikidata/login",
      function (data) {
        discardWaiter();
        ManageAccountDialog.firstLogin = false;
        callback(data.username);
      });
};

ManageAccountDialog.ensureLoggedIn = function (callback) {
  ManageAccountDialog.isLoggedIn(function (logged_in_username) {
    if (logged_in_username == null) {
      ManageAccountDialog.display(null, callback);
    } else {
      callback(logged_in_username);
    }
  });
};

ManageAccountDialog.checkAndLaunch = function () {
  ManageAccountDialog.isLoggedIn(function (logged_in_username) {
    ManageAccountDialog.display(logged_in_username, function (success) {
    });
  });
};
