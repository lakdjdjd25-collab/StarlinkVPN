const base = process.env.PASARGUARD_BASE_URL?.trim();
const username = process.env.PASARGUARD_USERNAME?.trim();
const password = process.env.PASARGUARD_PASSWORD;

if (!base || !username || !password) {
  throw new Error("PasarGuard environment is not configured");
}

const root = new URL(base);
const dashboardIndex = root.pathname.toLowerCase().indexOf("/dashboard");
if (dashboardIndex >= 0) root.pathname = root.pathname.slice(0, dashboardIndex) || "/";
root.pathname = `${root.pathname.replace(/\/+$/, "")}/`;
root.search = "";
root.hash = "";

const endpoint = (path: string) => new URL(path.replace(/^\//, ""), root);

const tokenBody = new URLSearchParams({
  grant_type: "password",
  username,
  password,
});
const tokenResponse = await fetch(endpoint("api/admin/token"), {
  method: "POST",
  headers: { "content-type": "application/x-www-form-urlencoded" },
  body: tokenBody,
});
if (!tokenResponse.ok) throw new Error(`PasarGuard token request failed: ${tokenResponse.status}`);
const tokenJson = await tokenResponse.json() as { access_token?: unknown };
if (typeof tokenJson.access_token !== "string" || !tokenJson.access_token) {
  throw new Error("PasarGuard token response is invalid");
}

const templatesResponse = await fetch(endpoint("api/user_templates"), {
  headers: {
    authorization: `Bearer ${tokenJson.access_token}`,
    accept: "application/json",
  },
});
if (!templatesResponse.ok) throw new Error(`PasarGuard templates request failed: ${templatesResponse.status}`);
const raw = await templatesResponse.json() as unknown;
const items = Array.isArray(raw)
  ? raw
  : (raw && typeof raw === "object" && Array.isArray((raw as { user_templates?: unknown }).user_templates))
      ? (raw as { user_templates: unknown[] }).user_templates
      : (raw && typeof raw === "object" && Array.isArray((raw as { templates?: unknown }).templates))
          ? (raw as { templates: unknown[] }).templates
          : [];

const safe = items.map((item) => {
  const value = item && typeof item === "object" ? item as Record<string, unknown> : {};
  return {
    id: value.id,
    name: value.name,
    data_limit: value.data_limit,
    expire_duration: value.expire_duration,
    is_disabled: value.is_disabled,
    group_ids: Array.isArray(value.group_ids) ? value.group_ids : [],
  };
});

console.log("PASARGUARD_TEMPLATE_DIAGNOSTIC_BEGIN");
console.log(JSON.stringify(safe));
console.log("PASARGUARD_TEMPLATE_DIAGNOSTIC_END");
