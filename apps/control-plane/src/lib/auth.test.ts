import { beforeAll, describe, expect, it } from "vitest";
import {
  createAuthCode,
  hashAuthCode,
  hashPassword,
  issueToken,
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

  it("issues scoped access tokens", async () => {
    const token = await issueToken("user-1", "CUSTOMER", "access");
    const claims = await verifyToken(token, "access");
    expect(claims.sub).toBe("user-1");
    expect(claims.role).toBe("CUSTOMER");
    await expect(verifyToken(token, "admin")).rejects.toThrow();
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
