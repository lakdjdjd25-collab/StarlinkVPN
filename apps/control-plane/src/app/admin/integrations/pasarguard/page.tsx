import { PasarGuardIntegrationForm, PasarGuardSyncButton } from "@/components/EntityForms";
import { db } from "@/lib/db";
import { formatBytes, formatDate } from "@/lib/format";
import { isPasarGuardConfigured } from "@/lib/pasarguard/client";

export const dynamic = "force-dynamic";

export default async function PasarGuardPage() {
  const [users, bindings] = await Promise.all([
    db.user.findMany({
      where: { status: "ACTIVE" },
      orderBy: { email: "asc" },
      select: { id: true, email: true },
    }),
    db.pasarGuardBinding.findMany({
      orderBy: { createdAt: "desc" },
      include: {
        service: { include: { user: { select: { email: true } } } },
        nodes: { select: { id: true, name: true, status: true } },
      },
    }),
  ]);
  const configured = isPasarGuardConfigured();

  return (
    <>
      <header className="page-header">
        <div>
          <h1>اتصال پاسارگارد</h1>
          <p>دریافت امن کاربران، حجم، انقضا و کانفیگ‌های sing-box</p>
        </div>
        <span className={configured ? "badge green" : "badge red"}>
          {configured ? "Secretها تنظیم شده‌اند" : "نیازمند Secretهای Railway"}
        </span>
      </header>

      <section className="card section">
        <div className="section-title"><h2>اتصال کاربر پنل به حساب QuickPing</h2></div>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          رمز مدیر و توکن اشتراک در مرورگر یا اپ نمایش داده نمی‌شود. پس از اتصال، سرورها با پیکربندی رمزگذاری‌شده وارد می‌شوند.
        </p>
        <PasarGuardIntegrationForm
          configured={configured}
          quickPingUsers={users.map((user) => ({ id: user.id, label: user.email }))}
        />
      </section>

      <section className="card section">
        <div className="section-title"><h2>اتصال‌های فعال</h2></div>
        {bindings.length ? (
          <div className="table-wrap"><table>
            <thead><tr><th>کاربر پاسارگارد</th><th>حساب QuickPing</th><th>مصرف</th><th>انقضا</th><th>سرورها</th><th>آخرین همگام‌سازی</th><th>کنترل</th></tr></thead>
            <tbody>{bindings.map((binding) => <tr key={binding.id}>
              <td><strong dir="ltr">{binding.externalUsername}</strong><br /><small dir="ltr">#{String(binding.externalUserId)}</small></td>
              <td dir="ltr">{binding.service.user.email}</td>
              <td>{formatBytes(binding.service.usedBytes)} / {formatBytes(binding.service.quotaBytes)}</td>
              <td>{formatDate(binding.service.expiresAt)}</td>
              <td><span className="badge blue">{binding.nodes.length} سرور</span></td>
              <td>{formatDate(binding.lastSyncAt)}{binding.lastError ? <><br /><span className="error">{binding.lastError}</span></> : null}</td>
              <td><PasarGuardSyncButton bindingId={binding.id} /></td>
            </tr>)}</tbody>
          </table></div>
        ) : <div className="empty">هنوز کاربری از پاسارگارد متصل نشده است.</div>}
      </section>
    </>
  );
}
