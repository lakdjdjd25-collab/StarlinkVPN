import { afterEach, describe, expect, it } from "vitest";
import { GoogleAuthError, googleWebClientId } from "./google-auth";

afterEach(() => {
  delete process.env.GOOGLE_WEB_CLIENT_ID;
});

describe("Google OAuth configuration", () => {
  it("rejects a missing web client ID", () => {
    expect(() => googleWebClientId()).toThrowError(GoogleAuthError);
  });

  it("rejects malformed or non-Google client IDs", () => {
    process.env.GOOGLE_WEB_CLIENT_ID = "not-a-google-client-id";
    expect(() => googleWebClientId()).toThrowError(GoogleAuthError);

    process.env.GOOGLE_WEB_CLIENT_ID = "12345.apps.googleusercontent.com with-space";
    expect(() => googleWebClientId()).toThrowError(GoogleAuthError);
  });

  it("returns a normalized valid Google web client ID", () => {
    process.env.GOOGLE_WEB_CLIENT_ID = "  123456789012-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com  ";
    expect(googleWebClientId()).toBe(
      "123456789012-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com",
    );
  });
});
