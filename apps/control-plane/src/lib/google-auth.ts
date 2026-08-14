import { createRemoteJWKSet, jwtVerify } from "jose";

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
const GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"];
const GOOGLE_WEB_CLIENT_SUFFIX = ".apps.googleusercontent.com";

export type GoogleIdentity = {
  subject: string;
  email: string;
};

export class GoogleAuthError extends Error {
  constructor(
    public readonly code: "not_configured" | "invalid_token",
    message: string,
  ) {
    super(message);
    this.name = "GoogleAuthError";
  }
}

export function googleWebClientId(): string {
  const value = process.env.GOOGLE_WEB_CLIENT_ID?.trim();
  if (!value) {
    throw new GoogleAuthError("not_configured", "شناسه OAuth گوگل روی سرور تنظیم نشده است");
  }
  if (
    value.length < 20 ||
    value.length > 512 ||
    !value.toLowerCase().endsWith(GOOGLE_WEB_CLIENT_SUFFIX) ||
    /\s/.test(value)
  ) {
    throw new GoogleAuthError("not_configured", "شناسه Web OAuth گوگل روی سرور معتبر نیست");
  }
  return value;
}

export async function verifyGoogleIdToken(idToken: string, expectedNonce: string): Promise<GoogleIdentity> {
  const audience = googleWebClientId();
  try {
    const { payload } = await jwtVerify(idToken, GOOGLE_JWKS, {
      audience,
      issuer: GOOGLE_ISSUERS,
    });
    const subject = typeof payload.sub === "string" ? payload.sub : "";
    const email = typeof payload.email === "string" ? payload.email.trim().toLowerCase() : "";
    if (!subject || subject.length > 255 || !email || payload.email_verified !== true) {
      throw new GoogleAuthError("invalid_token", "هویت حساب گوگل کامل یا تأییدشده نیست");
    }
    if (payload.nonce !== expectedNonce) {
      throw new GoogleAuthError("invalid_token", "درخواست ورود گوگل معتبر نیست");
    }
    return { subject, email };
  } catch (error) {
    if (error instanceof GoogleAuthError) throw error;
    throw new GoogleAuthError("invalid_token", "توکن ورود گوگل معتبر یا قابل تأیید نیست");
  }
}
