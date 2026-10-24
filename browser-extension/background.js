// Achtergrondworker: haalt een onversleutelde HLS-stream op, zet de segmenten
// om naar MP4/M4A (zonder hercodering) en slaat het bestand op.
//
// Bewust niet ondersteund: DRM (Widevine/PlayReady/FairPlay, SAMPLE-AES) en
// AES-128-versleuteling. De worker stopt met een duidelijke melding in plaats
// van een onbruikbaar bestand te maken. Er wordt niets omzeild.

try {
  importScripts("vendor/mux.min.js");
} catch (e) {
  // Zonder mux.js is er nog steeds .ts-uitvoer mogelijk als terugvaloptie.
}

const CONCURRENCY = 4;
const RETRIES = 3;
const FETCH_TIMEOUT_MS = 20000;
const KEEPALIVE_MS = 15000;

const jobs = new Map();

class JobError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

// ---------- playlist-parsing ----------

function parseAttributes(input) {
  const out = {};
  const re = /([A-Z0-9-]+)=("[^"]*"|[^,]*)/g;
  let m;
  while ((m = re.exec(input)) !== null) {
    out[m[1]] = m[2].replace(/^"|"$/g, "");
  }
  return out;
}

function parsePlaylist(text) {
  const lines = text.replace(/^\uFEFF/, "").split(/\r?\n/);
  const out = {
    isMaster: false,
    variants: [],
    media: [],
    segments: [],
    keys: [],
    sessionKeys: [],
    targetDuration: 0,
    ended: false
  };
  let pendingVariant = null;
  let pendingSegment = null;
  let currentMap = null;
  let currentKey = null;

  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;

    if (line.startsWith("#EXT-X-STREAM-INF:")) {
      out.isMaster = true;
      pendingVariant = { attrs: parseAttributes(line.slice(line.indexOf(":") + 1)) };
      continue;
    }
    if (line.startsWith("#EXT-X-MEDIA:")) {
      out.isMaster = true;
      const attrs = parseAttributes(line.slice(line.indexOf(":") + 1));
      if (attrs.TYPE === "AUDIO" && attrs.URI) out.media.push({ attrs });
      continue;
    }
    if (line.startsWith("#EXT-X-SESSION-KEY:")) {
      out.sessionKeys.push(parseAttributes(line.slice(line.indexOf(":") + 1)));
      continue;
    }
    if (line.startsWith("#EXT-X-KEY:")) {
      currentKey = parseAttributes(line.slice(line.indexOf(":") + 1));
      out.keys.push(currentKey);
      continue;
    }
    if (line.startsWith("#EXT-X-MAP:")) {
      const attrs = parseAttributes(line.slice(line.indexOf(":") + 1));
      if (attrs.URI) currentMap = attrs.URI;
      continue;
    }
    if (line.startsWith("#EXTINF:")) {
      const n = parseFloat(line.slice(line.indexOf(":") + 1));
      pendingSegment = { duration: isFinite(n) ? n : 0, key: currentKey, map: currentMap };
      continue;
    }
    if (line.startsWith("#EXT-X-TARGETDURATION:")) {
      out.targetDuration = parseFloat(line.slice(line.indexOf(":") + 1)) || 0;
      continue;
    }
    if (line.startsWith("#EXT-X-ENDLIST")) {
      out.ended = true;
      continue;
    }
    if (line.startsWith("#")) continue;

    if (pendingVariant) {
      out.variants.push({ attrs: pendingVariant.attrs, url: line });
      pendingVariant = null;
    } else if (pendingSegment) {
      out.segments.push({
        url: line,
        duration: pendingSegment.duration,
        key: pendingSegment.key,
        map: pendingSegment.map
      });
      pendingSegment = null;
    }
  }
  return out;
}

function keyKind(key) {
  if (!key) return "none";
  const method = (key.METHOD || "NONE").toUpperCase();
  if (method === "NONE") return "none";
  const format = (key.KEYFORMAT || "").toLowerCase();
  if (format.includes("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")) return "drm"; // Widevine
  if (format.includes("playready") || format.includes("microsoft")) return "drm";
  if (format.includes("streamingkeydelivery") || format.includes("apple")) return "drm";
  if (method === "SAMPLE-AES" || method === "SAMPLE-AES-CTR") return "drm";
  return "encrypted";
}

function detectProtection(playlist) {
  const allKeys = (playlist.sessionKeys || []).concat(playlist.keys || []);
  for (const key of allKeys) {
    const kind = keyKind(key);
    if (kind === "drm") return "drm";
  }
  for (const key of allKeys) {
    if (keyKind(key) === "encrypted") return "encrypted";
  }
  return "none";
}

function pickBestVariant(variants) {
  let best = variants[0];
  const bwOf = (v) => parseFloat(v.attrs.BANDWIDTH || v.attrs["AVERAGE-BANDWIDTH"] || "0") || 0;
  for (const v of variants) {
    if (bwOf(v) > bwOf(best)) best = v;
  }
  return best;
}

function absolute(base, rel) {
  try {
    return new URL(rel, base).toString();
  } catch (e) {
    return rel;
  }
}

function looksLikePlaylist(text) {
  return text.replace(/^\uFEFF/, "").trimStart().startsWith("#EXTM3U");
}

// ---------- URL-normalisatie ----------

function cleanMediaUrl(rawUrl) {
  let url = String(rawUrl || "")
    .replace(/&amp;/g, "&")
    .replace(/\\u0026/gi, "&")
    .replace(/\\\//g, "/")
    .replace(/(?:%22|%5C|["'\\])+$/, "");

  // De site sluit de URL in een RSC-payload af met de id van het volgende
  // chunkje (bijv. "22"), zonder scheidingsteken. Daardoor plakt er twee
  // tekens aan de laatste Key-Pair-Id vast en weigert CloudFront de URL.
  // De CloudFront-key en de signed-URL-key zijn dezelfde sleutel; kap de
  // native key af op de CloudFront-waarde.
  const cfKey = (url.match(/[?&]CloudFront-Key-Pair-Id=([A-Za-z0-9]+)/) || [])[1];
  const native = url.match(/([?&]Key-Pair-Id=)([A-Za-z0-9]+)$/);
  if (cfKey && native && native[2].startsWith(cfKey) && native[2].length > cfKey.length) {
    url = url.slice(0, native.index + native[1].length) + cfKey;
  }
  return url;
}

// ---------- netwerk ----------

async function fetchWithRetry(url, signal, omitCredentials) {
  let lastError = null;
  for (let attempt = 1; attempt <= RETRIES; attempt++) {
    if (signal && signal.aborted) throw new JobError("ABORTED", "Geannuleerd.");
    try {
      const timeout = AbortSignal.timeout(FETCH_TIMEOUT_MS);
      const combined = signal ? AbortSignal.any([signal, timeout]) : timeout;
      const res = await fetch(url, {
        credentials: omitCredentials ? "omit" : "include",
        cache: "no-store",
        signal: combined,
        headers: { "Accept": "*/*" }
      });
      if (res.status === 401 || res.status === 403) {
        throw new JobError(
          "AUTH",
          "De CDN weigerde het verzoek (" + res.status + "). Ververs de pagina en probeer " +
          "opnieuw; lukt het dan nog niet, start de uitzending eerst en download daarna."
        );
      }
      if (res.status === 404) {
        throw new JobError("NOT_FOUND", "Streamonderdeel niet gevonden (404).");
      }
      if (!res.ok) {
        throw new Error("HTTP " + res.status);
      }
      return res;
    } catch (err) {
      if (err instanceof JobError) throw err;
      if (err && err.name === "AbortError") {
        if (signal && signal.aborted) throw new JobError("ABORTED", "Geannuleerd.");
        lastError = new Error("Time-out bij het ophalen van " + url);
      } else {
        lastError = err;
      }
      if (attempt < RETRIES) {
        await new Promise((resolve) => setTimeout(resolve, 400 * attempt));
      }
    }
  }
  throw new JobError("NETWORK", "Netwerkfout: " + (lastError && lastError.message ? lastError.message : "onbekend"));
}

async function fetchText(url, signal, omitCredentials) {
  const res = await fetchWithRetry(url, signal, omitCredentials);
  return res.text();
}

// ---------- downloaden ----------

async function downloadSegments(urls, signal, onProgress) {
  const results = new Array(urls.length);
  let nextIndex = 0;
  let done = 0;

  async function worker() {
    for (;;) {
      const index = nextIndex++;
      if (index >= urls.length) return;
      if (signal.aborted) throw new JobError("ABORTED", "Geannuleerd.");
      const res = await fetchWithRetry(urls[index], signal, false);
      results[index] = new Uint8Array(await res.arrayBuffer());
      done++;
      onProgress(done, urls.length);
    }
  }

  const workers = [];
  const count = Math.min(CONCURRENCY, urls.length);
  for (let i = 0; i < count; i++) workers.push(worker());
  await Promise.all(workers);
  return results;
}

function concatChunks(chunks) {
  let total = 0;
  for (const c of chunks) total += c.byteLength;
  const out = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.byteLength;
  }
  return out;
}

function transmuxTsToFmp4(tsChunks) {
  const mux = globalThis.muxjs;
  const Transmuxer = mux && mux.mp4 && mux.mp4.Transmuxer;
  if (!Transmuxer) return null;
  const transmuxer = new Transmuxer();
  let init = null;
  const parts = [];

  transmuxer.on("data", (segment) => {
    if (segment && segment.initSegment && !init) init = segment.initSegment;
    if (segment && segment.data && segment.data.byteLength) parts.push(segment.data);
  });

  transmuxer.on("error", () => {});
  transmuxer.on("done", () => {});

  for (const chunk of tsChunks) transmuxer.push(chunk);
  transmuxer.flush();

  if (!init || parts.length === 0) return null;
  return concatChunks([init].concat(parts));
}

function safeName(title) {
  return (title || "schatkamer")
    .replace(/[\\/:*?"<>|]+/g, "-")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120) || "schatkamer";
}

// ---------- opslaan via offscreen document ----------
// Een MV3-serviceworker heeft geen URL.createObjectURL. De blob gaat via
// IndexedDB naar een offscreen document, dat er een blob-URL van maakt; de
// worker start daarmee de download.

const BLOB_DB = "sk-downloader";
const BLOB_STORE = "blobs";
let offscreenCreating = null;

function openBlobDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(BLOB_DB, 1);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(BLOB_STORE)) {
        req.result.createObjectStore(BLOB_STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function storeBlob(key, blob) {
  const db = await openBlobDb();
  try {
    await new Promise((resolve, reject) => {
      const tx = db.transaction(BLOB_STORE, "readwrite");
      tx.objectStore(BLOB_STORE).put(blob, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

async function ensureOffscreen() {
  if (await chrome.offscreen.hasDocument()) return;
  if (!offscreenCreating) {
    offscreenCreating = chrome.offscreen.createDocument({
      url: "offscreen.html",
      reasons: ["BLOBS"],
      justification: "Een blob-URL maken zodat de download kan starten."
    }).finally(() => {
      offscreenCreating = null;
    });
  }
  await offscreenCreating;
}

async function downloadBlob(blob, filename) {
  const key = "d-" + Date.now() + "-" + Math.random().toString(36).slice(2);
  await storeBlob(key, blob);
  await ensureOffscreen();
  const res = await chrome.runtime.sendMessage({ target: "offscreen", type: "BLOB_URL", key });
  if (!res || !res.ok || !res.url) {
    throw new JobError("SAVE", "Opslaan mislukt: kon het bestand niet klaarzetten.");
  }
  await chrome.downloads.download({
    url: res.url,
    filename: "Schatkamer/" + filename,
    saveAs: false,
    conflictAction: "uniquify"
  });
}

// ---------- kern ----------

async function runDownload(job, signal, progress) {
  const masterUrl = cleanMediaUrl(job.masterUrl);
  const refererHint = job.pageUrl || "https://schatkamer.beeldengeluid.nl/";

  progress({ stage: "start", message: "Verbinden met de stream\u2026" });

  progress({ stage: "manifest", message: "Playlist ophalen\u2026" });
  let playlistText;
  try {
    playlistText = await fetchText(masterUrl, signal);
  } catch (err) {
    if (err instanceof JobError) throw err;
    throw new JobError("NETWORK", "Playlist ophalen mislukt: " + (err.message || err));
  }
  if (!looksLikePlaylist(playlistText)) {
    throw new JobError(
      "AUTH",
      "De CDN gaf geen geldige playlist terug. Herlaad de pagina, start de video en probeer opnieuw."
    );
  }

  let playlist = parsePlaylist(playlistText);
  let playlistUrl = masterUrl;

  if (playlist.isMaster) {
    if (playlist.sessionKeys.length && detectProtection(playlist) === "drm") {
      throw new JobError("DRM", DRM_MESSAGE);
    }

    if (!playlist.variants.length) {
      throw new JobError("NO_STREAM", "Deze playlist bevat geen afspeelbare kwaliteiten.");
    }

    const best = pickBestVariant(playlist.variants);
    const bw = parseFloat(best.attrs.BANDWIDTH || "0");
    progress({
      stage: "variant",
      message: "Beste kwaliteit kiezen" + (bw ? " (" + Math.round(bw / 1000) + " kbps)" : "") + "\u2026"
    });
    playlistUrl = absolute(masterUrl, best.url);
    const variantText = await fetchText(playlistUrl, signal);
    if (!looksLikePlaylist(variantText)) {
      throw new JobError("AUTH", "De variant-playlist kon niet worden geladen.");
    }
    playlist = parsePlaylist(variantText);
  }

  const protection = detectProtection(playlist);
  if (protection === "drm") throw new JobError("DRM", DRM_MESSAGE);
  if (protection === "encrypted") {
    throw new JobError(
      "ENCRYPTED",
      "Deze stream is versleuteld met AES (geen DRM, maar wel versleuteld). " +
      "Die versleuteling wordt door deze extensie niet ondersteund."
    );
  }

  if (!playlist.segments.length) {
    throw new JobError("NO_SEGMENTS", "Geen videostukken gevonden. Is dit een live-stream?");
  }

  const segUrls = playlist.segments.map((s) => absolute(playlistUrl, s.url));
  const usesFmp4 = playlist.segments.some((s) => s.map);

  progress({
    stage: "segments",
    current: 0,
    total: segUrls.length,
    message: "Downloaden: 0/" + segUrls.length
  });

  const chunks = await downloadSegments(segUrls, signal, (done, total) => {
    progress({
      stage: "segments",
      current: done,
      total,
      message: "Downloaden: " + done + "/" + total
    });
  });

  const totalBytes = chunks.reduce((sum, c) => sum + c.byteLength, 0);
  progress({
    stage: "muxen",
    message: "Omzetten naar " + (job.kind === "audio" ? "M4A" : "MP4") +
      " (" + (totalBytes / 1048576).toFixed(1) + " MB)\u2026"
  });

  let output = null;
  let extension;
  let remuxed = false;

  if (usesFmp4) {
    const parts = [];
    let lastMap = null;
    for (let i = 0; i < playlist.segments.length; i++) {
      const seg = playlist.segments[i];
      const mapUrl = seg.map ? absolute(playlistUrl, seg.map) : null;
      if (mapUrl && mapUrl !== lastMap) {
        if (signal.aborted) throw new JobError("ABORTED", "Geannuleerd.");
        const initRes = await fetchWithRetry(mapUrl, signal, false);
        parts.push(new Uint8Array(await initRes.arrayBuffer()));
        lastMap = mapUrl;
      }
      parts.push(chunks[i]);
    }
    output = concatChunks(parts);
    extension = job.kind === "audio" ? "m4a" : "mp4";
  } else {
    let fmp4 = null;
    try {
      fmp4 = transmuxTsToFmp4(chunks);
    } catch (e) {
      fmp4 = null;
    }
    if (fmp4) {
      output = fmp4;
      extension = job.kind === "audio" ? "m4a" : "mp4";
      remuxed = true;
    } else {
      output = concatChunks(chunks);
      extension = "ts";
      remuxed = false;
    }
  }

  progress({ stage: "opslaan", message: "Bestand opslaan\u2026" });

  const mime = extension === "ts" ? "video/mp2t" : job.kind === "audio" ? "audio/mp4" : "video/mp4";
  const blob = new Blob([output], { type: mime });
  const filename = safeName(job.title) + "." + extension;
  await downloadBlob(blob, filename);

  return {
    filename,
    bytes: output.byteLength,
    segments: playlist.segments.length,
    remuxed,
    kind: job.kind || "video",
    referer: refererHint
  };
}

const DRM_MESSAGE =
  "Deze stream is DRM-beveiligd (Widevine/PlayReady/FairPlay). Downloaden is niet " +
  "toegestaan en de extensie omzeilt de beveiliging niet. Alleen onversleutelde " +
  "streams (meestal audio) zijn downloadbaar.";

// ---------- berichtafhandeling ----------

function startKeepalive() {
  return setInterval(() => {
    chrome.runtime.getPlatformInfo().catch(() => {});
  }, KEEPALIVE_MS);
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (!msg || !msg.type) return false;

  if (msg.type === "START_JOB") {
    const id = msg.id || "job-" + Date.now();
    const controller = new AbortController();
    const job = { state: "running", progress: { stage: "start", message: "Bezig\u2026" }, controller };
    jobs.set(id, job);

    const keepalive = startKeepalive();

    runDownload(msg.job, controller.signal, (p) => {
      job.progress = p;
    })
      .then((result) => {
        job.state = "done";
        job.result = result;
      })
      .catch((err) => {
        if (err && err.name === "AbortError") {
          job.state = "error";
          job.error = "Geannuleerd.";
          job.code = "ABORTED";
          return;
        }
        job.state = "error";
        job.error = err && err.message ? err.message : String(err);
        job.code = err && err.code ? err.code : null;
      })
      .finally(() => {
        clearInterval(keepalive);
        job.controller = null;
      });

    sendResponse({ ok: true, id });
    return false;
  }

  if (msg.type === "GET_JOB") {
    const job = jobs.get(msg.id);
    if (!job) {
      sendResponse({ state: "onbekend" });
      return false;
    }
    sendResponse({
      state: job.state,
      progress: job.progress,
      result: job.result,
      error: job.error,
      code: job.code
    });
    if (job.state !== "running") {
      setTimeout(() => jobs.delete(msg.id), 60000);
    }
    return false;
  }

  if (msg.type === "ABORT_JOB") {
    const job = jobs.get(msg.id);
    if (job && job.controller) {
      job.controller.abort();
      sendResponse({ ok: true });
    } else {
      sendResponse({ ok: false });
    }
    return false;
  }

  return false;
});
