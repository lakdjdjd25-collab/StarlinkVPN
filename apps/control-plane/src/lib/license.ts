import { randomBytes } from "node:crypto";

const GENERATED_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
const LICENSE_PATTERN = /^[A-Z0-9][A-Z0-9_-]{5,63}$/;
const QR_PREFIX = "NIMHUB:";

export function normalizeLicense(value: string): string {
  return value.trim().toUpperCase();
}

export function isValidLicense(value: string): boolean {
  return LICENSE_PATTERN.test(normalizeLicense(value));
}

export function generateLicense(): string {
  const characters: string[] = [];
  const unbiasedLimit = Math.floor(256 / GENERATED_ALPHABET.length) * GENERATED_ALPHABET.length;
  while (characters.length < 16) {
    for (const byte of randomBytes(24)) {
      if (byte >= unbiasedLimit) continue;
      characters.push(GENERATED_ALPHABET[byte % GENERATED_ALPHABET.length]);
      if (characters.length === 16) break;
    }
  }
  const groups = Array.from({ length: 4 }, (_, index) => characters.slice(index * 4, index * 4 + 4).join(""));
  return `NH-${groups.join("-")}`;
}

export function licenseQrPayload(license: string): string {
  const normalized = normalizeLicense(license);
  if (!isValidLicense(normalized)) throw new Error("License is invalid");
  return `${QR_PREFIX}${normalized}`;
}
