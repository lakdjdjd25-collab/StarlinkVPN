import { beforeAll, describe, expect, it } from "vitest";
import { decryptConfig, encryptConfig } from "./config-encryption";

beforeAll(() => {
  process.env.CONFIG_ENCRYPTION_KEY = Buffer.alloc(32, 7).toString("base64");
});

describe("node configuration encryption", () => {
  it("round trips structured runtime configuration", () => {
    const value = { protocol: "vless", host: "vpn.example", port: 443 };
    const encrypted = encryptConfig(value);
    expect(encrypted).not.toContain("vpn.example");
    expect(decryptConfig(encrypted)).toEqual(value);
  });

  it("rejects tampered ciphertext", () => {
    const encrypted = encryptConfig({ secret: "value" });
    const parts = encrypted.split(".");
    const ciphertext = Buffer.from(parts[3], "base64url");
    ciphertext[0] ^= 1;
    parts[3] = ciphertext.toString("base64url");
    expect(() => decryptConfig(parts.join("."))).toThrow();
  });
});
