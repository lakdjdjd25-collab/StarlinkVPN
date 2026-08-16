"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
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

type Receipt = {
  reused: boolean;
  license: string;
  qrPayload: string;
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
      style={{ width: 240, maxWidth: "100%", background: "#fff", borderRadius: 14 }}
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

export function ManagedLicenseForm() {
  const router = useRouter();
  const [profiles, setProfiles] = useState<ProviderProfile[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [receipt, setReceipt] = useState<Receipt | null>(null);

  useEffect(() => {
    let active = true;
    void fetch("/api/v1/admin/licenses", { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => null) as {
          data?: { profiles?: ProviderProfile[] };
          error?: { message?: string };
        } | null;
        if (!active) return;
        if (!response.ok) setError(body?.error?.message ?? "دریافت قالب‌های پاسارگارد انجام نشد");
        else {
          const items = body?.data?.profiles ?? [];
          setProfiles(items);
          if (!items.length) setError("هیچ قالب یا گروه قابل‌استفاده‌ای در پاسارگارد پیدا نشد");
        }
      })
      .catch(() => {
        if (active) setError("ارتباط با سرور برای دریافت قالب‌ها برقرار نشد");
      })
      .finally(() => {
        if (active) setLoadingTemplates(false);
      });
    return () => { active = false; };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError("");
    setMessage("");
    setReceipt(null);
    try {
      const response = await fetch("/api/v1/admin/licenses", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          remoteUsername: data.get("remoteUsername"),
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
        ? "این کاربر قبلاً متصل شده بود؛ مجوز موجود نمایش داده شد."
        : "کاربر پاسارگارد، سرویس و مجوز با موفقیت ساخته و همگام شدند.");
      if (!body.data.reused) form.reset();
      router.refresh();
    } catch {
      setError("ارتباط با سرور هنگام ساخت مجوز قطع شد؛ دوباره تلاش کنید");
    } finally {
      setBusy(false);
    }
  }

  async function copyLicense() {
    if (!receipt) return;
    try {
      await navigator.clipboard.writeText(receipt.license);
      setMessage("متن مجوز کپی شد.");
    } catch {
      setError("کپی خودکار ممکن نبود؛ متن مجوز را دستی انتخاب کنید");
    }
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

  return (
    <>
      <form onSubmit={submit}>
        <div className="form-grid">
          <div className="field"><label>نام کاربر</label><input className="input" name="customerName" placeholder="مثلاً اشتراک علی" minLength={2} maxLength={120} required /></div>
          <div className="field"><label>نام کاربری پاسارگارد</label><input className="input" name="remoteUsername" dir="ltr" pattern="[A-Za-z0-9][A-Za-z0-9_.-]{2,63}" placeholder="ali_001" required /></div>
          <div className="field"><label>حجم (GB)</label><input className="input" name="quotaGb" type="number" min="0.1" max="100000" step="0.1" defaultValue="60" required /></div>
          <div className="field"><label>اعتبار (روز)</label><input className="input" name="days" type="number" min="1" max="3650" defaultValue="30" required /></div>
          <div className="field"><label>تعداد دستگاه متصل</label><input className="input" name="maxDevices" type="number" min="1" max="1000" defaultValue="2" required /></div>
          <div className="field">
            <label>قالب/گروه پاسارگارد</label>
            <select className="select" name="profileKey" required disabled={loadingTemplates || !profiles.length}>
              {loadingTemplates ? <option value="">در حال دریافت از پاسارگارد…</option> : null}
              {!loadingTemplates && !profiles.length ? <option value="">قالب یا گروهی موجود نیست</option> : null}
              {profiles.map((profile) => (
                <option value={profile.key} key={profile.key}>
                  {profile.kind === "template" ? "قالب" : "گروه"}: {profile.name} — گروه {profile.groupIds.join("،")}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="field"><label>یادداشت (اختیاری)</label><input className="input" name="note" maxLength={500} /></div>
        {error ? <p className="error">{error}</p> : null}
        {message ? <p style={{ color: "var(--success)", fontSize: 13 }}>{message}</p> : null}
        <button className="button" disabled={busy || loadingTemplates || !profiles.length} style={{ marginTop: 14 }}>
          {busy ? "در حال ساخت و همگام‌سازی…" : "افزودن کاربر و صدور مجوز"}
        </button>
      </form>

      {receipt ? (
        <div className="card" style={{ marginTop: 18, padding: 18, display: "grid", gap: 16, gridTemplateColumns: "minmax(0, 1fr) minmax(180px, 260px)", alignItems: "center" }}>
          <div>
            <span className="badge green">مجوز آماده است</span>
            <h3 style={{ marginBottom: 8 }}>{receipt.service.name}</h3>
            <input className="input" value={receipt.license} readOnly dir="ltr" onFocus={(event) => event.currentTarget.select()} />
            <p style={{ color: "var(--muted)", fontSize: 13 }}>
              کاربر: <span dir="ltr">{receipt.remoteUser.username}</span> — دستگاه: {receipt.service.maxDevices} — انقضا: {new Date(receipt.service.expiresAt).toLocaleDateString("fa-IR")}
            </p>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button className="button" type="button" onClick={copyLicense}>کپی متن مجوز</button>
              <button className="button secondary" type="button" onClick={downloadQr}>دانلود کیوآرکد</button>
            </div>
          </div>
          <div style={{ display: "flex", justifyContent: "center" }}><QrCode value={receipt.qrPayload} /></div>
        </div>
      ) : null}
    </>
  );
}
