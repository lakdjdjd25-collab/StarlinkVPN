"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

type BatchResponse = {
  data?: {
    processed?: number;
    migratedCount?: number;
    failed?: Array<{ bindingId: string; message: string }>;
    hasMoreEligible?: boolean;
  };
  error?: { message?: string };
};

export function PasarGuardMigrationPanel({
  pendingCount,
  readyCount,
  blockedCount,
}: {
  pendingCount: number;
  readyCount: number;
  blockedCount: number;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function migrateAll() {
    setBusy(true);
    setError("");
    setMessage("شروع انتقال امن کاربران آماده…");
    const failedIds = new Set<string>();
    let migratedTotal = 0;
    let failedTotal = 0;
    try {
      for (let batch = 0; batch < 100; batch += 1) {
        const response = await fetch("/api/v1/admin/integrations/pasarguard", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({
            action: "migrate_batch",
            limit: 20,
            excludeBindingIds: [...failedIds],
          }),
        });
        const body = await response.json().catch(() => null) as BatchResponse | null;
        if (!response.ok) throw new Error(body?.error?.message ?? "انتقال گروهی انجام نشد");
        const migrated = body?.data?.migratedCount ?? 0;
        const failures = body?.data?.failed ?? [];
        migratedTotal += migrated;
        failedTotal += failures.length;
        failures.forEach((failure) => failedIds.add(failure.bindingId));
        setMessage(`انتقال: ${migratedTotal} موفق${failedTotal ? ` — ${failedTotal} نیازمند بررسی` : ""}`);
        if (!body?.data?.hasMoreEligible || (body.data.processed ?? 0) === 0) break;
      }
      if (failedTotal) {
        setError(`${failedTotal} سرویس به دلیل وضعیت خاص نیازمند بررسی دستی است؛ بقیه کاربران منتقل شدند.`);
      }
      router.refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "انتقال گروهی متوقف شد");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card section">
      <div className="section-title"><h2>انتقال کاربران پنل قبلی</h2></div>
      <p style={{ color: "var(--muted)", marginTop: 0 }}>
        این شمارش پیش‌نمایش است و چیزی را تغییر نمی‌دهد. هنگام انتقال، مصرف قبلی، حجم باقی‌مانده، تاریخ انقضا و تعداد دستگاه حفظ می‌شود.
      </p>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", margin: "16px 0" }}>
        <span className="badge blue">{pendingCount} نیازمند انتقال</span>
        <span className="badge green">{readyCount} آماده انتقال</span>
        <span className={blockedCount ? "badge red" : "badge green"}>{blockedCount} نیازمند بررسی</span>
      </div>
      <p style={{ color: "var(--muted)", fontSize: 13 }}>
        تا وقتی انتقال یک سرویس تأیید نشده، اپ هیچ سروری از پنل قبلی به آن کاربر نمی‌دهد و وضعیت سرویس «در حال بررسی» است. بعد از انتقال موفق، سرورهای پنل جدید خودکار Sync می‌شوند.
      </p>
      {message ? <p style={{ color: "var(--success)", fontSize: 13 }}>{message}</p> : null}
      {error ? <p className="error">{error}</p> : null}
      <button className="button" type="button" disabled={busy || readyCount === 0} onClick={migrateAll}>
        {busy ? "در حال انتقال گروهی…" : `انتقال همه ${readyCount} کاربر آماده`}
      </button>
    </section>
  );
}
