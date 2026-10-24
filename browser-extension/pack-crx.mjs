#!/usr/bin/env node
// CRX3-packer: verpakt een zip met extensiebestanden in een ondertekend .crx-bestand.
// Formaat volgens components/crx_file/crx3.proto:
//   "Cr24" | uint32le(versie=3) | uint32le(headerlengte) | header | zip
// De signature dekt "CRX3 SignedData\0" + uint32le(len(signed_header_data)) +
// signed_header_data + de volledige zip.

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_KEY = path.join(HERE, "keys", "crx-key.pem");
const CONTEXT = Buffer.from("CRX3 SignedData\0", "utf8");

function uint32le(n) {
  const b = Buffer.alloc(4);
  b.writeUInt32LE(n >>> 0, 0);
  return b;
}

function varint(n) {
  const bytes = [];
  let value = n;
  while (value > 0x7f) {
    bytes.push((value & 0x7f) | 0x80);
    value = Math.floor(value / 128);
  }
  bytes.push(value);
  return Buffer.from(bytes);
}

function fieldBytes(number, bytes) {
  return Buffer.concat([varint((number << 3) | 2), varint(bytes.length), bytes]);
}

function signedHeaderData(crxId) {
  return fieldBytes(1, crxId);
}

function proofMessage(publicKey, signature) {
  return Buffer.concat([fieldBytes(1, publicKey), fieldBytes(2, signature)]);
}

function fileHeader(proof, signedData) {
  return Buffer.concat([fieldBytes(2, proof), fieldBytes(10000, signedData)]);
}

function readVarint(buf, pos) {
  let result = 0;
  let shift = 0;
  while (pos < buf.length) {
    const byte = buf[pos++];
    result += (byte & 0x7f) * Math.pow(2, shift);
    if ((byte & 0x80) === 0) return { value: result, pos };
    shift += 7;
    if (shift > 56) throw new Error("varint te groot");
  }
  throw new Error("onvolledige varint");
}

function parseMessage(buf) {
  const fields = [];
  let pos = 0;
  while (pos < buf.length) {
    const tag = readVarint(buf, pos);
    pos = tag.pos;
    const number = tag.value >>> 3;
    const wire = tag.value & 7;
    if (wire !== 2) {
      if (wire === 0) {
        pos = readVarint(buf, pos).pos;
        continue;
      }
      if (wire === 1) { pos += 8; continue; }
      if (wire === 5) { pos += 4; continue; }
      throw new Error("ongeldig wire-type " + wire);
    }
    const len = readVarint(buf, pos);
    pos = len.pos;
    fields.push({ number, value: buf.subarray(pos, pos + len.value) });
    pos += len.value;
  }
  return fields;
}

function encodeExtensionId(crxId) {
  return crxId.toString("hex").split("").map((c) => String.fromCharCode(97 + parseInt(c, 16))).join("");
}

function loadOrCreateKey(keyPath) {
  if (fs.existsSync(keyPath)) {
    return crypto.createPrivateKey(fs.readFileSync(keyPath));
  }
  fs.mkdirSync(path.dirname(keyPath), { recursive: true });
  const { privateKey } = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
  const pem = privateKey.export({ type: "pkcs8", format: "pem" });
  fs.writeFileSync(keyPath, pem, { mode: 0o600 });
  console.log("Nieuwe sleutel aangemaakt: " + keyPath);
  return privateKey;
}

function pack(zipPath, crxPath, keyPath) {
  const zip = fs.readFileSync(zipPath);
  if (zip.subarray(0, 2).toString() !== "PK") {
    throw new Error("Geen geldig zip-bestand: " + zipPath);
  }

  const privateKey = loadOrCreateKey(keyPath);
  const publicKey = crypto.createPublicKey(privateKey).export({ type: "spki", format: "der" });
  const crxId = crypto.createHash("sha256").update(publicKey).digest().subarray(0, 16);

  const signedData = signedHeaderData(crxId);
  const signer = crypto.createSign("sha256");
  signer.update(CONTEXT);
  signer.update(uint32le(signedData.length));
  signer.update(signedData);
  signer.update(zip);
  const signature = signer.sign(privateKey);

  const header = fileHeader(proofMessage(publicKey, signature), signedData);
  const crx = Buffer.concat([Buffer.from("Cr24", "utf8"), uint32le(3), uint32le(header.length), header, zip]);
  fs.mkdirSync(path.dirname(crxPath), { recursive: true });
  fs.writeFileSync(crxPath, crx);

  console.log("CRX geschreven: " + crxPath);
  console.log("  zip: " + zip.length + " bytes, crx: " + crx.length + " bytes");
  console.log("  extensie-ID: " + encodeExtensionId(crxId));
  return crxId;
}

function verify(crxPath) {
  const crx = fs.readFileSync(crxPath);
  if (crx.subarray(0, 4).toString("utf8") !== "Cr24") throw new Error("Geen CRX-bestand (magic ontbreekt)");
  const version = crx.readUInt32LE(4);
  if (version !== 3) throw new Error("Onverwachte CRX-versie: " + version);
  const headerLen = crx.readUInt32LE(8);
  const header = crx.subarray(12, 12 + headerLen);
  const zip = crx.subarray(12 + headerLen);

  const fields = parseMessage(header);
  const proof = fields.find((f) => f.number === 2);
  const signed = fields.find((f) => f.number === 10000);
  if (!proof || !signed) throw new Error("Header mist bewijs of signed_header_data");

  const proofFields = parseMessage(proof.value);
  const publicKeyRaw = proofFields.find((f) => f.number === 1).value;
  const signature = proofFields.find((f) => f.number === 2).value;
  const crxId = crypto.createHash("sha256").update(publicKeyRaw).digest().subarray(0, 16);
  const signedFields = parseMessage(signed.value);
  const storedId = signedFields.find((f) => f.number === 1).value;
  if (!storedId.equals(crxId)) throw new Error("crx_id komt niet overeen met de publieke sleutel");

  const publicKey = crypto.createPublicKey({ key: publicKeyRaw, format: "der", type: "spki" });
  const verifier = crypto.createVerify("sha256");
  verifier.update(CONTEXT);
  verifier.update(uint32le(signed.value.length));
  verifier.update(signed.value);
  verifier.update(zip);
  if (!verifier.verify(publicKey, signature)) throw new Error("Signature ongeldig");

  console.log("CRX geverifieerd: " + crxPath);
  console.log("  extensie-ID: " + encodeExtensionId(crxId));
  console.log("  zip: " + zip.length + " bytes");
}

const [command, ...args] = process.argv.slice(2);
try {
  if (command === "pack" && args.length >= 2) {
    pack(args[0], args[1], args[2] || DEFAULT_KEY);
  } else if (command === "verify" && args.length === 1) {
    verify(args[0]);
  } else {
    console.log("Gebruik:");
    console.log("  node pack-crx.mjs pack <input.zip> <output.crx> [sleutel.pem]");
    console.log("  node pack-crx.mjs verify <bestand.crx>");
    process.exit(1);
  }
} catch (err) {
  console.error("Fout: " + err.message);
  process.exit(1);
}
