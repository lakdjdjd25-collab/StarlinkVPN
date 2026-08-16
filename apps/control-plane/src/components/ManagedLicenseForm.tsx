"use client";

import {
  FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import QRCode from "@/vendor/qrcode";

type ProviderProfile = {
  key: string;
  kind: "template" | "group";
  id: number;
  name: string;
  groupIds: number[];
  dataLimit: string | null;
  expireDurationSeconds: number | null;
};

type ManagedLicense = {
  id: string;
  name: string;
  email: string;
  credentialsReady: boolean;
  license: string;
  status: string;
  quotaBytes: string;
  usedBytes: string;
  expiresAt: string;
  maxDevices: number;
  profileKey: string;
  profileName: string;
  remoteUsername: string;
  serverCount: number;
  lastSyncAt: string | null;
  lastError: string | null;
  providerId: string | null;
  providerName: string;
  needsMigration: boolean;
};

type Credentials = {
  email: string;
  initialPassword: string;
};

type Receipt = {
  reused: boolean;
  license: string;
  qrPayload: string;
  credentials: { email: string; initialPassword: string | null };
  service: {
    id: string;
    name: string;
    quotaBytes: string;
    expiresAt: string;
    maxDevices: number;
  };
  remoteUser: { id: number; username: string };
};

function qrGeometry(value: string) {
  const code = new QRCode(0, 0);
  code.addData(value);
  code.make();
  const count = code.getModuleCount();
  const quiet = 4;
  const path: string[] = [];
  for (let row = 0; row < count; row += 1) {
    for (let column = 0; column < count; column += 1) {
      if (code.isDark(row, column)) path.push(`M${column + quiet} ${row + quiet}h1v1h-1z`);
    }
  }
  return { size: count + quiet * 2, path: path.join("") };
}

function QrCode({ value }: { value: string }) {
  const geometry = useMemo(() => qrGeometry(value), [value]);
  return (
    <svg
      aria-label="کیوآرکد مجوز NimHUB"
      role="img"
      viewBox={`0 0 ${geometry.size} ${geometry.size}`}
      xmlns="http://www.w3.org/2000/svg"
      className="license-qr"
    >
      <rect width={geometry.size} height={geometry.size} fill="#fff" />
      <path d={geometry.path} fill="#05070a" />
    </svg>
  );
}

function qrSvg(value: string): string {
  const geometry = qrGeometry(value);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${geometry.size} ${geometry.size}" shape-rendering="crispEdges"><rect width="100%" height="100%" fill="white"/><path d="${geometry.path}" fill="#05070a"/></svg>`;
}

function bytesToGb(value: string): number {
  return Number(value) / 1024 ** 3;
}

function formatGb(value: string): string {
  return new Intl.NumberFormat("fa-IR", { maximumFractionDigits: 1 }).format(bytesToGb(value));
}

function daysFromNow(value: string): number {
  const difference = new Date(value).getTime() - Date.now();
  return Math.max(1, Math.ceil(difference / 86_400_000));
}

function persianDate(value: string): string {
  return new Intl.DateTimeFormat("fa-IR", { dateStyle: "medium" }).format(new Date(value));
}

function profileLabel(profile: ProviderProfile): string {
  return `${profile.kind === "template" ? "قالب" : "گروه"}: ${profile.name}`;
}

async function copyText(value: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(value);
    return true;
  } catch {
    return false;
  }
}

export function ManagedLicenseForm() {
  const [profiles, setProfiles] = useState<ProviderProfile[]>([]);
  const [licenses, setLicenses] = useState<ManagedLicense[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [search, setSearch] = useState("");
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [credentialReceipt, setCredentialReceipt] = useState<{
    serviceName: string;
    credentials: Credentials;
  } | null>(null);
  const idempotencyKey = useRef("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const response = await fetch("/api/v1/admin/licenses", { cache: "no-store" });
      const body = await response.json().catch(() => null) as {
        data?: { profiles?: ProviderProfile[]; licenses?: ManagedLicense[] };
        error?: { message?: string };
      } | null;
      if (!response.ok) {
        setError(body?.error?.message ?? "دریافت اطلاعات مجوزها انجام نشد");
        return;
      }
      const nextProfiles = body?.data?.profiles ?? [];
      setProfiles(nextProfiles);
      setLicenses(body?.data?.licenses ?? []);
      if (!nextProfiles.length) setError("هیچ قالب یا گروه قابل‌استفاده‌ای در پاسارگارد پیدا نشد");
    } catch {
      setError("ارتباط با سرور برای دریافت مجوزها برقرار نشد");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    if (!idempotencyKey.current) idempotencyKey.current = crypto.randomUUID();
    setBusy(true);
    setError("");
    setMessage("");
    setReceipt(null);
    setCredentialReceipt(null);
    try {
      const response = await fetch("/api/v1/admin/licenses", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          idempotencyKey: idempotencyKey.current,
          customerName: data.get("customerName"),
          quotaGb: Number(data.get("quotaGb")),
          days: Number(data.get("days")),
          maxDevices: Number(data.get("maxDevices")),
          profileKey: data.get("profileKey"),
          note: data.get("note"),
        }),
      });
      const body = await response.json().catch(() => null) as {
        data?: Receipt;
        error?: { message?: string };
      } | null;
      if (!response.ok || !body?.data) {
        setError(body?.error?.message ?? "ساخت کاربر و مجوز انجام نشد");
        return;
      }
      setReceipt(body.data);
      setMessage(body.data.reused
        ? "این درخواست قبلاً انجام شده بود؛ مجوز موجود نمایش داده شد. برای رمز تازه از مدیریت کاربر استفاده کنید."
        : "کاربر، ایمیل و رمز، سرویس پاسارگارد و مجوز با موفقیت ساخته شدند.");
      idempotencyKey.current = "";
      if (!body.data.reused) form.reset();
      await load();
    } catch {
      setError("ارتباط با سرور هنگام ساخت مجوز قطع شد؛ دوباره همان دکمه را بزنید");
    } finally {
      setBusy(false);
    }
  }

  async function copyReceipt() {
    if (!receipt) return;
    const lines = [
      `ایمیل: ${receipt.credentials.email}`,
      ...(receipt.credentials.initialPassword ? [`رمز اولیه: ${receipt.credentials.initialPassword}`] : []),
      `مجوز: ${receipt.license}`,
    ];
    if (await copyText(lines.join("\n"))) setMessage("ایمیل، رمز و مجوز کپی شدند.");
    else setError("کپی خودکار ممکن نبود؛ اطلاعات را دستی انتخاب کنید");
  }

  function downloadQr() {
    if (!receipt) return;
    const blob = new Blob([qrSvg(receipt.qrPayload)], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `nimhub-${receipt.remoteUser.username}-qr.svg`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  const normalizedSearch = search.trim().toLowerCase();
  const visibleLicenses = normalizedSearch
    ? licenses.filter((item) => [item.name, item.email, item.license, item.remoteUsername]
      .some((value) => value.toLowerCase().includes(normalizedSearch)))
    : licenses;

  return (
    <div className="license-manager">
      <section className="manager-panel manager-create">
        <div className="section-title">
          <div><h2>صدور کاربر جدید</h2><p>همه‌چیز در یک مرحله ساخته می‌شود.</p></div>
        </div>
        <form onSubmit={submit}>
          <div className="form-grid">
            <div className="field"><label>نام مشتری یا سرویس</label><input className="input" name="customerName" placeholder="مثلاً اشتراک علی" minLength={2} maxLength={120} required /></div>
            <div className="field"><label>حجم (GB)</label><input className="input" name="quotaGb" type="number" min="0.1" max="100000" step="0.1" defaultValue="60" required /></div>
            <div className="field"><label>اعتبار (روز)</label><input className="input" name="days" type="number" min="1" max="3650" defaultValue="30" required /></div>
            <div className="field"><label>تعداد دستگاه</label><input className="input" name="maxDevices" type="number" min="1" max="1000" defaultValue="2" required /></div>
            <div className="field manager-profile">
              <label>پلن / گروه سرورها</label>
              <select className="select" name="profileKey" required disabled={loading || !profiles.length}>
                {loading ? <option value="">در حال دریافت از پاسارگارد…</option> : null}
                {!loading && !profiles.length ? <option value="">گروهی موجود نیست</option> : null}
                {profiles.map((profile) => <option value={profile.key} key={profile.key}>{profileLabel(profile)}</option>)}
              </select>
            </div>
            <div className="field"><label>یادداشت (اختیاری)</label><input className="input" name="note" maxLength={500} /></div>
          </div>
          <p className="manager-hint">ایمیل تصادفی @nimhub.com، رمز اولیهٔ ۸ کاراکتری، مجوز و QR خودکار ساخته می‌شوند.</p>
          {error ? <p className="error">{error}</p> : null}
          {message ? <p className="success-message">{message}</p> : null}
          <button className="button" disabled={busy || loading || !profiles.length}>
            {busy ? "در حال ساخت و همگام‌سازی…" : "ساخت کاربر و صدور مجوز"}
          </button>
        </form>
      </section>

      {receipt ? (
        <section className="manager-panel credential-receipt">
          <div className="receipt-content">
            <span className="badge green">اطلاعات آمادهٔ تحویل</span>
            <h3>{receipt.service.name}</h3>
            <CredentialFields
              email={receipt.credentials.email}
              password={receipt.credentials.initialPassword}
              license={receipt.license}
            />
            {!receipt.credentials.initialPassword ? <p className="manager-hint">رمز قبلی قابل نمایش نیست؛ از دکمهٔ بازنشانی رمز در کارت کاربر استفاده کنید.</p> : null}
            <div className="manager-actions">
              <button className="button" type="button" onClick={copyReceipt}>کپی اطلاعات ورود و مجوز</button>
              <button className="button secondary" type="button" onClick={downloadQr}>دانلود QR</button>
            </div>
          </div>
          <div className="receipt-qr"><QrCode value={receipt.qrPayload} /></div>
        </section>
      ) : null}

      {credentialReceipt ? (
        <section className="manager-panel credential-receipt compact-receipt">
          <div className="receipt-content">
            <span className="badge green">رمز تازه ساخته شد</span>
            <h3>{credentialReceipt.serviceName}</h3>
            <CredentialFields
              email={credentialReceipt.credentials.email}
              password={credentialReceipt.credentials.initialPassword}
            />
            <button
              className="button"
              type="button"
              onClick={() => void copyText(`ایمیل: ${credentialReceipt.credentials.email}\nرمز اولیه: ${credentialReceipt.credentials.initialPassword}`)
                .then((done) => setMessage(done ? "ایمیل و رمز کپی شدند." : "کپی خودکار ممکن نبود"))}
            >
              کپی ایمیل و رمز
            </button>
          </div>
        </section>
      ) : null}

      <section className="manager-panel">
        <div className="manager-list-header">
          <div><h2>مدیریت کاربران و مجوزها</h2><p>مسدودی، حجم، اعتبار، دستگاه و گروه سرورها</p></div>
          <input
            className="input manager-search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="جست‌وجوی نام، ایمیل یا مجوز"
          />
        </div>
        {loading ? <div className="empty">در حال دریافت کاربران…</div> : null}
        {!loading && !visibleLicenses.length ? <div className="empty">کاربری برای نمایش پیدا نشد.</div> : null}
        <div className="managed-license-grid">
          {visibleLicenses.map((license) => (
            <ManagedLicenseCard
              key={license.id}
              license={license}
              profiles={profiles}
              onUpdated={async (text) => { setMessage(text); await load(); }}
              onCredentials={async (credentials) => {
                setCredentialReceipt({ serviceName: license.name, credentials });
                setMessage("ایمیل و رمز تازه ساخته شد؛ رمز فقط همین بار نمایش داده می‌شود.");
                await load();
              }}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function CredentialFields({
  email,
  password,
  license,
}: {
  email: string;
  password: string | null;
  license?: string;
}) {
  return (
    <div className="credential-fields">
      <label><span>ایمیل ورود</span><input className="input" value={email} readOnly dir="ltr" onFocus={(event) => event.currentTarget.select()} /></label>
      {password ? <label><span>رمز اولیه</span><input className="input" value={password} readOnly dir="ltr" onFocus={(event) => event.currentTarget.select()} /></label> : null}
      {license ? <label><span>مجوز</span><input className="input" value={license} readOnly dir="ltr" onFocus={(event) => event.currentTarget.select()} /></label> : null}
    </div>
  );
}

function ManagedLicenseCard({
  license,
  profiles,
  onUpdated,
  onCredentials,
}: {
  license: ManagedLicense;
  profiles: ProviderProfile[];
  onUpdated: (message: string) => Promise<void>;
  onCredentials: (credentials: Credentials) => Promise<void>;
}) {
  const [quotaGb, setQuotaGb] = useState(String(Number(bytesToGb(license.quotaBytes).toFixed(1))));
  const [days, setDays] = useState(String(daysFromNow(license.expiresAt)));
  const [maxDevices, setMaxDevices] = useState(String(license.maxDevices));
  const [status, setStatus] = useState(license.status === "ACTIVE" ? "ACTIVE" : "SUSPENDED");
  const [profileKey, setProfileKey] = useState(license.profileKey);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setQuotaGb(String(Number(bytesToGb(license.quotaBytes).toFixed(1))));
    setDays(String(daysFromNow(license.expiresAt)));
    setMaxDevices(String(license.maxDevices));
    setStatus(license.status === "ACTIVE" ? "ACTIVE" : "SUSPENDED");
    setProfileKey(license.profileKey);
  }, [license, profiles]);

  async function request(body: unknown) {
    setBusy(true);
    setError("");
    try {
      const response = await fetch("/api/v1/admin/licenses", {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      const parsed = await response.json().catch(() => null) as {
        data?: { credentials?: Credentials };
        error?: { message?: string };
      } | null;
      if (!response.ok) {
        setError(parsed?.error?.message ?? "ذخیره تغییرات انجام نشد");
        return null;
      }
      return parsed?.data ?? {};
    } catch {
      setError("ارتباط با سرور قطع شد؛ دوباره تلاش کنید");
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = await request({
      action: "update",
      serviceId: license.id,
      status,
      quotaGb: Number(quotaGb),
      daysFromNow: Number(days),
      maxDevices: Number(maxDevices),
      profileKey,
    });
    if (result) await onUpdated("تغییرات کاربر در NimHUB و پاسارگارد ذخیره شد.");
  }

  async function resetCredentials() {
    const result = await request({ action: "reset_credentials", serviceId: license.id });
    if (result?.credentials) await onCredentials(result.credentials);
  }

  async function migrateProvider() {
    if (!profileKey) {
      setError("برای انتقال، ابتدا Group یا Template پنل فعال را انتخاب کنید");
      return;
    }
    const result = await request({ action: "migrate_provider", serviceId: license.id, profileKey });
    if (result) await onUpdated("سرویس بدون حذف حساب NimHUB به پنل فعال منتقل و سرورها دوباره همگام شدند.");
  }

  const usedPercent = Math.min(100, Math.round((Number(license.usedBytes) / Math.max(1, Number(license.quotaBytes))) * 100));

  return (
    <article className={`managed-license-card ${license.status === "ACTIVE" ? "is-active" : "is-blocked"}`}>
      <div className="license-card-head">
        <div>
          <h3>{license.name}</h3>
          <p dir="ltr">{license.credentialsReady ? license.email : "ورود ایمیلی هنوز ساخته نشده"}</p>
        </div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", justifyContent: "flex-end" }}><span className={license.status === "ACTIVE" ? "badge green" : "badge red"}>{license.status === "ACTIVE" ? "فعال" : "مسدود"}</span>{license.needsMigration ? <span className="badge red">نیازمند انتقال پنل</span> : null}</div>
      </div>
      <div className="license-code" dir="ltr">{license.license}</div>
      <div className="usage-row"><span>مصرف {formatGb(license.usedBytes)} از {formatGb(license.quotaBytes)} GB</span><span>{usedPercent}٪</span></div>
      <div className="usage-track"><span style={{ width: `${usedPercent}%` }} /></div>
      <div className="license-facts">
        <span>انقضا <strong>{persianDate(license.expiresAt)}</strong></span>
        <span>دستگاه <strong>{license.maxDevices}</strong></span>
        <span>سرور <strong>{license.serverCount}</strong></span>
        <span>گروه <strong>{license.profileName}</strong></span>
        <span>Provider <strong>{license.providerName}</strong></span>
      </div>
      {license.lastError ? <p className="error">آخرین همگام‌سازی: {license.lastError}</p> : null}
      {license.needsMigration ? <p className="error">این سرویس هنوز به پنل قبلی متصل است. Group/Template پنل فعال را انتخاب و «انتقال به پنل فعال» را اجرا کنید؛ سیستم هیچ Mapping را خودکار حدس نمی‌زند.</p> : null}
      <details className="license-editor">
        <summary>مدیریت این کاربر</summary>
        <form onSubmit={update}>
          <div className="form-grid">
            <div className="field"><label>وضعیت</label><select className="select" value={status} onChange={(event) => setStatus(event.target.value)}><option value="ACTIVE">فعال</option><option value="SUSPENDED">مسدود</option></select></div>
            <div className="field"><label>تعداد دستگاه</label><input className="input" value={maxDevices} onChange={(event) => setMaxDevices(event.target.value)} type="number" min="1" max="1000" required /></div>
            <div className="field"><label>حجم کل (GB)</label><input className="input" value={quotaGb} onChange={(event) => setQuotaGb(event.target.value)} type="number" min="0.1" max="100000" step="0.1" required /><QuickAdds values={[10, 30, 50]} unit="GB" onAdd={(value) => setQuotaGb(String(Number(quotaGb || 0) + value))} /></div>
            <div className="field"><label>اعتبار از امروز (روز)</label><input className="input" value={days} onChange={(event) => setDays(event.target.value)} type="number" min="1" max="3650" required /><QuickAdds values={[30, 90, 180]} unit="روز" onAdd={(value) => setDays(String(Number(days || 0) + value))} /></div>
            <div className="field manager-profile"><label>پلن / گروه سرورها</label><select className="select" value={profileKey} onChange={(event) => setProfileKey(event.target.value)} required><option value="" disabled>یک گروه را انتخاب کنید</option>{profiles.map((profile) => <option value={profile.key} key={profile.key}>{profileLabel(profile)}</option>)}</select></div>
          </div>
          {error ? <p className="error">{error}</p> : null}
          <div className="manager-actions">
            {license.needsMigration ? (
              <button className="button" type="button" disabled={busy || !profileKey} onClick={migrateProvider}>{busy ? "در حال انتقال…" : "انتقال به پنل فعال"}</button>
            ) : (
              <button className="button" disabled={busy || !profileKey}>{busy ? "در حال ذخیره…" : "ذخیره و همگام‌سازی"}</button>
            )}
            <button className="button secondary" type="button" disabled={busy} onClick={resetCredentials}>
              {license.credentialsReady ? "بازنشانی رمز ورود" : "ساخت ایمیل و رمز ورود"}
            </button>
            <button className="button secondary" type="button" onClick={() => void copyText(license.license)}>کپی مجوز</button>
          </div>
        </form>
      </details>
    </article>
  );
}

function QuickAdds({ values, unit, onAdd }: { values: number[]; unit: string; onAdd: (value: number) => void }) {
  return <div className="quick-adds">{values.map((value) => <button type="button" key={value} onClick={() => onAdd(value)}>+{value} {unit}</button>)}</div>;
}
