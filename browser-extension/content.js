// Vindt de video op de Schatkamer-pagina en biedt een downloadknop aan.
// Detecteert de master.m3u8 op drie manieren:
//   1. al geladen door de speler (performance entries)
//   2. de src/currentSrc van het video-element
//   3. tekst in de pagina-HTML (zelfde aanpak als de Android-app)
//
// De overlay wordt met DOM-API's opgebouwd, niet met innerHTML: dat is
// veiliger en raakt geen quoting.

(function () {
  "use strict";

  var M3U8_RE = /https?:\/\/[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*/g;

  var state = { m3u8: null, title: "", detected: false };
  var el = {};
  var pollTimer = null;
  var scanTimer = null;

  // ---------- detectie ----------

  function currentTitle() {
    return (document.title || "")
      .replace(/\s*[|\u2013\u2014-]\s*.*Schatkamer.*$/i, "")
      .replace(/\s*[|\u2013\u2014-]\s*Beeld\s*&\s*Geluid.*$/i, "")
      .trim();
  }

  function scanPerformance() {
    try {
      var entries = performance.getEntriesByType("resource");
      for (var i = 0; i < entries.length; i++) {
        var n = entries[i].name;
        if (n && n.indexOf(".m3u8") !== -1) { state.m3u8 = n; return true; }
      }
    } catch (e) {}
    return false;
  }

  function scanDom() {
    var vids = document.querySelectorAll("video");
    for (var i = 0; i < vids.length; i++) {
      var src = vids[i].currentSrc || vids[i].src || "";
      if (src && src.indexOf(".m3u8") !== -1) { state.m3u8 = src; return true; }
    }
    return false;
  }

  function scanHtml() {
    try {
      var m = document.documentElement.innerHTML.match(M3U8_RE);
      if (m && m.length) { state.m3u8 = m[0]; return true; }
    } catch (e) {}
    return false;
  }

  function rescan() {
    state.title = currentTitle();
    state.detected = scanPerformance() || scanDom() || scanHtml();
    if (el.title) el.title.textContent = state.title || "Schatkamer";
    if (state.detected) {
      setStatus("Stream gevonden: " + shorten(state.m3u8));
      if (el.start) el.start.disabled = false;
    } else {
      setStatus("Nog geen stream gevonden. Start de video op deze pagina, dan verschijnt de knop.");
      if (el.start) el.start.disabled = true;
    }
  }

  function shorten(u) {
    try {
      var p = new URL(u);
      var last = p.pathname.split("/").filter(Boolean).pop() || "";
      return p.hostname + " \u203a " + last;
    } catch (e) {
      return u.length > 60 ? u.slice(0, 57) + "\u2026" : u;
    }
  }

  // ---------- overlay opbouwen ----------

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

    el.title = mk("div", "sk-dl-title", "Schatkamer");
    el.status = mk("div", "sk-dl-status", "Bezig met zoeken naar de videostream\u2026");

    var barWrap = mk("div", "sk-dl-bar");
    el.bar = mk("div", "sk-dl-bar-fill");
    barWrap.appendChild(el.bar);

    var actions = mk("div", "sk-dl-actions");

    el.again = mk("button", "sk-dl-btn ghost", "Opnieuw zoeken");
    el.again.type = "button";
    el.again.addEventListener("click", function () {
      setProgress(0);
      rescan();
    });

    el.start = mk("button", "sk-dl-btn", "Download als MP4");
    el.start.type = "button";
    el.start.disabled = true;
    el.start.addEventListener("click", start);

    actions.appendChild(el.again);
    actions.appendChild(el.start);

    var close = mk("button", "sk-dl-x", "\u00d7");
    close.type = "button";
    close.title = "Sluiten";
    close.addEventListener("click", function () { root.style.display = "none"; });

    card.appendChild(el.title);
    card.appendChild(el.status);
    card.appendChild(barWrap);
    card.appendChild(actions);
    card.appendChild(close);
    root.appendChild(card);
    document.documentElement.appendChild(root);

    el.root = root;
  }

  function setStatus(t) { if (el.status) el.status.textContent = t; }

  function setProgress(frac, text) {
    if (el.bar) el.bar.style.width = Math.round(Math.max(0, Math.min(1, frac)) * 100) + "%";
    if (text) setStatus(text);
  }

  // ---------- download ----------

  async function start() {
    if (!state.m3u8) return;
    if (el.start) el.start.disabled = true;
    setStatus("Verbinden met de stream\u2026");

    var id = "job-" + Date.now();
    try {
      await chrome.runtime.sendMessage({
        type: "START_JOB",
        id: id,
        job: {
          m3u8: state.m3u8,
          title: state.title || document.title,
          referer: location.origin + "/"
        }
      });
    } catch (err) {
      setStatus("Achtergrondworker reageert niet: " + (err && err.message ? err.message : err));
      if (el.start) el.start.disabled = false;
      return;
    }

    poll(id);
  }

  function poll(id) {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(async function () {
      var st;
      try {
        st = await chrome.runtime.sendMessage({ type: "GET_JOB", id: id });
      } catch (e) { return; }
      if (!st) return;

      if (st.state === "running" && st.progress) {
        var p = st.progress;
        if (p.stage === "segments" && p.total) {
          setProgress(p.current / p.total, p.message);
        } else if (p.stage === "muxen") {
          setProgress(0.95, p.message);
        } else {
          setStatus(p.message || "Bezig\u2026");
        }
      } else if (st.state === "done") {
        clearInterval(pollTimer);
        setProgress(1,
          "Klaar: " + st.result.filename + " (" +
          (st.result.bytes / 1048576).toFixed(1) + " MB, " + st.result.segments + " stukken)");
        if (el.start) el.start.disabled = false;
      } else if (st.state === "error") {
        clearInterval(pollTimer);
        setProgress(0, st.error);
        if (el.start) el.start.disabled = false;
      }
    }, 700);
  }

  // ---------- opstarten ----------

  build();
  rescan();

  var tries = 0;
  scanTimer = setInterval(function () {
    tries++;
    if (state.detected || tries > 24) { clearInterval(scanTimer); return; }
    rescan();
  }, 1500);

  document.addEventListener("play", function () {
    setTimeout(rescan, 800);
  }, true);
})();
