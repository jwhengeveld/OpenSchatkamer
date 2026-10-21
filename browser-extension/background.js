// Achtergrondworker: vindt de master.m3u8, haalt segmenten op met de juiste
// CloudFront-cookies, plakt ze aan elkaar en slaat het bestand op.
//
// Bewust NIET ondersteund: Widevine/EME-DRM. Als een playlist #EXT-X-KEY met
// METHOD=SAMPLE-AES of KEYFORMAT bevat, stopt de worker met een duidelijke
// melding in plaats van een onbruikbaar bestand te maken.

const UA_FALLBACK = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

// ---------- hulpfuncties ----------

function parseM3u8(text) {
  const lines = text.split(/\r?\n/);
  const master = { variants: [], media: [], keys: [], segments: [], targetDuration: 0 };
  let currentKey = null;

  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;

    if (line.startsWith("#EXT-X-TARGETDURATION:")) {
      master.targetDuration = parseFloat(line.split(":")[1]) || 0;
    } else if (line.startsWith("#EXT-X-KEY:")) {
      currentKey = parseKeyTag(line);
      master.keys.push(currentKey);
    } else if (line.startsWith("#EXT-X-STREAM-INF:")) {
      const attrs = parseAttrs(line.slice(line.indexOf(":") + 1));
      const next = nextNonComment(lines, lines.indexOf(raw));
      if (next) master.variants.push({ attrs: attrs, url: next });
    } else if (line.startsWith("#EXTINF:")) {
      const next = nextNonCommentAfter(lines, lines.indexOf(raw));
      if (next) master.segments.push({ url: next, key: currentKey });
    } else if (!line.startsWith("#")) {
      // variant zonder STREAM-INF erboven
      if (master.segments.length === 0 && master.variants.length === 0) {
        master.segments.push({ url: line, key: currentKey });
      }
    }
  }
  return master;
}

function nextNonComment(lines, from) {
  for (let i = from + 1; i < lines.length; i++) {
    const l = lines[i].trim();
    if (l && !l.startsWith("#")) return l;
  }
  return null;
}

function nextNonCommentAfter(lines, from) {
  return nextNonComment(lines, from);
}

function parseKeyTag(line) {
  const attrs = parseAttrs(line.slice(line.indexOf(":") + 1));
  return {
    method: (attrs.METHOD || "").toUpperCase(),
    uri: attrs.URI || null,
    keyFormat: attrs.KEYFORMAT || null,
    iv: attrs.IV || null
  };
}

function parseAttrs(str) {
  const out = {};
  const re = /([A-Z0-9-]+)=("[^"]*"|[^,]*)/g;
  let m;
  while ((m = re.exec(str)) !== null) {
    out[m[1]] = m[2].replace(/^"|"$/g, "");
  }
  return out;
}

function isDrm(playlist) {
  return (playlist.keys || []).some(function (k) {
    if (!k) return false;
    if (k.method === "SAMPLE-AES" || k.method === "SAMPLE-AES-CTR") return true;
    if (k.keyFormat && k.keyFormat.indexOf("widevine") !== -1) return true;
    if (k.keyFormat && k.keyFormat.indexOf("com.apple") !== -1) return true;
    return false;
  });
}

function absolute(base, rel) {
  try {
    return new URL(rel, base).toString();
  } catch (e) {
    return rel;
  }
}

async function cookiesForUrl(url) {
  try {
    const host = new URL(url).hostname;
    const jar = await chrome.cookies.getAll({ domain: host });
    if (!jar.length) return "";
    return jar
      .map(function (c) { return c.name + "=" + c.value; })
      .join("; ");
  } catch (e) {
    return "";
  }
}

// Referer + cookies meesturen: CloudFront signed cookies leven op *.cdn.beeldengeluid.nl
async function buildHeaders(url, referer) {
  const headers = { "User-Agent": UA_FALLBACK, "Accept": "*/*" };
  if (referer) headers["Referer"] = referer;
  const cookie = await cookiesForUrl(url);
  if (cookie) headers["Cookie"] = cookie;
  return headers;
}

async function fetchText(url, referer) {
  const res = await fetch(url, { headers: await buildHeaders(url, referer), credentials: "include" });
  if (!res.ok) throw new Error("HTTP " + res.status + " voor " + url);
  return res.text();
}

async function fetchBinary(url, referer) {
  const res = await fetch(url, { headers: await buildHeaders(url, referer), credentials: "include" });
  if (!res.ok) throw new Error("HTTP " + res.status + " voor " + url);
  return new Uint8Array(await res.arrayBuffer());
}

// Kiest de hoogste bandbreedte uit de variant-list
function pickBest(variants) {
  if (!variants.length) return null;
  var best = variants[0];
  for (const v of variants) {
    var bw = parseFloat(v.attrs["BANDWIDTH"] || v.attrs["AVERAGE-BANDWIDTH"] || "0");
    var bbw = parseFloat(best.attrs["BANDWIDTH"] || best.attrs["AVERAGE-BANDWIDTH"] || "0");
    if (bw > bbw) best = v;
  }
  return best;
}

function safeName(title) {
  return (title || "schatkamer")
    .replace(/[\\/:*?"<>|]+/g, "-")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120) || "schatkamer";
}

// ---------- kern: stukken samenvoegen ----------

async function runDownload(job, progress) {
  const masterUrl = job.m3u8;
  const referer = job.referer || "https://schatkamer.beeldengeluid.nl/";

  progress({ stage: "manifest", message: "Master-playlist ophalen..." });
  const masterText = await fetchText(masterUrl, referer);

  let playlist = parseM3u8(masterText);
  let baseUrl = masterUrl;

  // Master? Dan de beste variant volgen.
  if (playlist.variants.length) {
    const best = pickBest(playlist.variants);
    progress({ stage: "variant", message: "Beste kwaliteit kiezen (" +
      Math.round(parseFloat(best.attrs["BANDWIDTH"] || 0) / 1000) + " kbps)..." });
    const variantUrl = absolute(masterUrl, best.url);
    const variantText = await fetchText(variantUrl, referer);
    playlist = parseM3u8(variantText);
    baseUrl = variantUrl;
  }

  if (!playlist.segments.length) {
    throw new Error("Geen videostukken gevonden in de playlist. Is dit een live-stream?");
  }

  if (isDrm(playlist)) {
    const err = new Error(
      "DRM (Widevine/SAMPLE-AES) gedetecteerd. Deze stream is versleuteld en kan niet " +
      "worden gedownload. Wel toegestaan: op de website beschikbare, niet-DRM streams."
    );
    err.code = "DRM";
    throw err;
  }

  const chunks = [];
  let total = 0;
  const totalSegs = playlist.segments.length;

  for (let i = 0; i < totalSegs; i++) {
    const seg = playlist.segments[i];
    const segUrl = absolute(baseUrl, seg.url);
    progress({
      stage: "segments",
      current: i + 1,
      total: totalSegs,
      message: "Stuk " + (i + 1) + "/" + totalSegs
    });
    const bytes = await fetchBinary(segUrl, referer);
    chunks.push(bytes);
    total += bytes.byteLength;
  }

  progress({ stage: "muxen", message: "Bestanden samenvoegen (" +
    (total / (1024 * 1024)).toFixed(1) + " MB)..." });

  const blob = new Blob(chunks, { type: "video/mp2t" });
  const filename = safeName(job.title) + ".ts";

  const outUrl = URL.createObjectURL(blob);
  await chrome.downloads.download({ url: outUrl, filename: "Schatkamer/" + filename, saveAs: true });
  setTimeout(function () { URL.revokeObjectURL(outUrl); }, 60000);

  return {
    segments: totalSegs,
    bytes: total,
    filename: filename
  };
}

// ---------- berichtafhandeling ----------

const jobs = new Map();

chrome.runtime.onMessage.addListener(function (msg, sender, sendResponse) {
  if (!msg || !msg.type) return false;

  if (msg.type === "START_JOB") {
    const id = msg.id;
    jobs.set(id, { state: "running", progress: { stage: "start", message: "Bezig..." } });
    runDownload(msg.job, function (p) {
      jobs.set(id, { state: "running", progress: p });
    })
      .then(function (result) {
        jobs.set(id, { state: "done", result: result });
      })
      .catch(function (err) {
        jobs.set(id, { state: "error", error: err.message || String(err), code: err.code || null });
      });
    sendResponse({ ok: true, id: id });
    return true;
  }

  if (msg.type === "GET_JOB") {
    sendResponse(jobs.get(msg.id) || { state: "onbekend" });
    return true;
  }

  return false;
});

chrome.runtime.onInstalled.addListener(function () {
  chrome.storage.local.get(["history"], function (s) {
    if (!s.history) chrome.storage.local.set({ history: [] });
  });
});

chrome.runtime.onStartup && chrome.runtime.onStartup.addListener(function () {});
