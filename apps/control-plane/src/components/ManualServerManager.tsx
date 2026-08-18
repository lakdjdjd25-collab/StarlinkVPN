"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";

type Preview = {
  protocol: string;
  host: string;
  port: number;
  transport: string;
  security: string;
  country: string;
  countryCode: string;
  flag: string;
  geoDetected: boolean;
};

type ManualServer = {
  id: string;
  displayName: string;
  host: string;
  port: number;
  displayCountry: string;
  countryCode: string | null;
  countryOverride: string | null;
  category: "UNLIMITED" | "LIMITED";
  subcategory: string | null;
  volumeBytes: string | null;
  accessTier: "STANDARD" | "VIP";
  enabled: boolean;
  countTraffic: boolean;
  sortOrder: number;
  flag: string;
  stats: {
    totalTraffic: string;
    sessions: string;
    uniqueUsers: string;
    lastUsed: string | null;
  };
};

type ApiEnvelope<T> = { data?: T; error?: { message?: string } };

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { cache: "no-store", ...init });
  const body = await response.json().catch(() => null) as ApiEnvelope<T> | null;
  if (!response.ok || !body?.data) throw new Error(body?.error?.message ?? "درخواست انجام نشد");
  return body.data;
}

function formatBytes(value: string): string {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let amount = bytes;
  let index = 0;
  while (amount >= 1024 && index < units.length - 1) {
    amount /= 1024;
    index += 1;
  }
  return `${amount.toFixed(index >= 3 ? 2 : 1)} ${units[index]}`;
}

function bytesToGbInput(value: string | null): string {
  if (!value) return "";
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return "";
  const gb = bytes / 1024 ** 3;
  return Number.isInteger(gb) ? String(gb) : gb.toFixed(2).replace(/\.?0+$/, "");
}

function optionalNumber(value: FormDataEntryValue | null): number | null {
  const raw = String(value ?? "").trim();
  if (!raw) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

export function ManualServerManager() {
  const [servers, setServers] = useState<ManualServer[]>([]);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [address, setAddress] = useState("");
  const [port, setPort] = useState("");
  const [countryOverride, setCountryOverride] = useState("");
  const [countryCode, setCountryCode] = useState("");
  const [category, setCategory] = useState<"UNLIMITED" | "LIMITED">("UNLIMITED");

  const reload = useCallback(async () => {
    try {
      setServers(await api<ManualServer[]>("/api/v1/admin/manual-servers"));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "دریافت سرورها ناموفق بود");
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  async function validate(form: HTMLFormElement): Promise<Preview | null> {
    const data = new FormData(form);
    const config = String(data.get("config") ?? "").trim();
    if (!config) {
      setError("ابتدا لینک VLESS را وارد کنید");
      return null;
    }
    setBusy(true);
    setError("");
    try {
      const result = await api<Preview>("/api/v1/admin/manual-servers/validate", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          config,
          countryOverride: String(data.get("countryOverride") ?? "").trim() || null,
          countryCodeOverride: String(data.get("countryCode") ?? "").trim() || null,
        }),
      });
      setPreview(result);
      setAddress((current) => current.trim() || result.host);
      setPort((current) => current.trim() || String(result.port));
      setCountryOverride((current) => current.trim() || (result.country === "Unknown" ? "" : result.country));
      setCountryCode((current) => current.trim() || (result.countryCode === "GLOBAL" || result.countryCode === "global" ? "" : result.countryCode));
      return result;
    } catch (cause) {
      setPreview(null);
      setError(cause instanceof Error ? cause.message : "بررسی لینک ناموفق بود");
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const checked = await validate(form);
    if (!checked) return;
    const data = new FormData(form);
    const selectedCategory = String(data.get("category")) as "UNLIMITED" | "LIMITED";
    const selectedAddress = String(data.get("host") ?? "").trim() || checked.host;
    const selectedPort = Number(String(data.get("port") ?? "").trim() || checked.port);
    const selectedCountry = String(data.get("countryOverride") ?? "").trim() || (checked.country === "Unknown" ? "" : checked.country);
    const selectedCountryCode = String(data.get("countryCode") ?? "").trim() || (checked.countryCode === "global" ? "" : checked.countryCode);
    setBusy(true);
    setError("");
    try {
      await api("/api/v1/admin/manual-servers", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          config: String(data.get("config")),
          displayName: String(data.get("displayName")),
          host: selectedAddress,
          port: selectedPort,
          category: selectedCategory,
          subcategory: String(data.get("subcategory") ?? "").trim(),
          volumeGb: optionalNumber(data.get("volumeGb")),
          accessTier: data.get("vip") === "on" ? "VIP" : "STANDARD",
          enabled: data.get("enabled") === "on",
          countTraffic: data.get("countTraffic") === "on",
          sortOrder: Number(data.get("sortOrder") || 0),
          countryOverride: selectedCountry,
          countryCode: selectedCountryCode || null,
        }),
      });
      form.reset();
      setPreview(null);
      setAddress("");
      setPort("");
      setCountryOverride("");
      setCountryCode("");
      setCategory("UNLIMITED");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "ذخیره سرور ناموفق بود");
    } finally {
      setBusy(false);
    }
  }

  async function update(event: FormEvent<HTMLFormElement>, server: ManualServer) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      const replacement = String(data.get("config") ?? "").trim();
      await api("/api/v1/admin/manual-servers", {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          id: server.id,
          displayName: String(data.get("displayName")),
          host: String(data.get("host") ?? "").trim(),
          port: Number(data.get("port")),
          category: String(data.get("category")),
          subcategory: String(data.get("subcategory") ?? "").trim(),
          volumeGb: optionalNumber(data.get("volumeGb")),
          accessTier: data.get("vip") === "on" ? "VIP" : "STANDARD",
          enabled: data.get("enabled") === "on",
          countTraffic: data.get("countTraffic") === "on",
          sortOrder: Number(data.get("sortOrder") || 0),
          countryOverride: String(data.get("countryOverride") ?? "").trim() || null,
          countryCode: String(data.get("countryCode") ?? "").trim() || null,
          ...(replacement ? { config: replacement } : {}),
        }),
      });
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "ویرایش سرور ناموفق بود");
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    if (!window.confirm("این سرور دستی حذف شود؟")) return;
    setBusy(true);
    setError("");
    try {
      await api("/api/v1/admin/manual-servers", {
        method: "DELETE",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ id }),
      });
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "حذف سرور ناموفق بود");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <section className="card section">
        <div className="section-title">
          <h2>افزودن سرور دستی</h2>
          <p>VLESS Config به‌صورت رمزگذاری‌شده نگهداری می‌شود؛ اطلاعات اتصال و دسته‌بندی را همین‌جا مدیریت کنید.</p>
        </div>
        <form onSubmit={create}>
          <div className="field">
            <label>VLESS Config</label>
            <textarea className="textarea" name="config" rows={5} dir="ltr" placeholder="vless://..." required onChange={() => setPreview(null)} />
          </div>
          <div className="form-grid">
            <div className="field"><label>نام سرور</label><input className="input" name="displayName" required /></div>
            <div className="field"><label>آدرس</label><input className="input" name="host" dir="ltr" value={address} onChange={(event) => setAddress(event.target.value)} placeholder="server.example.com" required /></div>
            <div className="field"><label>پورت</label><input className="input" name="port" type="number" min={1} max={65535} value={port} onChange={(event) => setPort(event.target.value)} required /></div>
            <div className="field"><label>کشور</label><input className="input" name="countryOverride" value={countryOverride} onChange={(event) => setCountryOverride(event.target.value)} placeholder="Germany" required /></div>
            <div className="field"><label>دسته‌بندی</label><select className="select" name="category" value={category} onChange={(event) => setCategory(event.target.value as "UNLIMITED" | "LIMITED")}><option value="UNLIMITED">Unlimited ∞</option><option value="LIMITED">Limited</option></select></div>
            <div className="field"><label>زیردسته‌بندی</label><input className="input" name="subcategory" placeholder="مثلاً Gaming" required /></div>
            <div className="field"><label>حجم سرور (GB)</label><input className="input" name="volumeGb" type="number" min="0.01" step="0.01" placeholder={category === "LIMITED" ? "مثلاً 50" : "برای Unlimited خالی"} required={category === "LIMITED"} disabled={category === "UNLIMITED"} /></div>
          </div>
          <details style={{ marginTop: 12 }}>
            <summary style={{ cursor: "pointer", color: "var(--muted)" }}>تنظیمات تکمیلی</summary>
            <div className="form-grid" style={{ marginTop: 10 }}>
              <div className="field"><label>کد کشور</label><input className="input" name="countryCode" maxLength={2} value={countryCode} onChange={(event) => setCountryCode(event.target.value)} placeholder="DE" dir="ltr" /></div>
              <div className="field"><label>ترتیب نمایش</label><input className="input" name="sortOrder" type="number" defaultValue={0} /></div>
            </div>
          </details>
          <div style={{ display: "flex", gap: 18, flexWrap: "wrap", marginTop: 12 }}>
            <label style={{ display: "flex", gap: 8, alignItems: "center" }}><input type="checkbox" name="vip" /> سرور VIP</label>
            <label style={{ display: "flex", gap: 8, alignItems: "center" }}><input type="checkbox" name="enabled" defaultChecked /> فعال</label>
            <label style={{ display: "flex", gap: 8, alignItems: "center" }}><input type="checkbox" name="countTraffic" defaultChecked /> کسر ترافیک از حجم کاربر</label>
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
            <button className="button secondary" type="button" disabled={busy} onClick={(event) => void validate(event.currentTarget.form!)}>بررسی و Preview</button>
            <button className="button" disabled={busy}>ذخیره سرور</button>
          </div>
          {preview ? <div className="card" style={{ padding: 14, marginTop: 14 }}>
            <strong>{preview.flag} {preview.country}</strong>
            <div dir="ltr" style={{ marginTop: 8, color: "var(--muted)" }}>
              Protocol: {preview.protocol} • Host: {preview.host}:{preview.port} • Transport: {preview.transport} • Security: {preview.security}
            </div>
          </div> : null}
          {error ? <p className="error">{error}</p> : null}
        </form>
      </section>

      <section className="card section">
        <div className="section-title"><h2>سرورهای دستی</h2><p>سرورها برای همه سرویس‌ها منتشر می‌شوند؛ VIP و محاسبه ترافیک از همین‌جا کنترل می‌شود.</p></div>
        {servers.length === 0 ? <div className="empty">هنوز سرور دستی ثبت نشده است.</div> : null}
        <div style={{ display: "grid", gap: 12 }}>
          {servers.map((server) => <form key={server.id} onSubmit={(event) => void update(event, server)} className="card" style={{ padding: 14 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
              <div><strong>{server.flag} {server.displayName}</strong><div dir="ltr" style={{ color: "var(--muted)", fontSize: 12 }}>{server.host}:{server.port}</div></div>
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                <span className="badge blue">{server.category === "LIMITED" ? "Limited" : "Unlimited ∞"}</span>
                {server.subcategory ? <span className="badge">{server.subcategory}</span> : null}
                {server.volumeBytes ? <span className="badge">{formatBytes(server.volumeBytes)}</span> : null}
                <span className="badge">{server.accessTier === "VIP" ? "VIP" : "Standard"}</span>
                <span className={server.enabled ? "badge green" : "badge red"}>{server.enabled ? "Active" : "Disabled"}</span>
                <span className={server.countTraffic ? "badge green" : "badge"}>{server.countTraffic ? "Traffic On" : "Traffic Off"}</span>
              </div>
            </div>
            <div className="form-grid" style={{ marginTop: 12 }}>
              <div className="field"><label>نام سرور</label><input className="input" name="displayName" defaultValue={server.displayName} required /></div>
              <div className="field"><label>آدرس</label><input className="input" name="host" defaultValue={server.host} dir="ltr" required /></div>
              <div className="field"><label>پورت</label><input className="input" name="port" type="number" min={1} max={65535} defaultValue={server.port} required /></div>
              <div className="field"><label>کشور</label><input className="input" name="countryOverride" defaultValue={server.countryOverride ?? server.displayCountry} required /></div>
              <div className="field"><label>دسته‌بندی</label><select className="select" name="category" defaultValue={server.category}><option value="UNLIMITED">Unlimited ∞</option><option value="LIMITED">Limited</option></select></div>
              <div className="field"><label>زیردسته‌بندی</label><input className="input" name="subcategory" defaultValue={server.subcategory ?? ""} required /></div>
              <div className="field"><label>حجم سرور (GB)</label><input className="input" name="volumeGb" type="number" min="0.01" step="0.01" defaultValue={bytesToGbInput(server.volumeBytes)} placeholder={server.category === "LIMITED" ? "لازم برای Limited" : "برای Unlimited خالی"} /></div>
              <div className="field"><label>کد کشور</label><input className="input" name="countryCode" defaultValue={server.countryCode ?? ""} maxLength={2} dir="ltr" /></div>
              <div className="field"><label>ترتیب</label><input className="input" name="sortOrder" type="number" defaultValue={server.sortOrder} /></div>
            </div>
            <div className="field"><label>جایگزینی VLESS (اختیاری)</label><textarea className="textarea" name="config" rows={2} dir="ltr" placeholder="برای نگه‌داشتن کانفیگ فعلی خالی بگذارید" /></div>
            <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "center" }}>
              <label><input type="checkbox" name="vip" defaultChecked={server.accessTier === "VIP"} /> VIP</label>
              <label><input type="checkbox" name="enabled" defaultChecked={server.enabled} /> فعال</label>
              <label><input type="checkbox" name="countTraffic" defaultChecked={server.countTraffic} /> کسر ترافیک</label>
              <span style={{ color: "var(--muted)", fontSize: 12 }}>ترافیک عبوری {formatBytes(server.stats.totalTraffic)} • {server.stats.sessions} Session • {server.stats.uniqueUsers} User</span>
              <button className="button secondary" disabled={busy}>ثبت تغییرات</button>
              <button className="button secondary" type="button" disabled={busy} onClick={() => void remove(server.id)}>حذف</button>
            </div>
          </form>)}
        </div>
      </section>
    </>
  );
}
