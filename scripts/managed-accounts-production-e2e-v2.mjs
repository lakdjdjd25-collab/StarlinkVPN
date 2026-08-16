import { randomBytes, randomUUID } from "node:crypto";
import { writeFile } from "node:fs/promises";

const origin = new URL(process.env.PUBLIC_APP_URL || "https://control-plane-production-a517.up.railway.app").origin;
const resultPath = "/tmp/nimhub-e2e-result.json";
const result = {
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
  serverGroupChanged: false,
  serverAssigned: false,
  blocked: false,
  blockedLoginsRejected: false,
  serverConfigLocked: false,
  unblocked: false,
  loginAfterUnblock: false,
  cleanup: false,
  failure: null,
};

let adminCookie = "";
let serviceId = "";
let managedEmail = "";
let currentPassword = "";
let license = "";
let nodeId = "";
let oldAccessToken = "";

function ensure(value, label) {
  if (!value) throw new Error(label);
}

async function json(response) {
  return response.json().catch(() => null);
}

async function req(path, init = {}) {
  return fetch(new URL(path, origin), {
    ...init,
    headers: { accept: "application/json", ...(init.headers || {}) },
    signal: AbortSignal.timeout(25_000),
  });
}

async function login(credential, password, tag) {
  const response = await req("/api/v1/auth/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: credential,
      password,
      installationId: `e2e-${tag}-${randomUUID()}`,
      deviceName: "NimHUB Production E2E",
      appVersion: "2.6.12-e2e",
    }),
  });
  return { response, body: await json(response) };
}

const groupsKey = (profile) => [...(profile.groupIds || [])].sort((a, b) => a - b).join(",");

try {
  const health = await req("/api/v1/health");
  ensure(health.ok, `health_${health.status}`);
  result.health = true;

  ensure(process.env.ADMIN_EMAIL && process.env.ADMIN_PASSWORD, "admin_credentials_missing");
  const adminLogin = await req("/api/v1/admin/session", {
    method: "POST",
    headers: { origin, "content-type": "application/json" },
    body: JSON.stringify({ email: process.env.ADMIN_EMAIL, password: process.env.ADMIN_PASSWORD }),
  });
  ensure(adminLogin.ok, `admin_login_${adminLogin.status}`);
  const cookieHeader = typeof adminLogin.headers.getSetCookie === "function"
    ? adminLogin.headers.getSetCookie()[0]
    : adminLogin.headers.get("set-cookie");
  ensure(cookieHeader, "admin_cookie_missing");
  adminCookie = cookieHeader.split(";")[0];

  const adminPage = await req("/admin/services", { headers: { cookie: adminCookie }, redirect: "manual" });
  ensure(adminPage.status === 200, `admin_page_${adminPage.status}`);
  result.adminPage = true;

  const list = await req("/api/v1/admin/licenses", { headers: { cookie: adminCookie } });
  ensure(list.ok, `profiles_${list.status}`);
  const listBody = await json(list);
  const profiles = Array.isArray(listBody?.data?.profiles) ? listBody.data.profiles : [];
  ensure(profiles.length > 0, "no_pasarguard_profiles");
  ensure(profiles.every((p) => /^(template|group):[1-9]\d*$/.test(p.key)), "profile_key_invalid");
  result.profileCount = profiles.length;
  result.profileKinds = [...new Set(profiles.map((p) => p.kind).filter(Boolean))];
  result.profilesSelectable = true;

  const initialProfile = profiles[0];
  const changedProfile = profiles.find((p) => p.key !== initialProfile.key && groupsKey(p) !== groupsKey(initialProfile));
  ensure(changedProfile, "no_distinct_server_group_available");

  const createdResponse = await req("/api/v1/admin/licenses", {
    method: "POST",
    headers: { cookie: adminCookie, origin, "content-type": "application/json" },
    body: JSON.stringify({
      idempotencyKey: randomUUID(),
      customerName: `NimHUB E2E ${Date.now()}`,
      quotaGb: 1,
      days: 2,
      maxDevices: 2,
      profileKey: initialProfile.key,
      note: "Automated production E2E; delete after validation",
    }),
  });
  ensure(createdResponse.status === 201, `create_${createdResponse.status}`);
  const created = (await json(createdResponse))?.data;
  ensure(created?.service?.id, "service_missing");
  ensure(created?.credentials?.email?.endsWith("@nimhub.com"), "managed_email_invalid");
  ensure(created?.credentials?.initialPassword?.length === 8, "initial_password_invalid");
  ensure(typeof created?.license === "string" && created.license.length > 0, "license_missing");
  ensure(typeof created?.qrPayload === "string" && created.qrPayload.length > 0, "qr_missing");
  serviceId = created.service.id;
  managedEmail = created.credentials.email;
  currentPassword = created.credentials.initialPassword;
  license = created.license;
  result.generatedCredentials = true;
  result.qrGenerated = true;

  const byLicense = await login(license, "", "license");
  ensure(byLicense.response.ok, `license_login_${byLicense.response.status}`);
  oldAccessToken = byLicense.body?.data?.accessToken || "";
  ensure(oldAccessToken, "license_access_missing");
  result.loginByLicense = true;

  const byEmail = await login(managedEmail, currentPassword, "email");
  ensure(byEmail.response.ok, `email_login_${byEmail.response.status}`);
  const emailAccess = byEmail.body?.data?.accessToken || "";
  ensure(emailAccess, "email_access_missing");
  result.loginByEmail = true;

  const bootstrap = await req("/api/v1/client/bootstrap", { headers: { authorization: `Bearer ${oldAccessToken}` } });
  ensure(bootstrap.ok, `bootstrap_${bootstrap.status}`);
  const service = (await json(bootstrap))?.data?.services?.find((s) => s.id === serviceId);
  ensure(service?.servers?.length > 0, "server_not_assigned");
  nodeId = service.servers[0].id;
  result.serverAssigned = true;

  const nextPassword = randomBytes(18).toString("base64url");
  const changedPassword = await req("/api/v1/client/account/password/change", {
    method: "POST",
    headers: { authorization: `Bearer ${emailAccess}`, "content-type": "application/json" },
    body: JSON.stringify({ currentPassword, newPassword: nextPassword }),
  });
  ensure(changedPassword.ok, `password_change_${changedPassword.status}`);
  currentPassword = nextPassword;
  const passwordRelogin = await login(managedEmail, currentPassword, "password");
  ensure(passwordRelogin.response.ok, `password_relogin_${passwordRelogin.response.status}`);
  result.passwordChanged = true;

  const update = await req("/api/v1/admin/licenses", {
    method: "PATCH",
    headers: { cookie: adminCookie, origin, "content-type": "application/json" },
    body: JSON.stringify({
      action: "update",
      serviceId,
      status: "ACTIVE",
      quotaGb: 2,
      daysFromNow: 7,
      maxDevices: 3,
      profileKey: changedProfile.key,
    }),
  });
  ensure(update.ok, `update_${update.status}`);

  const afterUpdate = await req("/api/v1/admin/licenses", { headers: { cookie: adminCookie } });
  ensure(afterUpdate.ok, `verify_update_${afterUpdate.status}`);
  const updated = (await json(afterUpdate))?.data?.licenses?.find((item) => item.id === serviceId);
  ensure(updated, "updated_service_missing");
  ensure(BigInt(String(updated.quotaBytes)) === 2n * 1024n * 1024n * 1024n, "quota_mismatch");
  ensure(new Date(updated.expiresAt).getTime() > Date.now() + 6 * 86_400_000, "validity_mismatch");
  ensure(updated.maxDevices === 3, "device_limit_mismatch");
  ensure(updated.profileKey === changedProfile.key, "server_group_mismatch");
  result.quotaAndValidityUpdated = true;
  result.deviceCountUpdated = true;
  result.serverGroupChanged = true;

  const block = await req("/api/v1/admin/licenses", {
    method: "PATCH",
    headers: { cookie: adminCookie, origin, "content-type": "application/json" },
    body: JSON.stringify({
      action: "update",
      serviceId,
      status: "SUSPENDED",
      quotaGb: 2,
      daysFromNow: 7,
      maxDevices: 3,
      profileKey: changedProfile.key,
    }),
  });
  ensure(block.ok, `block_${block.status}`);
  result.blocked = true;

  const blockedLicense = await login(license, "", "blocked-license");
  const blockedEmail = await login(managedEmail, currentPassword, "blocked-email");
  ensure(!blockedLicense.response.ok && !blockedEmail.response.ok, "blocked_login_allowed");
  result.blockedLoginsRejected = true;

  const lockedConfig = await req(`/api/v1/client/services/${encodeURIComponent(serviceId)}/config?nodeId=${encodeURIComponent(nodeId)}`, {
    headers: { authorization: `Bearer ${oldAccessToken}` },
  });
  ensure(!lockedConfig.ok, "blocked_config_available");
  result.serverConfigLocked = true;

  const unblock = await req("/api/v1/admin/licenses", {
    method: "PATCH",
    headers: { cookie: adminCookie, origin, "content-type": "application/json" },
    body: JSON.stringify({
      action: "update",
      serviceId,
      status: "ACTIVE",
      quotaGb: 2,
      daysFromNow: 7,
      maxDevices: 3,
      profileKey: changedProfile.key,
    }),
  });
  ensure(unblock.ok, `unblock_${unblock.status}`);
  result.unblocked = true;

  const finalLicense = await login(license, "", "final-license");
  const finalEmail = await login(managedEmail, currentPassword, "final-email");
  ensure(finalLicense.response.ok && finalEmail.response.ok, "post_unblock_login_failed");
  result.loginAfterUnblock = true;
} catch (error) {
  result.failure = error instanceof Error ? error.message : "unknown_failure";
  process.exitCode = 1;
} finally {
  if (serviceId && adminCookie) {
    const cleanup = await req("/api/v1/admin/licenses/delete", {
      method: "DELETE",
      headers: { cookie: adminCookie, origin, "content-type": "application/json" },
      body: JSON.stringify({ serviceId }),
    }).catch(() => null);
    if (cleanup?.ok) {
      const verify = await req("/api/v1/admin/licenses", { headers: { cookie: adminCookie } }).catch(() => null);
      if (verify?.ok) {
        const body = await json(verify);
        result.cleanup = !body?.data?.licenses?.some((item) => item.id === serviceId);
      }
    }
    if (!result.cleanup) {
      result.failure ||= "cleanup_failed";
      process.exitCode = 1;
    }
  } else {
    result.cleanup = true;
  }
  await writeFile(resultPath, JSON.stringify(result, null, 2), { mode: 0o600 });
  console.log(JSON.stringify(result));
}
