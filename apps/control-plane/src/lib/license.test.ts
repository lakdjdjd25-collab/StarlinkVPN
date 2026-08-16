import { describe, expect, it } from "vitest";
import { generateLicense, isValidLicense, licenseQrPayload, normalizeLicense } from "./license";

describe("NimHUB licenses", () => {
  it("normalizes and accepts existing compatible licenses", () => {
    expect(normalizeLicense("  old_key-123  ")).toBe("OLD_KEY-123");
    expect(isValidLicense("old_key-123")).toBe(true);
    expect(isValidLicense("bad value")).toBe(false);
  });

  it("generates strong, readable, QR-compatible licenses", () => {
    const values = new Set(Array.from({ length: 128 }, generateLicense));
    expect(values.size).toBe(128);
    for (const value of values) {
      expect(value).toMatch(/^NH-[A-HJ-NP-Z2-9]{4}(?:-[A-HJ-NP-Z2-9]{4}){3}$/);
      expect(isValidLicense(value)).toBe(true);
      expect(licenseQrPayload(value)).toBe(`NIMHUB:${value}`);
    }
  });
});
