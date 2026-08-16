import { randomBytes, randomUUID } from "node:crypto";
import { writeFile } from "node:fs/promises";

const origin = new URL(
  process.env.PUBLIC_APP_URL || "https://control-plane-production-a517.up.railway.app",
).origin;
const cleanupPath = "/tmp/nimhub-e2e-cleanup.json";
const resultPath = "/tmp/nimhub-e2e-result.json";

const result = {
  productionCommit: "a666b00076f8c5f14cad1d66e79f8a5d42bce746",
  health: false,
  adminPage: false,
  profilesSelectable: false,
  profileCount: 0,
  profileKinds: [],
  generatedCredentials: false,
  qrGenerated: false,
  loginByLicense: false,
  loginByEmail: false,
  passwordChanged: false,
  quotaAndValidityUpdated: false,
  deviceCountUpdated: false,
  profileChanged: false,
  serverAssigned: false,
  blocked: false,
  blockedLoginsRejected: false,
  serverConfigLocked: false,
  unblocked: false,
  loginAfterUnblock: false,
  remoteCleanup: false,
  failure: null,
};

const cleanup = {
  serviceId: null,
  remoteUsername: null,
};

function ensure(condition, label) {
  if (!condition) throw new Error(label);
}

async function safeJson(response) {
  return response.json().catch(() => null);
}

async function request(path, init = {}) {
  return fetch(new URL(path, origin), {
    ...init,
    headers: {
      accept: "application/json",
      ...(init.headers || {}),
    },
    redirect: init.redirect || "follow",
    signal: AbortSignal.timeout(20_000),
  });
}

async function login(credential, password, installationId) {
  const response = await request("/api/v1/auth/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: credential,
      password,
      installationId,
      deviceName: "NimHUB Production E2E",
      appVersion: "2.6.12-e2e",
    }),
  });
  return { response, payload: await safeJson(response) };
}

function normalizePasarGuardBase(value) {
  const url = new URL(value.trim());
  url.username = "";
  url.password = "";
  url.search = "";
  url.hash = "";
  const dashboardIndex = url.pathname.toLowerCase().indexOf("/dashboard");
  if (dashboardIndex >= 0) url.pathname = url.pathname.slice(0, dashboardIndex) || "/";
  url.pathname = `${url.pathname.replace(/\/+$/, "")}/`;
  return url;
}

async function deleteRemoteUser(username) {
  const baseUrl = process.env.PASARGUARD_BASE_URL;
  const adminUsername = process.env.PASARGUARD_USERNAME;
  const adminPassword = process.env.PASARGUARD_PASSWORD;
  ensure(baseUrl && adminUsername && adminPassword, "pasarguard_cleanup_credentials_missing");
  const base = normalizePasarGuardBase(baseUrl);
  const tokenResponse = await fetch(new URL("api/admin/token", base), {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "password",
      username: adminUsername,
      password: adminPassword,
    }),
    signal: AbortSignal.timeout(20_000),
  });
  ensure(tokenResponse.ok, `pasarguard_cleanup_login_${tokenResponse.status}`);
  const tokenPayload = await safeJson(tokenResponse);
  const token = tokenPayload?.access_token;
  ensure(typeof token === "string" && token.length > 0, "pasarguard_cleanup_token_invalid");

  let deleted = false;
  for (let attempt = 0; attempt < 3 && !deleted; attempt += 1) {
    const response = await fetch(new URL(`api/user/${encodeURIComponent(username)}`, base), {
      method: "DELETE",
      headers: { authorization: `Bearer ${token}`, accept: "application/json" },
      signal: AbortSignal.timeout(20_000),
    }).catch(() => null);
    if (response && (response.ok || response.status === 404)) deleted = true;
  }
  ensure(deleted, "pasarguard_remote_cleanup_failed");
}

let adminCookie = null;
let initialPassword = null;
let currentPassword = null;
let managedEmail = null;
let license = null;
let serviceId = null;
let oldAccessToken = null;
let nodeId = null;

try {
  const health = await request("/api/v1/health");
  ensure(health.ok, `health_${health.status}`);
  result.health = true;

  const adminEmail = process.env.ADMIN_EMAIL;
  const adminPassword = process.env.ADMIN_PASSWORD;
  ensure(adminEmail && adminPassword, "admin_credentials_missing");
  const adminSession = await request("/api/v1/admin/session", {
    method: "POST",
    headers: {
      origin,
      "content-type": "application/json",
    },
    body: JSON.stringify({ email: adminEmail, password: adminPassword }),
  });
  ensure(adminSession.ok, `admin_login_${adminSession.status}`);
  const setCookies = typeof adminSession.headers.getSetCookie === "function"
    ? adminSession.headers.getSetCookie()
    : [];
  const rawCookie = setCookies[0] || adminSession.headers.get("set-cookie");
  ensure(rawCookie, "admin_cookie_missing");
  adminCookie = rawCookie.split(";")[0];

  const adminPage = await request("/admin/services", {
    headers: { cookie: adminCookie },
    redirect: "manual",
  });
  ensure(adminPage.status === 200, `admin_page_${adminPage.status}`);
  const adminHtml = await adminPage.text();
  ensure(adminHtml.includes("کاربران") || adminHtml.includes("مجوز"), "admin_page_content_missing");
  result.adminPage = true;

  const listResponse = await request("/api/v1/admin/licenses", {
    headers: { cookie: adminCookie },
  });
  ensure(listResponse.ok, `profiles_${listResponse.status}`);
  const listPayload = await safeJson(listResponse);
  const profiles = Array.isArray(listPayload?.data?.profiles) ? listPayload.data.profiles : [];
  ensure(profiles.length > 0, "no_pasarguard_profiles");
  result.profileCount = profiles.length;
  result.profileKinds = [...new Set(profiles.map((item) => item.kind).filter(Boolean))];
  result.profilesSelectable = profiles.every((item) => /^(template|group):[1-9]\d*$/.test(item.key));
  ensure(result.profilesSelectable, "invalid_pasarguard_profile_keys");

  const initialProfile = profiles[0];
  const updatedProfile = profiles.find((item) => item.key !== initialProfile.key) || initialProfile;
  const idempotencyKey = randomUUID();
  const testName = `NimHUB E2E ${Date.now()}`;
  const createResponse = await request("/api/v1/admin/licenses", {
    method: "POST",
    headers: {
      cookie: adminCookie,
      origin,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      idempotencyKey,
      customerName: testName,
      quotaGb: 1,
      days: 2,
      maxDevices: 2,
      profileKey: initialProfile.key,
      note: "Automated production E2E test record; safe to delete",
    }),
  });
  ensure(createResponse.status === 201, `create_${createResponse.status}`);
  const createdPayload = await safeJson(createResponse);
  const created = createdPayload?.data;
  ensure(created?.service?.id && created?.credentials?.email && created?.credentials?.initialPassword, "create_receipt_invalid");
  ensure(typeof created.license === "string" && created.license.length > 0, "generated_license_missing");
  ensure(typeof created.qrPayload === "string" && created.qrPayload.length > 0, "qr_missing");
  ensure(created.credentials.email.endsWith("@nimhub.com"), "managed_email_invalid");
  ensure(created.credentials.initialPassword.length === 8, "initial_password_length_invalid");

  serviceId = created.service.id;
  license = created.license;
  managedEmail = created.credentials.email;
  initialPassword = created.credentials.initialPassword;
  currentPassword = initialPassword;
  cleanup.serviceId = serviceId;
  cleanup.remoteUsername = created?.remoteUser?.username || null;
  await writeFile(cleanupPath, JSON.stringify(cleanup), { mode: 0o600 });
  result.generatedCredentials = true;
  result.qrGenerated = true;

  const licenseLogin = await login(license, "", `e2e-license-${randomUUID()}`);
  ensure(licenseLogin.response.ok, `license_login_${licenseLogin.response.status}`);
  ensure(typeof licenseLogin.payload?.data?.accessToken === "string", "license_access_token_missing");
  oldAccessToken = licenseLogin.payload.data.accessToken;
  result.loginByLicense = true;

  const emailLogin = await login(managedEmail, currentPassword, `e2e-email-${randomUUID()}`);
  ensure(emailLogin.response.ok, `email_login_${emailLogin.response.status}`);
  const emailAccessToken = emailLogin.payload?.data?.accessToken;
  ensure(typeof emailAccessToken === "string", "email_access_token_missing");
  result.loginByEmail = true;

  const bootstrap = await request("/api/v1/client/bootstrap", {
    headers: { authorization: `Bearer ${oldAccessToken}` },
  });
  ensure(bootstrap.ok, `bootstrap_${bootstrap.status}`);
  const bootstrapPayload = await safeJson(bootstrap);
  const managedService = bootstrapPayload?.data?.services?.find((item) => item.id === serviceId);
  ensure(managedService, "bootstrap_service_missing");
  ensure(Array.isArray(managedService.servers) && managedService.servers.length > 0, "managed_server_missing");
  nodeId = managedService.servers[0].id;
  result.serverAssigned = true;

  const newPassword = randomBytes(12).toString("base64url");
  const passwordChange = await request("/api/v1/client/account/password/change", {
    method: "POST",
    headers: {
      authorization: `Bearer ${emailAccessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ currentPassword, newPassword }),
  });
  ensure(passwordChange.ok, `password_change_${passwordChange.status}`);
  currentPassword = newPassword;
  const relogin = await login(managedEmail, currentPassword, `e2e-password-${randomUUID()}`);
  ensure(relogin.response.ok, `password_relogin_${relogin.response.status}`);
  result.passwordChanged = true;

  const updateResponse = await request("/api/v1/admin/licenses", {
    method: "PATCH",
    headers: {
      cookie: adminCookie,
      origin,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      action: "update",
      serviceId,
      status: "ACTIVE",
      quotaGb: 2,
      daysFromNow: 7,
      maxDevices: 3,
      profileKey: updatedProfile.key,
    }),
  });
  ensure(updateResponse.ok, `update_${updateResponse.status}`);

  const verifyUpdateResponse = await request("/api/v1/admin/licenses", {
    headers: { cookie: adminCookie },
  });
  ensure(verifyUpdateResponse.ok, `verify_update_${verifyUpdateResponse.status}`);
  const verifyUpdatePayload = await safeJson(verifyUpdateResponse);
  const updated = verifyUpdatePayload?.data?.licenses?.find((item) => item.id === serviceId);
  ensure(updated, "updated_license_missing");
  ensure(BigInt(String(updated.quotaBytes)) === 2n * 1024n * 1024n * 1024n, "quota_update_mismatch");
  ensure(new Date(updated.expiresAt).getTime() > Date.now() + 6 * 86_400_000, "validity_update_mismatch");
  ensure(updated.maxDevices === 3, "device_count_update_mismatch");
  ensure(updated.profileKey === updatedProfile.key, "profile_update_mismatch");
  result.quotaAndValidityUpdated = true;
  result.deviceCountUpdated = true;
  result.profileChanged = updatedProfile.key !== initialProfile.key;

  const blockResponse = await request("/api/v1/admin/licenses", {
    method: "PATCH",
    headers: {
      cookie: adminCookie,
      origin,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      action: "update",
      serviceId,
      status: "SUSPENDED",
      quotaGb: 2,
      daysFromNow: 7,
      maxDevices: 3,
      profileKey: updatedProfile.key,
    }),
  });
  ensure(blockResponse.ok, `block_${blockResponse.status}`);
  result.blocked = true;

  const blockedLicenseLogin = await login(license, "", `e2e-block-license-${randomUUID()}`);
  const blockedEmailLogin = await login(managedEmail, currentPassword, `e2e-block-email-${randomUUID()}`);
  ensure(!blockedLicenseLogin.response.ok && !blockedEmailLogin.response.ok, "blocked_login_allowed");
  result.blockedLoginsRejected = true;

  const lockedConfig = await request(`/api/v1/client/services/${encodeURIComponent(serviceId)}/config?nodeId=${encodeURIComponent(nodeId)}`, {
    headers: { authorization: `Bearer ${oldAccessToken}` },
  });
  ensure(!lockedConfig.ok, "blocked_config_still_available");
  result.serverConfigLocked = true;

  const unblockResponse = await request("/api/v1/admin/licenses", {
    method: "PATCH",
    headers: {
      cookie: adminCookie,
      origin,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      action: "update",
      serviceId,
      status: "ACTIVE",
      quotaGb: 2,
      daysFromNow: 7,
      maxDevices: 3,
      profileKey: updatedProfile.key,
    }),
  });
  ensure(unblockResponse.ok, `unblock_${unblockResponse.status}`);
  result.unblocked = true;

  const finalLicenseLogin = await login(license, "", `e2e-final-license-${randomUUID()}`);
  const finalEmailLogin = await login(managedEmail, currentPassword, `e2e-final-email-${randomUUID()}`);
  ensure(finalLicenseLogin.response.ok && finalEmailLogin.response.ok, "post_unblock_login_failed");
  result.loginAfterUnblock = true;
} catch (error) {
  result.failure = error instanceof Error ? error.message : "unknown_e2e_failure";
  process.exitCode = 1;
} finally {
  if (cleanup.remoteUsername) {
    try {
      await deleteRemoteUser(cleanup.remoteUsername);
      result.remoteCleanup = true;
    } catch {
      result.remoteCleanup = false;
      if (!result.failure) result.failure = "remote_cleanup_failed";
      process.exitCode = 1;
    }
  } else {
    result.remoteCleanup = true;
  }
  await writeFile(resultPath, JSON.stringify(result, null, 2), { mode: 0o600 });
  console.log(JSON.stringify(result));
}
