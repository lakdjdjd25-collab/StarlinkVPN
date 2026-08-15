import { beforeAll, describe, expect, it } from "vitest";
import {
  createAuthCode,
  createOpaqueToken,
  hashAuthCode,
  hashPassword,
  issueToken,
  opaqueTokenServiceId,
  verifyAuthCode,
  verifyPassword,
  verifyToken,
} from "./auth";

beforeAll(() => {
  process.env.JWT_SECRET = "a-secure-test-secret-with-more-than-thirty-two-characters";
});

describe("authentication primitives", () => {
  it("hashes and verifies passwords", async () => {
    const value = await hashPassword("correct horse battery staple");
    expect(value).not.toContain("correct horse");
    await expect(verifyPassword("correct horse battery staple", value)).resolves.toBe(true);
    await expect(verifyPassword("wrong", value)).resolves.toBe(false);
  });

  it("issues service-scoped access tokens without changing normal account tokens", async () => {
    const accountToken = await issueToken("user-1", "CUSTOMER", "access");
    const accountClaims = await verifyToken(accountToken, "access");
    expect(accountClaims.sub).toBe("user-1");
    expect(accountClaims.role).toBe("CUSTOMER");
    expect(accountClaims.serviceId).toBeUndefined();

    const licenseToken = await issueToken("user-1", "CUSTOMER", "access", "service-123");
    const licenseClaims = await verifyToken(licenseToken, "access");
    expect(licenseClaims.serviceId).toBe("service-123");
    await expect(verifyToken(licenseToken, "admin")).rejects.toThrow();
  });

  it("carries a license service scope through opaque refresh tokens", () => {
    const account = createOpaqueToken();
    expect(opaqueTokenServiceId(account.raw)).toBeNull();

    const licensed = createOpaqueToken("service_ABC-123");
    expect(opaqueTokenServiceId(licensed.raw)).toBe("service_ABC-123");
    expect(licensed.raw).not.toContain(licensed.hash);
  });

  it("creates and verifies challenge-bound one-time codes", () => {
    const code = createAuthCode();
    expect(code).toMatch(/^\d{6}$/);
    const digest = hashAuthCode("challenge-1", code);
    expect(verifyAuthCode("challenge-1", code, digest)).toBe(true);
    expect(verifyAuthCode("challenge-2", code, digest)).toBe(false);
    expect(verifyAuthCode("challenge-1", "000000", digest)).toBe(code === "000000");
  });
});
