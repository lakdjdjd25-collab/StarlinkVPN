import {
  PasarGuardIntegrationForm,
  PasarGuardPlanMappingForm,
  PasarGuardProviderForm,
  PasarGuardSyncButton,
} from "@/components/EntityForms";
import { PasarGuardMigrationPanel } from "@/components/PasarGuardMigrationPanel";
import { db } from "@/lib/db";
import { formatBytes, formatDate } from "@/lib/format";
import {
  activePasarGuardProviderSummary,
  isPasarGuardConfigured,
  syncActivePasarGuardProfiles,
} from "@/lib/pasarguard/provider";

export const dynamic = "force-dynamic";

type PlanLabelInput = {
  name: string;
  dataLimitBytes: bigint;
  durationDays: number;
  maxDevices: number;
};

function planLabel(plan: PlanLabelInput): string {
  const details = `${formatBytes(plan.dataLimitBytes)} • ${plan.durationDays} روز • ${plan.maxDevices} دستگاه`;
  return plan.name.startsWith("NimHUB Managed ") ? details : `${plan.name} — ${details}`;
}

export default async function PasarGuardPage() {
  const configured = await isPasarGuardConfigured();
  const activeProvider = configured ? await activePasarGuardProviderSummary() : null;
  const [users, bindings, plans, mappings] = await Promise.all([
    db.user.findMany({
      where: { status: "ACTIVE" },
      orderBy: { email: "asc" },
      select: { id: true, email: true },
    }),
    db.pasarGuardBinding.findMany({
      orderBy: { createdAt: "desc" },
      include: {
        provider: { select: { id: true, name: true, baseUrl: true, active: true } },
        service: {
          include: {
            user: { select: { email: true } },
            plan: { select: { id: true, name: true, dataLimitBytes: true, durationDays: true, maxDevices: true } },
          },
        },
        nodes: { select: { id: true, name: true, status: true } },
      },
    }),
    db.plan.findMany({
      where: { isActive: true },
      orderBy: { name: "asc" },
      select: { id: true, name: true, dataLimitBytes: true, durationDays: true, maxDevices: true },
    }),
    db.pasarGuardPlanMapping.findMany({
      orderBy: { updatedAt: "desc" },
      include: {
        provider: { select: { id: true, name: true, active: true } },
        plan: { select: { id: true, name: true, dataLimitBytes: true, durationDays: true, maxDevices: true } },
      },
    }),
  ]);

  let profiles: Array<{ key: string; name: string; kind: "template" | "group" }> = [];
  let profileError = "";
  if (configured) {
    try {
      profiles = (await syncActivePasarGuardProfiles()).profiles.map((profile) => ({
        key: profile.key,
        name: profile.name,
        kind: profile.kind,
      }));
    } catch (error) {
      profileError = error instanceof Error ? error.message : "دریافت گروه‌ها و قالب‌ها انجام نشد";
    }
  }

  const activeMappings = activeProvider
    ? mappings.filter((mapping) => mapping.providerId === activeProvider.id)
    : [];
  const mappedPlanIds = new Set(activeMappings.filter((mapping) => mapping.valid).map((mapping) => mapping.planId));
  const now = Date.now();
  const pendingBindings = activeProvider
    ? bindings.filter((binding) =>
        binding.providerId !== activeProvider.id
        && ["ACTIVE", "SUSPENDED"].includes(binding.service.status)
        && binding.service.expiresAt.getTime() > now,
      )
    : [];
  const readyBindings = pendingBindings.filter((binding) =>
    mappedPlanIds.has(binding.service.planId) && binding.service.usedBytes < binding.service.quotaBytes,
  );
  const blockedBindings = pendingBindings.filter((binding) => !readyBindings.includes(binding));

  return (
    <>
      <header className="page-header">
        <div>
          <h1>مدیریت پنل VPN</h1>
          <p>تعویض پنل، انتخاب گروه پلن‌ها و انتقال خودکار کاربران</p>
        </div>
        <span className={configured && activeProvider ? "badge green" : "badge red"}>
          {configured && activeProvider ? "پنل فعال متصل است" : "نیازمند تنظیم اتصال"}
        </span>
      </header>

      <section className="card section">
        <div className="section-title"><h2>پنل فعال</h2></div>
        {activeProvider ? (
          <div style={{ marginBottom: 16, color: "var(--muted)" }}>
            <strong style={{ color: "var(--text)" }}>{activeProvider.name}</strong><br />
            <span dir="ltr">{activeProvider.baseUrl}</span><br />
            آخرین تست: {formatDate(activeProvider.lastTestAt)} — آخرین Sync: {formatDate(activeProvider.lastSyncAt)}
            {activeProvider.lastError ? <><br /><span className="error">{activeProvider.lastError}</span></> : null}
          </div>
        ) : <div className="empty" style={{ marginBottom: 16 }}>هنوز پنل فعالی ثبت نشده است.</div>}
        <PasarGuardProviderForm
          configured={configured}
          baseUrl={activeProvider?.baseUrl ?? ""}
          username={activeProvider?.username ?? ""}
        />
        {profileError ? <p className="error">{profileError}</p> : null}
      </section>

      <section className="card section">
        <div className="section-title"><h2>پلن‌های پنل فعال</h2></div>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          فقط یک‌بار مشخص کن هر پلن NimHUB در پنل فعال داخل کدام گروه یا قالب ساخته شود.
        </p>
        <PasarGuardPlanMappingForm
          plans={plans.map((plan) => ({ id: plan.id, label: planLabel(plan) }))}
          profiles={profiles}
        />
        {activeMappings.length ? (
          <div className="table-wrap" style={{ marginTop: 18 }}><table>
            <thead><tr><th>پلن</th><th>گروه / قالب پنل فعال</th><th>وضعیت</th></tr></thead>
            <tbody>{activeMappings.map((mapping) => <tr key={mapping.id}>
              <td>{planLabel(mapping.plan)}</td>
              <td>{mapping.profileName}</td>
              <td><span className={mapping.valid ? "badge green" : "badge red"}>{mapping.valid ? "آماده" : "نیازمند انتخاب دوباره"}</span></td>
            </tr>)}</tbody>
          </table></div>
        ) : <div className="empty" style={{ marginTop: 16 }}>هنوز برای پنل فعال، گروهی به پلن‌ها اختصاص داده نشده است.</div>}
      </section>

      <PasarGuardMigrationPanel
        pendingCount={pendingBindings.length}
        readyCount={readyBindings.length}
        blockedCount={blockedBindings.length}
      />

      <details className="card section">
        <summary style={{ cursor: "pointer", fontWeight: 700, fontSize: 18 }}>تنظیمات پیشرفته و عیب‌یابی</summary>
        <p style={{ color: "var(--muted)" }}>
          این بخش برای اتصال دستی یا بررسی فنی است و برای انتقال عادی کاربران لازم نیست.
        </p>
        <div style={{ marginTop: 20 }}>
          <h3>اتصال دستی یک کاربر</h3>
          <PasarGuardIntegrationForm configured={configured} quickPingUsers={users.map((user) => ({ id: user.id, label: user.email }))} />
        </div>
        <div style={{ marginTop: 28 }}>
          <h3>وضعیت فنی سرویس‌ها</h3>
          {bindings.length ? (
            <div className="table-wrap"><table>
              <thead><tr><th>حساب NimHUB</th><th>پلن</th><th>وضعیت پنل</th><th>مصرف</th><th>سرورها</th><th>کنترل</th></tr></thead>
              <tbody>{bindings.map((binding) => {
                const needsMigration = Boolean(activeProvider && binding.providerId !== activeProvider.id);
                return <tr key={binding.id}>
                  <td dir="ltr">{binding.service.user.email}</td>
                  <td>{planLabel(binding.service.plan)}</td>
                  <td>{needsMigration ? <span className="badge red">در حال بررسی</span> : <span className="badge green">تأیید شده</span>}</td>
                  <td>{formatBytes(binding.service.usedBytes)} / {formatBytes(binding.service.quotaBytes)}</td>
                  <td><span className="badge blue">{needsMigration ? 0 : binding.nodes.length} سرور</span></td>
                  <td><PasarGuardSyncButton bindingId={binding.id} /></td>
                </tr>;
              })}</tbody>
            </table></div>
          ) : <div className="empty">سرویسی متصل نشده است.</div>}
        </div>
      </details>
    </>
  );
}
