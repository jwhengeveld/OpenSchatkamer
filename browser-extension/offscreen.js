const DB_NAME = "sk-downloader";
const STORE = "blobs";

function takeBlob(key) {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE)) {
        req.result.createObjectStore(STORE);
      }
    };
    req.onerror = () => reject(req.error);
    req.onsuccess = () => {
      const db = req.result;
      const tx = db.transaction(STORE, "readwrite");
      const store = tx.objectStore(STORE);
      const get = store.get(key);
      get.onsuccess = () => {
        const blob = get.result;
        if (blob) store.delete(key);
        resolve(blob);
      };
      get.onerror = () => reject(get.error);
      tx.oncomplete = () => db.close();
      tx.onerror = () => {
        db.close();
        reject(tx.error);
      };
    };
  });
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (!msg || msg.target !== "offscreen" || msg.type !== "BLOB_URL") return false;

  (async () => {
    try {
      const blob = await takeBlob(msg.key);
      if (!blob) throw new Error("blob niet gevonden");
      const url = URL.createObjectURL(blob);
      setTimeout(() => URL.revokeObjectURL(url), 600000);
      sendResponse({ ok: true, url });
    } catch (e) {
      sendResponse({ ok: false, error: e && e.message ? e.message : String(e) });
    }
  })();

  return true;
});
