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
  category: "UNLIMITED" | "GAMING";
  accessTier: "STANDARD" | "VIP";
  enabled: boolean;
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

export function ManualServerManager() {
  const [servers, setServers] = useState<ManualServer[]>([]);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

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
    setBusy(true);
    setError("");
    try {
      await api("/api/v1/admin/manual-servers", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          config: String(data.get("config")),
          displayName: String(data.get("displayName")),
          category: String(data.get("category")),
          accessTier: data.get("vip") === "on" ? "VIP" : "STANDARD",
          enabled: data.get("enabled") === "on",
          sortOrder: Number(data.get("sortOrder") || 0),
          countryOverride: String(data.get("countryOverride") ?? "").trim() || null,
          countryCode: String(data.get("countryCode") ?? "").trim() || null,
        }),
      });
      form.reset();
      setPreview(null);
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
          category: String(data.get("category")),
          accessTier: data.get("vip") === "on" ? "VIP" : "STANDARD",
          enabled: data.get("enabled") === "on",
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
          <p>لینک VLESS رمزگذاری می‌شود و فقط بعد از Authorization به Client مجاز تحویل داده می‌شود.</p>
        </div>
        <form onSubmit={create}>
          <div className="field">
            <label>VLESS Config</label>
            <textarea className="textarea" name="config" rows={5} dir="ltr" placeholder="vless://..." required onChange={() => setPreview(null)} />
          </div>
          <div className="form-grid">
            <div className="field"><label>Display Name</label><input className="input" name="displayName" required /></div>
            <div className="field"><label>Category</label><select className="select" name="category" defaultValue="UNLIMITED"><option value="UNLIMITED">Unlimited ∞</option><option value="GAMING">Gaming 🎮</option></select></div>
            <div className="field"><label>Sort Order</label><input className="input" name="sortOrder" type="number" defaultValue={0} /></div>
          </div>
          <details style={{ marginTop: 12 }}>
            <summary style={{ cursor: "pointer", color: "var(--muted)" }}>Advanced Settings</summary>
            <div className="form-grid" style={{ marginTop: 10 }}>
              <div className="field"><label>Country Override</label><input className="input" name="countryOverride" placeholder="Germany" /></div>
              <div className="field"><label>Country Code</label><input className="input" name="countryCode" maxLength={2} placeholder="DE" dir="ltr" /></div>
            </div>
          </details>
          <div style={{ display: "flex", gap: 18, flexWrap: "wrap", marginTop: 12 }}>
            <label style={{ display: "flex", gap: 8, alignItems: "center" }}><input type="checkbox" name="vip" /> سرور VIP</label>
            <label style={{ display: "flex", gap: 8, alignItems: "center" }}><input type="checkbox" name="enabled" defaultChecked /> فعال</label>
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
        <div className="section-title"><h2>سرورهای دستی</h2><p>Manual / Shared Config — Unlimited فقط دسته است و حجم همچنان محاسبه می‌شود.</p></div>
        {servers.length === 0 ? <div className="empty">هنوز سرور دستی ثبت نشده است.</div> : null}
        <div style={{ display: "grid", gap: 12 }}>
          {servers.map((server) => <form key={server.id} onSubmit={(event) => void update(event, server)} className="card" style={{ padding: 14 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
              <div><strong>{server.flag} {server.displayName}</strong><div dir="ltr" style={{ color: "var(--muted)", fontSize: 12 }}>{server.host}:{server.port}</div></div>
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                <span className="badge blue">{server.category === "GAMING" ? "Gaming 🎮" : "Unlimited ∞"}</span>
                <span className="badge">{server.accessTier === "VIP" ? "VIP" : "Public"}</span>
                <span className={server.enabled ? "badge green" : "badge red"}>{server.enabled ? "Active" : "Disabled"}</span>
              </div>
            </div>
            <div className="form-grid" style={{ marginTop: 12 }}>
              <div className="field"><label>نام</label><input className="input" name="displayName" defaultValue={server.displayName} required /></div>
              <div className="field"><label>دسته</label><select className="select" name="category" defaultValue={server.category}><option value="UNLIMITED">Unlimited ∞</option><option value="GAMING">Gaming 🎮</option></select></div>
              <div className="field"><label>ترتیب</label><input className="input" name="sortOrder" type="number" defaultValue={server.sortOrder} /></div>
              <div className="field"><label>Country Override</label><input className="input" name="countryOverride" defaultValue={server.countryOverride ?? ""} /></div>
              <div className="field"><label>Country Code</label><input className="input" name="countryCode" defaultValue={server.countryCode ?? ""} maxLength={2} dir="ltr" /></div>
            </div>
            <div className="field"><label>Replace VLESS (اختیاری)</label><textarea className="textarea" name="config" rows={2} dir="ltr" placeholder="برای نگه‌داشتن کانفیگ فعلی خالی بگذارید" /></div>
            <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "center" }}>
              <label><input type="checkbox" name="vip" defaultChecked={server.accessTier === "VIP"} /> VIP</label>
              <label><input type="checkbox" name="enabled" defaultChecked={server.enabled} /> فعال</label>
              <span style={{ color: "var(--muted)", fontSize: 12 }}>مصرف {formatBytes(server.stats.totalTraffic)} • {server.stats.sessions} Session • {server.stats.uniqueUsers} User</span>
              <button className="button secondary" disabled={busy}>ثبت تغییرات</button>
              <button className="button secondary" type="button" disabled={busy} onClick={() => void remove(server.id)}>حذف</button>
            </div>
          </form>)}
        </div>
      </section>
    </>
  );
}
