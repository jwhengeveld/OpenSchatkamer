// Vindt de video- of audiostream op de Schatkamer-pagina en toont een klein
// paneel met een downloadknop. Detectie gebeurt via:
//   1. al geladen netwerkverzoeken (performance entries)
//   2. het video-element (currentSrc)
//   3. de HTML van de pagina (dezelfde RSC-payload als de Android-app)
//
// De overlay wordt met DOM-API's opgebouwd, niet met innerHTML.

(function () {
  "use strict";

  var URL_CHARS = "[^\"'\\s<>\\\\:,;\\]\\[}{()]+";
  var M3U8_RE = new RegExp("https://sk-(?:video|audio)\\.cdn\\.beeldengeluid\\.nl/" + URL_CHARS + "\\.m3u8" + URL_CHARS);

  var state = {
    masterUrl: null,
    kind: null,
    title: "",
    drmState: null,
    detected: false,
    running: false,
    lastPage: location.href
  };

  var el = {};
  var pollTimer = null;
  var scanTimer = null;
  var pageTimer = null;

  // ---------- hulpfuncties ----------

  function pageHtml() {
    try {
      return document.documentElement.innerHTML
        .replace(/\\u0026/g, "&")
        .replace(/\\\//g, "/");
    } catch (e) {
      return "";
    }
  }

  function currentTitle() {
    return (document.title || "")
      .replace(/\s*[|\u2013\u2014-]\s*.*Schatkamer.*$/i, "")
      .replace(/\s*[|\u2013\u2014-]\s*Beeld\s*&\s*Geluid.*$/i, "")
      .trim();
  }

  function classifyUrl(url) {
    if (url.indexOf("sk-audio.") !== -1) return "audio";
    return "video";
  }

  function cleanMediaUrl(rawUrl) {
    var url = String(rawUrl || "")
      .replace(/&amp;/g, "&")
      .replace(/\\u0026/gi, "&")
      .replace(/\\\//g, "/")
      .replace(/(?:%22|%5C|["'\\])+$/, "");

    // De RSC-payload zet de id van het volgende chunkje direct achter de URL
    // (bijv. "...Key-Pair-Id=K1C5L1HDJC2G68" + "22"). Daardoor klopt de
    // signed-URL-key niet meer; kap hem af op de CloudFront-key.
    var cf = url.match(/[?&]CloudFront-Key-Pair-Id=([A-Za-z0-9]+)/);
    var native = url.match(/([?&]Key-Pair-Id=)([A-Za-z0-9]+)$/);
    if (cf && native && native[2].indexOf(cf[1]) === 0 && native[2].length > cf[1].length) {
      url = url.slice(0, native.index + native[1].length) + cf[1];
    }
    return url;
  }

  function drmStateFromPage() {
    var html = pageHtml();
    var m = html.match(/\\?"drmState\\?":\s*\\?"([A-Z_]+)/);
    return m ? m[1] : null;
  }

  function pageKind() {
    var m = pageHtml().match(/\\?"mediaType\\?":\s*\\?"(audio|video)\\?"/);
    return m ? m[1] : null;
  }

  function scanPerformance() {
    try {
      var entries = performance.getEntriesByType("resource");
      var masters = [];
      var others = [];
      for (var i = 0; i < entries.length; i++) {
        var name = entries[i].name;
        if (!name || name.indexOf(".m3u8") === -1) continue;
        if (name.indexOf("sk-video.cdn.beeldengeluid.nl") === -1 &&
            name.indexOf("sk-audio.cdn.beeldengeluid.nl") === -1) continue;
        if (name.indexOf("master.m3u8") !== -1) masters.push(name);
        else others.push(name);
      }
      var best = masters[0] || others[0] || null;
      return best ? cleanMediaUrl(best) : null;
    } catch (e) {
      return null;
    }
  }

  function scanDom() {
    var vids = document.querySelectorAll("video, audio");
    for (var i = 0; i < vids.length; i++) {
      var src = vids[i].currentSrc || vids[i].src || "";
      if (src && src.indexOf(".m3u8") !== -1) return cleanMediaUrl(src);
    }
    return null;
  }

  function scanHtml() {
    var html = pageHtml();
    if (!html) return null;
    var re = new RegExp(M3U8_RE.source, "g");
    var match;
    var fallback = null;
    while ((match = re.exec(html)) !== null) {
      if (match[0].indexOf("master.m3u8") !== -1) return cleanMediaUrl(match[0]);
      if (!fallback) fallback = match[0];
    }
    return fallback ? cleanMediaUrl(fallback) : null;
  }

  function rescan() {
    state.title = currentTitle();
    state.drmState = drmStateFromPage();

    var found = scanPerformance() || scanDom() || scanHtml();
    if (found) {
      state.masterUrl = found;
      state.kind = classifyUrl(found) || pageKind() || "video";
      state.detected = true;
    } else {
      state.detected = false;
      state.masterUrl = null;
      state.kind = pageKind();
    }

    updateUi();
  }

  function shorten(url) {
    try {
      var p = new URL(url);
      var last = p.pathname.split("/").filter(Boolean).pop() || "";
      return p.hostname.replace(".cdn.beeldengeluid.nl", "") + " \u203a " + last;
    } catch (e) {
      return url.length > 60 ? url.slice(0, 57) + "\u2026" : url;
    }
  }

  // ---------- UI ----------

  function mk(tag, cls, textContent) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (textContent != null) n.textContent = textContent;
    return n;
  }

  function build() {
    if (document.getElementById("sk-dl-overlay")) return;

    var root = mk("div");
    root.id = "sk-dl-overlay";

    var card = mk("div", "sk-dl-card");

    var head = mk("div", "sk-dl-head");
    el.title = mk("div", "sk-dl-title", "Schatkamer");
    el.badge = mk("span", "sk-dl-badge", "media");
    head.appendChild(el.title);
    head.appendChild(el.badge);

    el.status = mk("div", "sk-dl-status", "Bezig met zoeken naar een stream\u2026");

    var barWrap = mk("div", "sk-dl-bar");
    el.bar = mk("div", "sk-dl-bar-fill");
    barWrap.appendChild(el.bar);

    var actions = mk("div", "sk-dl-actions");

    el.start = mk("button", "sk-dl-btn", "Download als MP4");
    el.start.type = "button";
    el.start.disabled = true;
    el.start.addEventListener("click", start);

    el.cancel = mk("button", "sk-dl-btn danger", "Annuleren");
    el.cancel.type = "button";
    el.cancel.style.display = "none";
    el.cancel.addEventListener("click", cancel);

    el.again = mk("button", "sk-dl-btn ghost", "Opnieuw zoeken");
    el.again.type = "button";
    el.again.addEventListener("click", function () {
      resetProgress();
      rescan();
    });

    var close = mk("button", "sk-dl-x", "\u00d7");
    close.type = "button";
    close.title = "Sluiten";
    close.addEventListener("click", function () {
      root.style.display = "none";
    });

    actions.appendChild(el.start);
    actions.appendChild(el.cancel);
    actions.appendChild(el.again);

    card.appendChild(head);
    card.appendChild(el.status);
    card.appendChild(barWrap);
    card.appendChild(actions);
    card.appendChild(close);
    root.appendChild(card);
    document.documentElement.appendChild(root);

    el.root = root;
  }

  function setStatus(text, isError) {
    if (!el.status) return;
    el.status.textContent = text;
    el.status.classList.toggle("sk-dl-error", !!isError);
  }

  function setProgress(frac) {
    if (el.bar) el.bar.style.width = Math.round(Math.max(0, Math.min(1, frac)) * 100) + "%";
  }

  function resetProgress() {
    setProgress(0);
    el.status.classList.remove("sk-dl-error");
  }

  function updateUi(keepStatus) {
    if (!el.title) return;
    el.title.textContent = state.title || "Schatkamer";
    el.badge.textContent = state.kind === "audio" ? "audio" : "video";
    el.badge.className = "sk-dl-badge" + (state.kind === "audio" ? " audio" : "");
    el.start.textContent = state.kind === "audio" ? "Download als M4A" : "Download als MP4";

    if (state.running) {
      el.start.disabled = true;
      el.cancel.style.display = "";
      el.again.style.display = "none";
      return;
    }

    el.cancel.style.display = "none";
    el.again.style.display = "";

    if (keepStatus) return;

    if (state.detected) {
      el.start.disabled = false;
      var hint = state.drmState === "LIVE"
        ? "Video\u2019s zijn meestal DRM-beveiligd; de extensie controleert dat v\u00f3\u00f3r de download."
        : "Stream gevonden: " + shorten(state.masterUrl);
      setStatus(hint, false);
    } else {
      el.start.disabled = true;
      setStatus("Geen stream gevonden. Start de uitzending op deze pagina en probeer het opnieuw.", false);
    }
  }

  // ---------- download ----------

  function start() {
    if (!state.masterUrl || state.running) return;
    state.running = true;
    resetProgress();
    setStatus("Verbinden met de stream\u2026", false);
    updateUi();

    var id = "job-" + Date.now();
    state.jobId = id;

    chrome.runtime.sendMessage({
      type: "START_JOB",
      id: id,
      job: {
        masterUrl: state.masterUrl,
        pageUrl: location.href,
        title: state.title || document.title || "Schatkamer",
        kind: state.kind || "video"
      }
    }, function (response) {
      if (chrome.runtime.lastError || !response || !response.ok) {
        state.running = false;
        setStatus("De achtergrondworker reageert niet. Herlaad de extensie via chrome://extensions.", true);
        updateUi();
        return;
      }
      poll(id);
    });
  }

  function cancel() {
    if (!state.jobId) return;
    chrome.runtime.sendMessage({ type: "ABORT_JOB", id: state.jobId });
    setStatus("Annuleren\u2026", false);
  }

  function poll(id) {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(function () {
      chrome.runtime.sendMessage({ type: "GET_JOB", id: id }, function (st) {
        if (chrome.runtime.lastError || !st) return;
        if (st.state === "running") {
          var p = st.progress || {};
          if (p.stage === "segments" && p.total) {
            setProgress(p.current / p.total);
            setStatus(p.message || "Bezig\u2026", false);
          } else {
            setStatus(p.message || "Bezig\u2026", false);
          }
          return;
        }

        clearInterval(pollTimer);
        state.running = false;
        state.jobId = null;
        updateUi(true);

        if (st.state === "done" && st.result) {
          setProgress(1);
          setStatus(
            "Klaar: " + st.result.filename + " (" +
            (st.result.bytes / 1048576).toFixed(1) + " MB, " +
            st.result.segments + " segmenten). Opgeslagen in Downloads/Schatkamer/.",
            false
          );
        } else if (st.state === "error") {
          setProgress(0);
          setStatus(st.error || "Onbekende fout.", true);
        }
      });
    }, 600);
  }

  // ---------- opstarten ----------

  build();
  rescan();

  var tries = 0;
  scanTimer = setInterval(function () {
    tries++;
    if (state.detected || tries > 40) {
      clearInterval(scanTimer);
      return;
    }
    rescan();
  }, 1500);

  document.addEventListener("play", function () {
    setTimeout(rescan, 800);
  }, true);

  pageTimer = setInterval(function () {
    if (location.href === state.lastPage) return;
    state.lastPage = location.href;
    state.running = false;
    state.jobId = null;
    if (pollTimer) clearInterval(pollTimer);
    tries = 0;
    resetProgress();
    rescan();
    if (scanTimer) clearInterval(scanTimer);
    scanTimer = setInterval(function () {
      tries++;
      if (state.detected || tries > 40) {
        clearInterval(scanTimer);
        return;
      }
      rescan();
    }, 1500);
  }, 1000);
})();
