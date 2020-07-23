/**
 * Manages Wikibase instances.
 */
const WikibaseManager = {
  selected: "Wikidata",
  wikibases: {
    "Wikidata": WikidataManifestV1_0 // default one
  }
};

WikibaseManager.getSelectedWikibase = function () {
  return WikibaseManager.wikibases[WikibaseManager.selected];
};

WikibaseManager.getSelectedWikibaseRoot = function () {
  return WikibaseManager.getSelectedWikibase().mediawiki.root;
};

WikibaseManager.getSelectedWikibaseMainPage = function () {
  return WikibaseManager.getSelectedWikibase().mediawiki.main_page;
};

WikibaseManager.getSelectedWikibaseApi = function () {
  return WikibaseManager.getSelectedWikibase().mediawiki.api;
};

WikibaseManager.getSelectedWikibaseName = function () {
  return WikibaseManager.selected;
};

WikibaseManager.getSelectedWikibaseEntityPrefix = function () {
  return WikibaseManager.getSelectedWikibase().wikibase.properties.entity_prefix;
};

WikibaseManager.getSelectedWikibaseReconEndpoint = function () {
  return WikibaseManager.getSelectedWikibase().reconciliation.endpoint;
};

WikibaseManager.selectWikibase = function (wikibaseName) {
  if (WikibaseManager.wikibases.hasOwnProperty(wikibaseName)) {
    WikibaseManager.selected = wikibaseName;
  }
};

WikibaseManager.getAllWikibases = function () {
  return WikibaseManager.wikibases;
};

WikibaseManager.addWikibase = function (manifest) {
  WikibaseManager.wikibases[manifest.mediawiki.name] = manifest;
  WikibaseManager.saveWikibases();
};

WikibaseManager.removeWikibase = function (wikibaseName) {
  delete WikibaseManager.wikibases[wikibaseName];
  WikibaseManager.saveWikibases();
};

WikibaseManager.saveWikibases = function () {
  let manifests = [];
  for (let wikibaseName in WikibaseManager.wikibases) {
    if (WikibaseManager.wikibases.hasOwnProperty(wikibaseName)) {
      manifests.push(WikibaseManager.wikibases[wikibaseName])
    }
  }

  Refine.wrapCSRF(function (token) {
    $.ajax({
      async: false,
      type: "POST",
      url: "command/core/set-preference?" + $.param({
        name: "wikibase.manifests"
      }),
      data: {
        "value": JSON.stringify(manifests),
        csrf_token: token
      },
      dataType: "json"
    });
  });
};

// TODO: test
WikibaseManager.loadWikibases = function () {
  $.ajax({
    async: true,
    url: "command/core/get-preference?" + $.param({
      name: "wikibase.manifests"
    }),
    success: function (data) {
      if (data.value && data.value !== "null" && data.value !== "[]") {
        let toUpdate = false;
        let manifests = JSON.parse(data.value);
        manifests.forEach(function (manifest) {
          if (manifest.custom && manifest.custom.url && manifest.custom.last_updated
              && ((Date.now() - manifest.custom.last_updated) > 7 * 24 * 60 * 60 * 1000)) {
            toUpdate = true;
            // If the manifest was fetched via URL and hasn't been updated for a week,
            // fetch it again to keep track of the lasted version
            WikibaseManager.fetchManifestFromURL(manifest.custom.url, function (newManifest) {
              WikibaseManager.wikibases[newManifest.mediawiki.name] = newManifest;
            }, function () {
              // fallback to the current one if failed to fetch the latest one
              WikibaseManager.wikibases[manifest.mediawiki.name] = manifest;
            }, true)
          } else {
            WikibaseManager.wikibases[manifest.mediawiki.name] = manifest;
          }
        });

        if (toUpdate) {
          // wait for 10s for fetching latest manifests to finish
          setTimeout(function () {
            WikibaseManager.saveWikibases();
          }, 10000);
        }
      }
    },
    dataType: "json"
  });
};

WikibaseManager.fetchManifestFromURL = function (manifestURL, onSuccess, onError, silent) {
  let dismissBusy = function() {};
  if (!silent) {
    dismissBusy = DialogSystem.showBusy($.i18n("wikibase-management/contact-service") + "...");
  }

  let _onSuccess = function (data) {
    // record custom information in the manifest
    data.custom = {};
    data.custom.url = manifestURL;
    data.custom.last_updated = Date.now();
    if (onSuccess) {
      onSuccess(data);
    }
  };

  // try with CORS first
  $.ajax(manifestURL, {
    "dataType": "json",
    "timeout": 5000
  }).success(function (data) {
    dismissBusy();
    _onSuccess(data);
  }).error(function () {
    // if CORS failed, then try with JSONP
    $.ajax(manifestURL, {
      "dataType": "jsonp",
      "timeout": 5000
    }).success(function () {
      dismissBusy();
      _onSuccess(data);
    }).error(function (jqXHR, textStatus, errorThrown) {
      dismissBusy();
      if (!silent) {
        alert($.i18n("wikibase-management/error-contact")+": " + textStatus + " : " + errorThrown + " - " + manifestURL);
      }
      if (onError) {
        onError();
      }
    });
  });
};

(function () {
  WikibaseManager.loadWikibases();
})();