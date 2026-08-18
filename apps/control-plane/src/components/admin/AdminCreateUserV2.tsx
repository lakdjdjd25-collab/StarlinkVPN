"use client";

import { FormEvent, useMemo, useRef, useState } from "react";
import QRCode from "@/vendor/qrcode";
import { AdminIcon } from "./AdminIcon";

type ProviderProfile = {
  key: string;
  kind: "template" | "group";
  id: number;
  name: string;
  groupIds: number[];
};

type CreateReceipt = {
  reused: boolean;
  license: string;
  qrPayload: string;
  credentials: { email: string; initialPassword: string | null };
  service: { id: string; name: string; quotaBytes: string; expiresAt: string; maxDevices: number };
  remoteUser: { id: number; username: string };
  vipAccess: boolean;
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

function ReceiptQr({ value }: { value: string }) {
  const geometry = useMemo(() => qrGeometry(value), [value]);
  return <svg className="v2-create-qr" role="img" aria-label="QR مجوز NimHUB" viewBox={`0 0 ${geometry.size} ${geometry.size}`}><rect width={geometry.size} height={geometry.size} fill="#fff" /><path d={geometry.path} fill="#05070a" /></svg>;
}

async function copyText(value: string): Promise<boolean> {
  try { await navigator.clipboard.writeText(value); return true; } catch { return false; }
}

export function AdminCreateUserV2() {
  const [open, setOpen] = useState(false);
  const [profiles, setProfiles] = useState<ProviderProfile[]>([]);
  const [loadingProfiles, setLoadingProfiles] = useState(false);
  const [profileError, setProfileError] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [receipt, setReceipt] = useState<CreateReceipt | null>(null);
  const idempotencyKey = useRef("");

  async function loadProfiles() {
    if (profiles.length || loadingProfiles) return;
    setLoadingProfiles(true);
    setProfileError("");
    try {
      const response = await fetch("/api/v1/admin/licenses", { cache: "no-store" });
      const body = await response.json().catch(() => null) as { data?: { profiles?: ProviderProfile[] }; error?: { message?: string } } | null;
      if (!response.ok) {
        setProfileError(body?.error?.message ?? "دریافت گروه‌های سرور انجام نشد");
      } else {
        setProfiles(body?.data?.profiles ?? []);
        if (!body?.data?.profiles?.length) setProfileError("هیچ Group یا Template قابل استفاده‌ای در Provider پیدا نشد");
      }
    } catch {
      setProfileError("اتصال به Provider برای دریافت گروه‌های سرور برقرار نشد");
    } finally {
      setLoadingProfiles(false);
    }
  }

  function show() {
    setOpen(true);
    setError("");
    setMessage("");
    setReceipt(null);
    void loadProfiles();
  }

  function close() {
    if (busy) return;
    if (receipt) {
      window.location.reload();
      return;
    }
    setOpen(false);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!idempotencyKey.current) idempotencyKey.current = crypto.randomUUID();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch("/api/v1/admin/control-center/users/create", {
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
          vipAccess: data.get("vipAccess") === "on",
        }),
      });
      const body = await response.json().catch(() => null) as { data?: CreateReceipt; error?: { message?: string; details?: { technical?: string; retryable?: boolean } } } | null;
      if (!response.ok || !body?.data) {
        const technical = body?.error?.details?.technical;
        setError(technical ? `${body?.error?.message ?? "ساخت کاربر انجام نشد"} — ${technical}` : body?.error?.message ?? "ساخت کاربر انجام نشد");
        return;
      }
      setReceipt(body.data);
      setMessage(body.data.reused ? "درخواست قبلی بازیابی شد؛ اطلاعات موجود نمایش داده می‌شود." : "کاربر، اشتراک، مجوز و دسترسی سرورها با موفقیت ساخته شدند.");
      idempotencyKey.current = "";
    } catch {
      setError("ارتباط با سرور قطع شد. دوباره همان دکمه را بزن؛ درخواست با همان شناسه بازیابی می‌شود.");
    } finally {
      setBusy(false);
    }
  }

  async function copyAll() {
    if (!receipt) return;
    const text = [
      `ایمیل: ${receipt.credentials.email}`,
      ...(receipt.credentials.initialPassword ? [`رمز: ${receipt.credentials.initialPassword}`] : []),
      `مجوز: ${receipt.license}`,
    ].join("\n");
    setMessage(await copyText(text) ? "اطلاعات ورود و مجوز کپی شدند." : "کپی خودکار ممکن نبود.");
  }

  return <>
    <button type="button" className="v2-create-user-trigger" onClick={show}><span>+</span> کاربر جدید</button>
    {open ? <div className="v2-create-layer" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) close(); }}>
      <section className="v2-create-dialog" role="dialog" aria-modal="true" aria-label="ساخت کاربر جدید">
        <header><div><span className="v2-eyebrow">NEW CUSTOMER</span><h2>ساخت کاربر جدید</h2><p>Account، اشتراک، PasarGuard، مجوز و اطلاعات ورود در یک جریان ساخته می‌شوند.</p></div><button type="button" onClick={close} aria-label="بستن"><AdminIcon name="x" size={18} /></button></header>
        {receipt ? <div className="v2-create-receipt">
          <div className="v2-create-receipt-main"><span className="v2-user-badge is-success">آماده تحویل</span><h3>{receipt.service.name}</h3><label><small>ایمیل ورود</small><code dir="ltr">{receipt.credentials.email}</code></label>{receipt.credentials.initialPassword ? <label><small>رمز اولیه</small><code dir="ltr">{receipt.credentials.initialPassword}</code></label> : <p className="v2-create-note">این درخواست قبلاً ساخته شده و رمز قبلی قابل نمایش دوباره نیست؛ در صورت نیاز از User Drawer رمز را بازنشانی کن.</p>}<label><small>مجوز</small><code dir="ltr">{receipt.license}</code></label><div className="v2-create-receipt-badges"><span className={`v2-user-badge ${receipt.vipAccess ? "is-vip" : "is-neutral"}`}>{receipt.vipAccess ? "VIP" : "Standard"}</span><span className="v2-user-badge is-neutral">{receipt.service.maxDevices} دستگاه</span></div></div>
          <div className="v2-create-receipt-qr"><ReceiptQr value={receipt.qrPayload} /></div>
          {message ? <p className="v2-create-success">{message}</p> : null}
          <div className="v2-create-actions"><button type="button" onClick={() => void copyAll()}>کپی همه اطلاعات</button><button type="button" className="is-primary" onClick={close}>بستن و بروزرسانی کاربران</button></div>
        </div> : <form onSubmit={submit}>
          <div className="v2-create-grid">
            <label><span>نام مشتری / سرویس</span><input name="customerName" minLength={2} maxLength={120} placeholder="مثلاً اشتراک علی" required /></label>
            <label><span>حجم</span><div className="v2-create-unit"><input name="quotaGb" type="number" min="0.1" max="100000" step="0.1" defaultValue="60" required /><i>GB</i></div></label>
            <label><span>اعتبار</span><div className="v2-create-unit"><input name="days" type="number" min="1" max="3650" defaultValue="30" required /><i>روز</i></div></label>
            <label><span>تعداد دستگاه</span><input name="maxDevices" type="number" min="1" max="1000" defaultValue="2" required /></label>
            <label className="v2-create-profile"><span>گروه سرورها</span><select name="profileKey" disabled={loadingProfiles || !profiles.length} required>{loadingProfiles ? <option>در حال دریافت…</option> : null}{profiles.map((profile) => <option key={profile.key} value={profile.key}>{profile.kind === "template" ? "قالب" : "گروه"} — {profile.name}</option>)}</select><small>از Group / Template واقعی Provider فعال خوانده می‌شود.</small></label>
            <label className="v2-create-note-field"><span>یادداشت اختیاری</span><input name="note" maxLength={500} /></label>
          </div>
          <label className="v2-create-vip"><input type="checkbox" name="vipAccess" /><span><strong>دسترسی VIP</strong><small>سرورهای Standard همیشه مستقل باقی می‌مانند.</small></span></label>
          {profileError ? <div className="v2-create-error"><span className="v2-status-dot is-danger" />{profileError}<button type="button" onClick={() => { setProfiles([]); void loadProfiles(); }}>تلاش دوباره</button></div> : null}
          {error ? <div className="v2-create-error"><span className="v2-status-dot is-danger" />{error}</div> : null}
          {message ? <p className="v2-create-success">{message}</p> : null}
          <div className="v2-create-actions"><button type="button" onClick={close}>انصراف</button><button type="submit" className="is-primary" disabled={busy || loadingProfiles || !profiles.length}>{busy ? "در حال ساخت و Sync…" : "ساخت کاربر و صدور مجوز"}</button></div>
        </form>}
      </section>
    </div> : null}
  </>;
}
