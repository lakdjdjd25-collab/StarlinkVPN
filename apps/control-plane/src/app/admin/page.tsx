import Link from "next/link";
import { AdminIcon } from "@/components/admin/AdminIcon";
import { db } from "@/lib/db";
import { formatBytes, formatDate } from "@/lib/format";
import { effectiveUsedBytes } from "@/lib/server-access";

export const dynamic = "force-dynamic";

type Tone = "neutral" | "success" | "warning" | "danger" | "vip" | "info";

function formatNumber(value: number): string {
  return new Intl.NumberFormat("fa-IR").format(value);
}

function providerState(provider: {
  lastSyncAt: Date | null;
  lastError: string | null;
} | null, now: Date): { label: string; tone: Tone; detail: string } {
  if (!provider) return { label: "تنظیم نشده", tone: "neutral", detail: "Provider فعالی ثبت نشده است" };
  if (provider.lastError) return { label: "اختلال", tone: "danger", detail: provider.lastError };
  if (!provider.lastSyncAt) return { label: "در انتظار Sync", tone: "warning", detail: "هنوز همگام‌سازی موفقی ثبت نشده است" };
  const age = now.getTime() - provider.lastSyncAt.getTime();
  if (age > 15 * 60_000) return { label: "Stale", tone: "warning", detail: `آخرین Sync: ${formatDate(provider.lastSyncAt)}` };
  return { label: "Synced", tone: "success", detail: `آخرین Sync: ${formatDate(provider.lastSyncAt)}` };
}

function activityTitle(action: string): string {
  const labels: Record<string, string> = {
    "managed_license.create": "کاربر و مجوز جدید ساخته شد",
    "managed_license.update": "اشتراک کاربر به‌روزرسانی شد",
    "managed_license.resetCredentials": "اطلاعات ورود بازنشانی شد",
    "managed_license.migrateProvider": "اشتراک به Provider فعال منتقل شد",
    "service.vipAccess": "دسترسی VIP تغییر کرد",
    "service.create": "سرویس جدید ساخته شد",
    "service.update": "سرویس به‌روزرسانی شد",
    "service.addTraffic": "حجم اشتراک افزایش یافت",
    "service.extend": "اعتبار اشتراک تمدید شد",
    "service.suspend": "اشتراک متوقف شد",
    "service.reactivate": "اشتراک فعال شد",
    "service.deviceLimit": "محدودیت دستگاه تغییر کرد",
    "user.create": "حساب کاربری ساخته شد",
    "user.updateAccess": "دسترسی حساب تغییر کرد",
    "user.suspend": "حساب کاربر معلق شد",
    "user.reactivate": "حساب کاربر فعال شد",
    "device.revoke": "یک دستگاه لغو شد",
    "device.revokeAll": "همه دستگاه‌های کاربر لغو شدند",
    "manualServer.create": "سرور دستی اضافه شد",
    "manualServer.update": "سرور دستی ویرایش شد",
    "manualServer.delete": "سرور دستی حذف شد",
  };
  return labels[action] ?? action;
}

function Metric({ label, value, hint, tone = "neutral" }: {
  label: string;
  value: string;
  hint: string;
  tone?: Tone;
}) {
  return (
    <article className={`v2-metric is-${tone}`}>
      <div className="v2-metric-head"><span>{label}</span><i aria-hidden="true" /></div>
      <strong>{value}</strong>
      <small>{hint}</small>
    </article>
  );
}

function WarningItem({ title, detail, tone = "warning", href }: {
  title: string;
  detail: string;
  tone?: Tone;
  href?: string;
}) {
  const content = (
    <>
      <span className={`v2-status-dot is-${tone}`} />
      <span><strong>{title}</strong><small>{detail}</small></span>
      {href ? <AdminIcon name="chevron-left" size={15} /> : null}
    </>
  );
  return href
    ? <Link className="v2-warning-row" href={href}>{content}</Link>
    : <div className="v2-warning-row">{content}</div>;
}

export default async function DashboardPage() {
  const now = new Date();
  const today = new Date(now); today.setHours(0, 0, 0, 0);
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);
  const sevenDaysAhead = new Date(now.getTime() + 7 * 86_400_000);

  const [
    activeUsers,
    suspendedUsers,
    services,
    nodes,
    enabledManualServers,
    newUsersToday,
    newUsersWeek,
    provider,
    latestRelease,
    activity,
    usageSampleCount,
  ] = await Promise.all([
    db.user.count({ where: { role: "CUSTOMER", status: "ACTIVE" } }),
    db.user.count({ where: { role: "CUSTOMER", status: "SUSPENDED" } }),
    db.service.findMany({
      where: { status: { in: ["ACTIVE", "SUSPENDED"] } },
      select: {
        id: true,
        userId: true,
        status: true,
        quotaBytes: true,
        usedBytes: true,
        manualUsedBytes: true,
        expiresAt: true,
        vipAccess: true,
        user: { select: { status: true } },
      },
    }),
    db.vpnNode.findMany({ select: { id: true, status: true, accessTier: true } }),
    db.manualServer.count({ where: { enabled: true, deletedAt: null } }),
    db.user.count({ where: { role: "CUSTOMER", createdAt: { gte: today } } }),
    db.user.count({ where: { role: "CUSTOMER", createdAt: { gte: weekAgo } } }),
    db.pasarGuardProvider.findFirst({
      where: { active: true },
      orderBy: { updatedAt: "desc" },
      select: { id: true, name: true, lastTestAt: true, lastSyncAt: true, lastError: true },
    }),
    db.appRelease.findFirst({
      where: { platform: "ANDROID", publishedAt: { not: null } },
      orderBy: { versionCode: "desc" },
      select: { versionName: true, versionCode: true, minimumVersionCode: true, mandatory: true, publishedAt: true },
    }),
    db.auditLog.findMany({
      orderBy: { createdAt: "desc" },
      take: 10,
      include: { actor: { select: { email: true } } },
    }),
    db.usageSample.count(),
  ]);

  const operationalServices = services.filter((service) =>
    service.status === "ACTIVE"
    && service.user.status === "ACTIVE"
    && service.expiresAt.getTime() > now.getTime()
    && effectiveUsedBytes(service) < service.quotaBytes,
  );
  const quotaExhausted = services.filter((service) =>
    service.status === "ACTIVE"
    && service.user.status === "ACTIVE"
    && service.expiresAt.getTime() > now.getTime()
    && effectiveUsedBytes(service) >= service.quotaBytes,
  ).length;
  const expiringSoon = operationalServices.filter((service) => service.expiresAt <= sevenDaysAhead).length;
  const activeUserIds = new Set(operationalServices.map((service) => service.userId));
  const vipUserIds = new Set(operationalServices.filter((service) => service.vipAccess).map((service) => service.userId));
  const standardUsers = [...activeUserIds].filter((userId) => !vipUserIds.has(userId)).length;
  const currentAccountedUsage = services.reduce((total, service) => total + effectiveUsedBytes(service), 0n);

  const onlineNodes = nodes.filter((node) => node.status === "ONLINE").length;
  const degradedNodes = nodes.filter((node) => node.status === "DEGRADED").length;
  const offlineNodes = nodes.filter((node) => node.status === "OFFLINE").length;
  const maintenanceNodes = nodes.filter((node) => node.status === "MAINTENANCE").length;
  const vipNodes = nodes.filter((node) => node.accessTier === "VIP").length;
  const providerHealth = providerState(provider, now);

  const warnings: Array<{ title: string; detail: string; tone: Tone; href?: string }> = [];
  if (providerHealth.tone === "danger" || providerHealth.tone === "warning") {
    warnings.push({ title: `VPN Provider: ${providerHealth.label}`, detail: providerHealth.detail, tone: providerHealth.tone, href: "/admin/integrations/pasarguard" });
  }
  if (offlineNodes) warnings.push({ title: `${formatNumber(offlineNodes)} نود Offline`, detail: "سلامت و اتصال نودهای آفلاین را بررسی کنید", tone: "danger", href: "/admin/nodes" });
  if (degradedNodes) warnings.push({ title: `${formatNumber(degradedNodes)} نود Degraded`, detail: "سرویس فعال است اما نیاز به بررسی دارد", tone: "warning", href: "/admin/nodes" });
  if (quotaExhausted) warnings.push({ title: `${formatNumber(quotaExhausted)} اشتراک بدون حجم`, detail: "کاربران مربوطه امکان اتصال معتبر ندارند", tone: "danger", href: "/admin/services" });
  if (expiringSoon) warnings.push({ title: `${formatNumber(expiringSoon)} اشتراک نزدیک انقضا`, detail: "در هفت روز آینده منقضی می‌شوند", tone: "warning", href: "/admin/services" });

  return (
    <div className="v2-dashboard">
      <header className="page-header v2-dashboard-header">
        <div>
          <span className="v2-eyebrow">OPERATIONS OVERVIEW</span>
          <h1>داشبورد</h1>
          <p>نمای عملیاتی کاربران، اشتراک‌ها، سرورها، Provider و رخدادهای NimHUB</p>
        </div>
        <div className="v2-dashboard-status">
          <span className={`v2-status-dot is-${providerHealth.tone}`} />
          <span><small>VPN Provider</small><strong>{providerHealth.label}</strong></span>
        </div>
      </header>

      <section className="v2-kpi-grid" aria-label="شاخص‌های کلیدی">
        <Metric label="کاربران فعال" value={formatNumber(activeUsers)} hint={`+${formatNumber(newUsersToday)} امروز`} tone="success" />
        <Metric label="اشتراک فعال" value={formatNumber(operationalServices.length)} hint={`${formatNumber(expiringSoon)} نزدیک انقضا`} tone={expiringSoon ? "warning" : "neutral"} />
        <Metric label="کاربران معلق" value={formatNumber(suspendedUsers)} hint="Account status" tone={suspendedUsers ? "danger" : "neutral"} />
        <Metric label="بدون حجم" value={formatNumber(quotaExhausted)} hint="Quota exhausted" tone={quotaExhausted ? "danger" : "neutral"} />
        <Metric label="VIP" value={formatNumber(vipUserIds.size)} hint={`${formatNumber(vipNodes)} نود VIP`} tone="vip" />
        <Metric label="Standard" value={formatNumber(standardUsers)} hint="کاربران فعال بدون VIP" />
        <Metric label="سرور Online" value={formatNumber(onlineNodes)} hint={`${formatNumber(enabledManualServers)} Manual فعال`} tone="success" />
        <Metric label="مصرف ثبت‌شده" value={formatBytes(currentAccountedUsage)} hint="مجموع مصرف فعلی سرویس‌ها" tone="info" />
      </section>

      <section className="v2-dashboard-grid">
        <div className="v2-ops-panel v2-server-health-panel">
          <div className="v2-panel-head">
            <div><span className="v2-eyebrow">INFRASTRUCTURE</span><h2>سلامت سرورها</h2></div>
            <Link href="/admin/nodes">مدیریت سرورها <AdminIcon name="chevron-left" size={14} /></Link>
          </div>
          <div className="v2-health-summary">
            <div><span className="v2-status-dot is-success" /><strong>{formatNumber(onlineNodes)}</strong><small>Online</small></div>
            <div><span className="v2-status-dot is-warning" /><strong>{formatNumber(degradedNodes)}</strong><small>Degraded</small></div>
            <div><span className="v2-status-dot is-danger" /><strong>{formatNumber(offlineNodes)}</strong><small>Offline</small></div>
            <div><span className="v2-status-dot is-neutral" /><strong>{formatNumber(maintenanceNodes)}</strong><small>Maintenance</small></div>
          </div>
          <div className="v2-provider-strip">
            <div>
              <span className={`v2-status-dot is-${providerHealth.tone}`} />
              <span><small>{provider?.name ?? "VPN Provider"}</small><strong>{providerHealth.label}</strong></span>
            </div>
            <div><small>آخرین Test</small><strong>{formatDate(provider?.lastTestAt ?? null)}</strong></div>
            <div><small>آخرین Sync</small><strong>{formatDate(provider?.lastSyncAt ?? null)}</strong></div>
          </div>
        </div>

        <div className="v2-ops-panel">
          <div className="v2-panel-head"><div><span className="v2-eyebrow">ATTENTION</span><h2>نیازمند بررسی</h2></div></div>
          <div className="v2-warning-list">
            {warnings.length ? warnings.slice(0, 5).map((warning) => (
              <WarningItem key={`${warning.title}-${warning.detail}`} {...warning} />
            )) : (
              <div className="v2-clear-state"><span className="v2-status-dot is-success" /><span><strong>هشدار بحرانی نداریم</strong><small>وضعیت محلی سیستم سالم است.</small></span></div>
            )}
          </div>
        </div>
      </section>

      <section className="v2-dashboard-grid v2-dashboard-grid-lower">
        <div className="v2-ops-panel">
          <div className="v2-panel-head"><div><span className="v2-eyebrow">QUICK ACTIONS</span><h2>عملیات سریع</h2></div></div>
          <div className="v2-quick-actions">
            <Link href="/admin/services"><span><AdminIcon name="users" /></span><div><strong>کاربر جدید</strong><small>صدور مجوز و حساب NimHUB</small></div><AdminIcon name="chevron-left" size={15} /></Link>
            <Link href="/admin/manual-servers"><span><AdminIcon name="server" /></span><div><strong>افزودن سرور</strong><small>Managed / Manual server workflow</small></div><AdminIcon name="chevron-left" size={15} /></Link>
            <Link href="/admin/notifications"><span><AdminIcon name="bell" /></span><div><strong>ارسال اعلان</strong><small>All یا Selected users</small></div><AdminIcon name="chevron-left" size={15} /></Link>
            <Link href="/admin/settings/releases"><span><AdminIcon name="settings" /></span><div><strong>انتشار نسخه</strong><small>{latestRelease ? `فعلی: ${latestRelease.versionName}` : "نسخه فعالی ثبت نشده"}</small></div><AdminIcon name="chevron-left" size={15} /></Link>
          </div>
          <div className="v2-dashboard-facts">
            <span><small>کاربر جدید / ۷ روز</small><strong>{formatNumber(newUsersWeek)}</strong></span>
            <span><small>Android فعال</small><strong dir="ltr">{latestRelease ? `${latestRelease.versionName} (${latestRelease.versionCode})` : "—"}</strong></span>
            <span><small>Minimum version</small><strong dir="ltr">{latestRelease?.minimumVersionCode ?? "—"}</strong></span>
            <span><small>Update اجباری</small><strong>{latestRelease?.mandatory ? "بله" : "خیر"}</strong></span>
          </div>
        </div>

        <div className="v2-ops-panel">
          <div className="v2-panel-head"><div><span className="v2-eyebrow">RECENT ACTIVITY</span><h2>فعالیت‌های اخیر</h2></div></div>
          <div className="v2-activity-list">
            {activity.length ? activity.map((item) => (
              <div className="v2-activity-row" key={item.id.toString()}>
                <span className="v2-activity-icon"><AdminIcon name="activity" size={15} /></span>
                <span><strong>{activityTitle(item.action)}</strong><small>{item.actor?.email ?? "System"} · {formatDate(item.createdAt)}</small></span>
                <code dir="ltr">{item.entityType}</code>
              </div>
            )) : <div className="v2-clear-state"><span><strong>هنوز فعالیتی ثبت نشده است</strong><small>AuditLogهای جدید در این بخش نمایش داده می‌شوند.</small></span></div>}
          </div>
        </div>
      </section>

      {!usageSampleCount ? (
        <section className="v2-data-note">
          <AdminIcon name="activity" size={17} />
          <div><strong>Traffic Trend هنوز نمایش داده نمی‌شود</strong><p>برای جلوگیری از Fake Data، تا زمانی که UsageSample واقعی و قابل اتکا ثبت نشود نمودار روز/هفته/ماه ساخته نمی‌شود. مصرف فعلی بالا از accounting واقعی سرویس‌ها خوانده می‌شود.</p></div>
        </section>
      ) : null}
    </div>
  );
}
