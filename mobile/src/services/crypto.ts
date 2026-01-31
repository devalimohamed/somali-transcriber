import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';
import CryptoJS from 'crypto-js';

const STORAGE_KEY = 'somtranscriber_audio_encryption_key';
const PAYLOAD_VERSION = 'v3';
const PREVIOUS_PAYLOAD_VERSION = 'v2';
const KEY_BYTES = 32;
const IV_BYTES = 16;
const MAC_HEX_LENGTH = 64;

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function isValidHex(value: string, expectedLength: number): boolean {
  return value.length === expectedLength && /^[0-9a-f]+$/i.test(value);
}

async function randomHex(byteLength: number): Promise<string> {
  const bytes = await Crypto.getRandomBytesAsync(byteLength);
  return bytesToHex(bytes);
}

async function getOrCreateKey(): Promise<string> {
  const existing = await SecureStore.getItemAsync(STORAGE_KEY);
  if (existing && isValidHex(existing, KEY_BYTES * 2)) {
    return existing;
  }

  const key = await randomHex(KEY_BYTES);
  await SecureStore.setItemAsync(STORAGE_KEY, key);
  return key;
}

function deriveMacKeyHex(keyHex: string): string {
  return CryptoJS.SHA256(`mac:${keyHex}`).toString(CryptoJS.enc.Hex);
}

function constantTimeEqualHex(a: string, b: string): boolean {
  if (a.length !== b.length) {
    return false;
  }

  let mismatch = 0;
  for (let i = 0; i < a.length; i += 1) {
    mismatch |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return mismatch === 0;
}

export async function encryptBase64Audio(rawBase64: string): Promise<string> {
  const keyHex = await getOrCreateKey();
  const macKeyHex = deriveMacKeyHex(keyHex);
  const ivHex = await randomHex(IV_BYTES);
  const key = CryptoJS.enc.Hex.parse(keyHex);
  const iv = CryptoJS.enc.Hex.parse(ivHex);
  const encrypted = CryptoJS.AES.encrypt(rawBase64, key, {
    iv,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7
  });
  const ciphertextBase64 = encrypted.ciphertext.toString(CryptoJS.enc.Base64);
  const macHex = CryptoJS.HmacSHA256(
    `${PAYLOAD_VERSION}:${ivHex}:${ciphertextBase64}`,
    CryptoJS.enc.Hex.parse(macKeyHex)
  ).toString(CryptoJS.enc.Hex);

  return `${PAYLOAD_VERSION}:${ivHex}:${ciphertextBase64}:${macHex}`;
}

export async function decryptBase64Audio(encryptedPayload: string): Promise<string> {
  const keyHex = await getOrCreateKey();
  const macKeyHex = deriveMacKeyHex(keyHex);

  if (encryptedPayload.startsWith(`${PAYLOAD_VERSION}:`)) {
    const parts = encryptedPayload.split(':');
    if (parts.length !== 4 || !isValidHex(parts[1], IV_BYTES * 2) || !isValidHex(parts[3], MAC_HEX_LENGTH)) {
      throw new Error('Invalid encrypted payload format');
    }

    const [, ivHex, ciphertextBase64, macHex] = parts;
    const expectedMacHex = CryptoJS.HmacSHA256(
      `${PAYLOAD_VERSION}:${ivHex}:${ciphertextBase64}`,
      CryptoJS.enc.Hex.parse(macKeyHex)
    ).toString(CryptoJS.enc.Hex);
    if (!constantTimeEqualHex(macHex.toLowerCase(), expectedMacHex.toLowerCase())) {
      throw new Error('Encrypted payload integrity check failed');
    }

    const key = CryptoJS.enc.Hex.parse(keyHex);
    const iv = CryptoJS.enc.Hex.parse(ivHex);
    const cipherParams = CryptoJS.lib.CipherParams.create({
      ciphertext: CryptoJS.enc.Base64.parse(ciphertextBase64)
    });
    const bytes = CryptoJS.AES.decrypt(cipherParams, key, {
      iv,
      mode: CryptoJS.mode.CBC,
      padding: CryptoJS.pad.Pkcs7
    });
    const plaintext = bytes.toString(CryptoJS.enc.Utf8);
    if (!plaintext) {
      throw new Error('Failed to decrypt queued audio payload');
    }
    return plaintext;
  }

  if (encryptedPayload.startsWith(`${PREVIOUS_PAYLOAD_VERSION}:`)) {
    const parts = encryptedPayload.split(':');
    if (parts.length !== 3 || !isValidHex(parts[1], IV_BYTES * 2)) {
      throw new Error('Invalid encrypted payload format');
    }

    const [, ivHex, ciphertextBase64] = parts;
    const key = CryptoJS.enc.Hex.parse(keyHex);
    const iv = CryptoJS.enc.Hex.parse(ivHex);
    const cipherParams = CryptoJS.lib.CipherParams.create({
      ciphertext: CryptoJS.enc.Base64.parse(ciphertextBase64)
    });
    const bytes = CryptoJS.AES.decrypt(cipherParams, key, {
      iv,
      mode: CryptoJS.mode.CBC,
      padding: CryptoJS.pad.Pkcs7
    });
    const plaintext = bytes.toString(CryptoJS.enc.Utf8);
    if (!plaintext) {
      throw new Error('Failed to decrypt queued audio payload');
    }
    return plaintext;
  }

  // Backward compatibility for payloads encrypted with legacy passphrase mode.
  const legacyBytes = CryptoJS.AES.decrypt(encryptedPayload, keyHex);
  const legacyPlaintext = legacyBytes.toString(CryptoJS.enc.Utf8);
  if (!legacyPlaintext) {
    throw new Error('Failed to decrypt queued audio payload');
  }
  return legacyPlaintext;
}
