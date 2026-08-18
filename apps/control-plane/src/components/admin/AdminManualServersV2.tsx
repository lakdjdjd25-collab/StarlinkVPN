"use client";

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AdminIcon } from "./AdminIcon";

type Preview = {
  protocol: string;
  host: string;
  port: number;
  transport: string;
  security: string;
  country: string;
  countryCode: string;
  flag: string;
  fragment: string | null;
  suggestedName: string;
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

const SUBCATEGORY_OPTIONS = [
  { value: "GENERAL", label: "عمومی" },
  { value: "GAMING", label: "Gaming" },
  { value: "STREAMING", label: "Streaming" },
] as const;

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { cache: "no-store", ...init });
  const body = await response.json().catch(() => null) as ApiEnvelope<T> | null;
  if (!response.ok || body?.data === undefined) {
    throw new Error(body?.error?.message ?? "درخواست انجام نشد");
  }
  return body.data;
}

async function validateManualConfig(
  config: string,
  countryOverride?: string | null,
  countryCodeOverride?: string | null,
): Promise<Preview> {
  return api<Preview>("/api/v1/admin/manual-servers/validate", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      config,
      countryOverride: countryOverride?.trim() || null,
      countryCodeOverride: countryCodeOverride?.trim() || null,
    }),
  });
}

function formatNumber(value: number | string): string {
  const number = Number(value);
  return Number.isFinite(number) ? new Intl.NumberFormat("fa-IR").format(number) : "—";
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
  return `${new Intl.NumberFormat("fa-IR", { maximumFractionDigits: index >= 3 ? 1 : 0 }).format(amount)} ${units[index]}`;
}

function formatDateTime(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("fa-IR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function bytesToGbInput(value: string | null): string {
  if (!value) return "";
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return "";
  return String(Math.round(bytes / 1024 ** 3 * 100) / 100);
}

function optionalNumber(value: FormDataEntryValue | null): number | null {
  const raw = String(value ?? "").trim();
  if (!raw) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

function subcategoryLabel(value: string | null): string {
  const normalized = value?.trim().toUpperCase() || "GENERAL";
  return SUBCATEGORY_OPTIONS.find((item) => item.value === normalized)?.label ?? value ?? "عمومی";
}

function SubcategorySelect({ defaultValue }: { defaultValue?: string | null }) {
  const normalized = defaultValue?.trim().toUpperCase() || "GENERAL";
  const known = SUBCATEGORY_OPTIONS.some((item) => item.value === normalized);
  return (
    <select className="select" name="subcategory" defaultValue={normalized}>
      {!known ? <option value={normalized}>{defaultValue}</option> : null}
      {SUBCATEGORY_OPTIONS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
    </select>
  );
}

export function AdminManualServersV2() {
  const [servers, setServers] = useState<ManualServer[]>([]);
  const [query, setQuery] = useState("");
  const [tier, setTier] = useState("ALL");
  const [categoryFilter, setCategoryFilter] = useState("ALL");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [configInput, setConfigInput] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [address, setAddress] = useState("");
  const [port, setPort] = useState("");
  const [countryOverride, setCountryOverride] = useState("");
  const [countryCode, setCountryCode] = useState("");
  const [autoImporting, setAutoImporting] = useState(false);
  const [createCategory, setCreateCategory] = useState<"UNLIMITED" | "LIMITED">("UNLIMITED");
  const createNameTouched = useRef(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setServers(await api<ManualServer[]>("/api/v1/admin/manual-servers"));
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "دریافت سرورها ناموفق بود");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  useEffect(() => {
    const config = configInput.trim();
    if (!config) {
      setPreview(null);
      setAutoImporting(false);
      return;
    }
    if (!config.toLowerCase().startsWith("vless://")) {
      setPreview(null);
      setAutoImporting(false);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setAutoImporting(true);
      void validateManualConfig(config)
        .then((result) => {
          if (cancelled) return;
          setPreview(result);
          setAddress(result.host);
          setPort(String(result.port));
          setCountryOverride(result.country === "Unknown" ? "" : result.country);
          setCountryCode(result.countryCode.toLowerCase() === "global" ? "" : result.countryCode);
          if (!createNameTouched.current) setDisplayName(result.suggestedName);
          setError("");
        })
        .catch((cause) => {
          if (cancelled) return;
          setPreview(null);
          setError(cause instanceof Error ? cause.message : "خواندن لینک VLESS ناموفق بود");
        })
        .finally(() => {
          if (!cancelled) setAutoImporting(false);
        });
    }, 400);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [configInput]);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return servers.filter((server) => {
      if (tier !== "ALL" && server.accessTier !== tier) return false;
      if (categoryFilter !== "ALL" && server.category !== categoryFilter) return false;
      if (!normalized) return true;
      return `${server.displayName} ${server.host} ${server.displayCountry} ${server.countryCode ?? ""} ${server.subcategory ?? ""}`.toLowerCase().includes(normalized);
    });
  }, [servers, query, tier, categoryFilter]);

  const selected = useMemo(() => servers.find((server) => server.id === editingId) ?? null, [servers, editingId]);

  const summary = useMemo(() => {
    let traffic = 0n;
    for (const server of servers) {
      try { traffic += BigInt(server.stats.totalTraffic || "0"); } catch { /* invalid legacy stat */ }
    }
    return {
      enabled: servers.filter((server) => server.enabled).length,
      vip: servers.filter((server) => server.accessTier === "VIP").length,
      limited: servers.filter((server) => server.category === "LIMITED").length,
      traffic: traffic.toString(),
    };
  }, [servers]);

  function applyCreateImport(result: Preview) {
    setPreview(result);
    setAddress(result.host);
    setPort(String(result.port));
    setCountryOverride(result.country === "Unknown" ? "" : result.country);
    setCountryCode(result.countryCode.toLowerCase() === "global" ? "" : result.countryCode);
    if (!createNameTouched.current) setDisplayName(result.suggestedName);
  }

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
      const result = await validateManualConfig(
        config,
        String(data.get("countryOverride") ?? "").trim() || null,
        String(data.get("countryCode") ?? "").trim() || null,
      );
      applyCreateImport(result);
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
    const category = String(data.get("category")) as "UNLIMITED" | "LIMITED";
    setBusy(true);
    setError("");
    try {
      await api("/api/v1/admin/manual-servers", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          config: String(data.get("config")),
          displayName: String(data.get("displayName") || checked.suggestedName),
          host: String(data.get("host") ?? "").trim() || checked.host,
          port: Number(String(data.get("port") ?? "").trim() || checked.port),
          category,
          subcategory: String(data.get("subcategory") ?? "GENERAL"),
          volumeGb: optionalNumber(data.get("volumeGb")),
          accessTier: data.get("vip") === "on" ? "VIP" : "STANDARD",
          enabled: data.get("enabled") === "on",
          countTraffic: data.get("countTraffic") === "on",
          sortOrder: Number(data.get("sortOrder") || 0),
          countryOverride: String(data.get("countryOverride") ?? "").trim() || (checked.country === "Unknown" ? checked.host : checked.country),
          countryCode: String(data.get("countryCode") ?? "").trim() || null,
        }),
      });
      form.reset();
      createNameTouched.current = false;
      setPreview(null);
      setConfigInput("");
      setDisplayName("");
      setAddress("");
      setPort("");
      setCountryOverride("");
      setCountryCode("");
      setCreateCategory("UNLIMITED");
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
          subcategory: String(data.get("subcategory") ?? "GENERAL"),
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
      setEditingId(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "ویرایش سرور ناموفق بود");
    } finally {
      setBusy(false);
    }
  }

  async function remove(server: ManualServer) {
    if (!window.confirm(`سرور «${server.displayName}» حذف شود؟ Sessionهای فعال آن لغو می‌شوند.`)) return;
    setBusy(true);
    setError("");
    try {
      await api("/api/v1/admin/manual-servers", {
        method: "DELETE",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ id: server.id }),
      });
      setEditingId(null);
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "حذف سرور ناموفق بود");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="v2-manual-servers">
      <div className="v2-server-summary" aria-label="خلاصه سرورهای دستی">
        <div><small>کل Manual</small><strong>{formatNumber(servers.length)}</strong></div>
        <div><small>فعال</small><strong>{formatNumber(summary.enabled)}</strong></div>
        <div><small>VIP</small><strong>{formatNumber(summary.vip)}</strong></div>
        <div><small>Limited</small><strong>{formatNumber(summary.limited)}</strong></div>
        <div><small>Traffic ثبت‌شده</small><strong dir="ltr">{formatBytes(summary.traffic)}</strong></div>
      </div>

      <div className="v2-server-toolbar">
        <div className="v2-server-search">
          <AdminIcon name="search" size={16} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="نام، کشور، آدرس یا نوع کاربرد…" aria-label="جست‌وجوی سرورهای دستی" />
        </div>
        <select value={tier} onChange={(event) => setTier(event.target.value)} aria-label="فیلتر دسترسی">
          <option value="ALL">Standard + VIP</option><option value="STANDARD">Standard</option><option value="VIP">VIP</option>
        </select>
        <select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)} aria-label="فیلتر حجم">
          <option value="ALL">Unlimited + Limited</option><option value="UNLIMITED">Unlimited</option><option value="LIMITED">Limited</option>
        </select>
        <span className="v2-server-result-count">{loading ? "…" : `${formatNumber(filtered.length)} نتیجه`}</span>
      </div>

      {error ? <div className="v2-users-error"><span className="v2-status-dot is-danger" /><span>{error}</span><button type="button" onClick={() => void reload()}>تلاش دوباره</button></div> : null}

      <div className="v2-server-table-wrap">
        <table className="v2-server-table v2-manual-table">
          <thead><tr><th>سرور</th><th>Status</th><th>Access</th><th>نوع</th><th>حجم</th><th>Traffic</th><th>Session / User</th><th>آخرین استفاده</th><th>عملیات</th></tr></thead>
          <tbody>{filtered.map((server) => (
            <tr key={server.id}>
              <td><div className="v2-server-name"><span className={`v2-status-dot is-${server.enabled ? "success" : "danger"}`} /><span><strong>{server.flag} {server.displayName}</strong><small dir="ltr">{server.host}:{server.port} · {server.displayCountry}</small></span></div></td>
              <td><span className={`v2-server-state is-${server.enabled ? "success" : "danger"}`}>{server.enabled ? "فعال" : "غیرفعال"}</span></td>
              <td>{server.accessTier === "VIP" ? <span className="v2-server-access is-vip">VIP</span> : <span className="v2-server-access">Standard</span>}</td>
              <td><span className="v2-manual-kind"><strong>{server.category === "LIMITED" ? "Limited" : "Unlimited ∞"}</strong><small>{subcategoryLabel(server.subcategory)}</small></span></td>
              <td>{server.volumeBytes ? formatBytes(server.volumeBytes) : "∞"}</td>
              <td><span className="v2-manual-traffic"><strong>{formatBytes(server.stats.totalTraffic)}</strong><small>{server.countTraffic ? "Accounting On" : "Accounting Off"}</small></span></td>
              <td><span dir="ltr">{formatNumber(server.stats.sessions)} / {formatNumber(server.stats.uniqueUsers)}</span></td>
              <td><span className="v2-server-last-seen">{formatDateTime(server.stats.lastUsed)}</span></td>
              <td><button className="v2-server-edit" type="button" onClick={() => setEditingId(server.id)}><AdminIcon name="sliders" size={15} />ویرایش</button></td>
            </tr>
          ))}</tbody>
        </table>
        {!loading && !filtered.length ? <div className="v2-server-empty"><AdminIcon name="server" size={22} /><strong>سرور دستی پیدا نشد</strong><span>فیلترها را تغییر بده یا یک سرور جدید بساز.</span></div> : null}
      </div>

      <div className="v2-server-mobile-list">
        {filtered.map((server) => (
          <button type="button" className="v2-manual-mobile-card" onClick={() => setEditingId(server.id)} key={server.id}>
            <div className="v2-server-mobile-head"><span className={`v2-status-dot is-${server.enabled ? "success" : "danger"}`} /><span><strong>{server.flag} {server.displayName}</strong><small dir="ltr">{server.host}:{server.port}</small></span><AdminIcon name="chevron-left" size={16} /></div>
            <div className="v2-server-mobile-facts"><span><small>Access</small><strong>{server.accessTier === "VIP" ? "VIP" : "Standard"}</strong></span><span><small>نوع</small><strong>{server.category === "LIMITED" ? "Limited" : "Unlimited"}</strong></span><span><small>Traffic</small><strong>{formatBytes(server.stats.totalTraffic)}</strong></span></div>
          </button>
        ))}
      </div>

      <details className="v2-server-provision v2-manual-provision">
        <summary><span><strong>افزودن Manual Server</strong><small>فقط لینک VLESS را وارد کن؛ مشخصات اتصال خودکار پر می‌شوند و قابل ویرایش می‌مانند.</small></span><span className="v2-server-provision-marker">+</span></summary>
        <div className="v2-manual-provision-body">
          <form onSubmit={create}>
            <div className="field"><label>VLESS Config</label><textarea className="textarea" name="config" rows={4} dir="ltr" placeholder="vless://..." required value={configInput} onChange={(event) => { setConfigInput(event.target.value); setPreview(null); }} /></div>
            <div className="form-grid">
              <div className="field"><label>نام سرور</label><input className="input" name="displayName" value={displayName} onChange={(event) => { createNameTouched.current = true; setDisplayName(event.target.value); }} required /></div>
              <div className="field"><label>آدرس</label><input className="input" name="host" dir="ltr" value={address} onChange={(event) => setAddress(event.target.value)} required /></div>
              <div className="field"><label>پورت</label><input className="input" name="port" type="number" min={1} max={65535} value={port} onChange={(event) => setPort(event.target.value)} required /></div>
              <div className="field"><label>کشور</label><input className="input" name="countryOverride" value={countryOverride} onChange={(event) => setCountryOverride(event.target.value)} required /></div>
              <div className="field"><label>دسته حجم</label><select className="select" name="category" value={createCategory} onChange={(event) => setCreateCategory(event.target.value as "UNLIMITED" | "LIMITED")}><option value="UNLIMITED">Unlimited ∞</option><option value="LIMITED">Limited</option></select></div>
              <div className="field"><label>نوع کاربرد</label><SubcategorySelect /></div>
              <div className="field"><label>حجم سرور (GB)</label><input className="input" name="volumeGb" type="number" min="0.01" step="0.01" required={createCategory === "LIMITED"} disabled={createCategory === "UNLIMITED"} /></div>
              <div className="field"><label>کد کشور</label><input className="input" name="countryCode" maxLength={2} dir="ltr" value={countryCode} onChange={(event) => setCountryCode(event.target.value)} /></div>
              <div className="field"><label>ترتیب</label><input className="input" name="sortOrder" type="number" defaultValue={0} /></div>
            </div>
            <div className="v2-manual-switches"><label><input type="checkbox" name="vip" /> VIP</label><label><input type="checkbox" name="enabled" defaultChecked /> فعال</label><label><input type="checkbox" name="countTraffic" defaultChecked /> کسر ترافیک</label></div>
            <div className="v2-manual-create-actions"><button className="button secondary" type="button" disabled={busy || autoImporting} onClick={(event) => void validate(event.currentTarget.form!)}>{autoImporting ? "در حال خواندن…" : "بررسی دوباره"}</button><button className="button" disabled={busy || autoImporting}>ذخیره سرور</button></div>
            {preview ? <div className="v2-manual-preview"><strong>{preview.flag} {preview.country}</strong><code dir="ltr">{preview.protocol} · {preview.host}:{preview.port} · {preview.transport} · {preview.security}</code></div> : null}
          </form>
        </div>
      </details>

      {selected ? <ManualEditDrawer server={selected} busy={busy} onClose={() => setEditingId(null)} onSubmit={update} onDelete={remove} /> : null}
    </section>
  );
}

function ManualEditDrawer({ server, busy, onClose, onSubmit, onDelete }: {
  server: ManualServer;
  busy: boolean;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>, server: ManualServer) => Promise<void>;
  onDelete: (server: ManualServer) => Promise<void>;
}) {
  const [category, setCategory] = useState<"UNLIMITED" | "LIMITED">(server.category);
  const [replacement, setReplacement] = useState("");
  const [displayName, setDisplayName] = useState(server.displayName);
  const [host, setHost] = useState(server.host);
  const [port, setPort] = useState(String(server.port));
  const [country, setCountry] = useState(server.countryOverride ?? server.displayCountry);
  const [countryCode, setCountryCode] = useState(server.countryCode ?? "");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState("");
  const nameTouched = useRef(false);

  useEffect(() => {
    const config = replacement.trim();
    if (!config) {
      setPreview(null);
      setImportError("");
      setImporting(false);
      return;
    }
    if (!config.toLowerCase().startsWith("vless://")) {
      setPreview(null);
      setImportError("لینک جایگزین باید با vless:// شروع شود");
      setImporting(false);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setImporting(true);
      setImportError("");
      void validateManualConfig(config)
        .then((result) => {
          if (cancelled) return;
          setPreview(result);
          setHost(result.host);
          setPort(String(result.port));
          setCountry(result.country === "Unknown" ? "" : result.country);
          setCountryCode(result.countryCode.toLowerCase() === "global" ? "" : result.countryCode);
          if (!nameTouched.current) setDisplayName(result.suggestedName);
        })
        .catch((cause) => {
          if (cancelled) return;
          setPreview(null);
          setImportError(cause instanceof Error ? cause.message : "خواندن لینک جایگزین ناموفق بود");
        })
        .finally(() => {
          if (!cancelled) setImporting(false);
        });
    }, 400);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [replacement]);

  return (
    <div className="v2-server-drawer-layer" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}>
      <aside className="v2-server-drawer" role="dialog" aria-modal="true" aria-label={`ویرایش ${server.displayName}`}>
        <header><div><span className={`v2-status-dot is-${server.enabled ? "success" : "danger"}`} /><span><strong>{server.flag} {server.displayName}</strong><small dir="ltr">{server.host}:{server.port}</small></span></div><button type="button" onClick={onClose} aria-label="بستن"><AdminIcon name="x" size={17} /></button></header>
        <form onSubmit={(event) => void onSubmit(event, server)}>
          <div className="v2-server-drawer-body">
            <div className="form-grid">
              <div className="field"><label>نام سرور</label><input className="input" name="displayName" value={displayName} onChange={(event) => { nameTouched.current = true; setDisplayName(event.target.value); }} required /></div>
              <div className="field"><label>آدرس</label><input className="input" name="host" value={host} onChange={(event) => setHost(event.target.value)} dir="ltr" required /></div>
              <div className="field"><label>پورت</label><input className="input" name="port" type="number" min={1} max={65535} value={port} onChange={(event) => setPort(event.target.value)} required /></div>
              <div className="field"><label>کشور</label><input className="input" name="countryOverride" value={country} onChange={(event) => setCountry(event.target.value)} required /></div>
              <div className="field"><label>دسته حجم</label><select className="select" name="category" value={category} onChange={(event) => setCategory(event.target.value as "UNLIMITED" | "LIMITED")}><option value="UNLIMITED">Unlimited ∞</option><option value="LIMITED">Limited</option></select></div>
              <div className="field"><label>نوع کاربرد</label><SubcategorySelect defaultValue={server.subcategory} /></div>
              <div className="field"><label>حجم سرور (GB)</label><input className="input" name="volumeGb" type="number" min="0.01" step="0.01" defaultValue={bytesToGbInput(server.volumeBytes)} required={category === "LIMITED"} disabled={category === "UNLIMITED"} /></div>
              <div className="field"><label>کد کشور</label><input className="input" name="countryCode" value={countryCode} onChange={(event) => setCountryCode(event.target.value)} maxLength={2} dir="ltr" /></div>
              <div className="field"><label>ترتیب</label><input className="input" name="sortOrder" type="number" defaultValue={server.sortOrder} /></div>
            </div>
            <div className="field"><label>جایگزینی VLESS Config</label><textarea className="textarea" name="config" rows={3} dir="ltr" value={replacement} onChange={(event) => { setReplacement(event.target.value); setPreview(null); }} placeholder="لینک جدید را paste کن؛ فیلدها خودکار پر می‌شوند. برای حفظ کانفیگ فعلی خالی بگذار." /></div>
            {importing ? <div className="v2-manual-preview"><strong>در حال خواندن لینک…</strong><code dir="ltr">VLESS auto import</code></div> : null}
            {importError ? <div className="v2-users-error"><span className="v2-status-dot is-danger" /><span>{importError}</span></div> : null}
            {preview && !importing ? <div className="v2-manual-preview"><strong>{preview.flag} {preview.country}</strong><code dir="ltr">{preview.protocol} · {preview.host}:{preview.port} · {preview.transport} · {preview.security}</code></div> : null}
            <div className="v2-manual-switches"><label><input type="checkbox" name="vip" defaultChecked={server.accessTier === "VIP"} /> VIP</label><label><input type="checkbox" name="enabled" defaultChecked={server.enabled} /> فعال</label><label><input type="checkbox" name="countTraffic" defaultChecked={server.countTraffic} /> کسر ترافیک</label></div>
            <div className="v2-manual-drawer-stats"><span><small>Traffic</small><strong>{formatBytes(server.stats.totalTraffic)}</strong></span><span><small>Sessions</small><strong>{formatNumber(server.stats.sessions)}</strong></span><span><small>Users</small><strong>{formatNumber(server.stats.uniqueUsers)}</strong></span><span><small>Last Used</small><strong>{formatDateTime(server.stats.lastUsed)}</strong></span></div>
          </div>
          <footer><button className="button secondary v2-delete-button" type="button" disabled={busy || importing} onClick={() => void onDelete(server)}>حذف سرور</button><div><button className="button secondary" type="button" onClick={onClose} disabled={busy || importing}>انصراف</button><button className="button" disabled={busy || importing || (replacement.trim().length > 0 && !preview)}>ثبت تغییرات</button></div></footer>
        </form>
      </aside>
    </div>
  );
}
