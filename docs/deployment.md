# Deployment

## Railway topology

Create one Railway project with two services:

1. PostgreSQL
2. `quickping-control-plane`, built from this repository root

The included `railway.toml` runs migrations before starting the Next.js
standalone server and checks `/api/v1/health`.

## Required variables

| Key | Scope | Secret | Purpose |
| --- | --- | --- | --- |
| `DATABASE_URL` | runtime/build migration | yes | Railway PostgreSQL connection string |
| `JWT_SECRET` | runtime | yes | at least 32 random characters |
| `OTP_HASH_SECRET` | runtime | yes | HMAC key for one-time login codes; 32+ random characters |
| `CONFIG_ENCRYPTION_KEY` | runtime | yes | base64 encoding of exactly 32 random bytes |
| `ADMIN_EMAIL` | one-time seed | no | first administrator login |
| `ADMIN_PASSWORD` | one-time seed | yes | first administrator password, 12+ chars |
| `PUBLIC_APP_URL` | runtime | no | canonical HTTPS URL of the panel |
| `RESEND_API_KEY` | runtime | yes | transactional email provider credential |
| `AUTH_FROM_EMAIL` | runtime | no | verified sender for login codes |
| `SIGNUP_ENABLED` | runtime | no | whether a new email may create a customer account |

Generate secrets locally; never commit them. After PostgreSQL is available,
run the seed command once in a Railway shell. Remove `ADMIN_PASSWORD` from the
service variables after the administrator has been created.

Before enabling email login, verify the sender domain with the configured mail
provider. Development mode exposes the generated code only in the local API
response when mail variables are absent; production never does so.

## Android release

- Set `QUICKPING_API_BASE_URL` to the deployed HTTPS origin.
- Create a new upload/signing key if the former key cannot be recovered.
- Keep the package `org.quickping` only when the Play signing identity is under
  the owner's control. Otherwise use a new application id before publishing.
- Runtime VPN node material is obtained through short-lived authenticated API
  calls; it must never be committed to the Android source.
