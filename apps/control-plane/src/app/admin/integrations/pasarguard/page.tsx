import {
  PasarGuardIntegrationForm,
  PasarGuardPlanMappingForm,
  PasarGuardProviderForm,
  PasarGuardSyncButton,
} from "@/components/EntityForms";
import { PasarGuardMigrationPanel } from "@/components/PasarGuardMigrationPanel";
import { AdminSettingsTabs } from "@/components/admin/AdminSettingsTabs";
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
      where: { role: "CUSTOMER", status: { not: "DELETED" } },
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
  const syncedBindings = activeProvider ? bindings.filter((binding) => binding.providerId === activeProvider.id).length : 0;
  const validMappings = activeMappings.filter((mapping) => mapping.valid).length;

  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">SYSTEM SETTINGS</span>
          <h1>تنظیمات</h1>
          <p>اتصال Provider، Mapping پلن‌ها و Migration کاربران در بخش اختصاصی VPN Provider.</p>
        </div>
      </header>
      <AdminSettingsTabs />

      <div className="v2-provider-summary">
        <div><small>Provider</small><strong>{activeProvider?.name ?? "Not configured"}</strong></div>
        <div><small>Connection</small><span className={`v2-settings-state is-${configured && activeProvider && !activeProvider.lastError ? "success" : "warning"}`}>{configured && activeProvider ? (activeProvider.lastError ? "Needs attention" : "Configured") : "Setup required"}</span></div>
        <div><small>Plan Mapping</small><strong>{validMappings} / {plans.length}</strong></div>
        <div><small>Synced Services</small><strong>{syncedBindings}</strong></div>
        <div><small>Migration Queue</small><strong>{pendingBindings.length}</strong></div>
      </div>

      <section className="v2-provider-section">
        <div className="v2-settings-section-head">
          <div><span className="v2-eyebrow">CONNECTION</span><h2>پنل فعال</h2><p>Credentialها backend-only باقی می‌مانند و فقط پس از تست واقعی فعال می‌شوند.</p></div>
          <span className={`v2-settings-state is-${configured && activeProvider && !activeProvider.lastError ? "success" : "warning"}`}>{configured && activeProvider ? "Connected configuration" : "Setup required"}</span>
        </div>
        <div className="v2-provider-current">
          {activeProvider ? <>
            <div><small>Name</small><strong>{activeProvider.name}</strong></div>
            <div><small>Base URL</small><code dir="ltr">{activeProvider.baseUrl}</code></div>
            <div><small>Last test</small><strong>{formatDate(activeProvider.lastTestAt)}</strong></div>
            <div><small>Last sync</small><strong>{formatDate(activeProvider.lastSyncAt)}</strong></div>
          </> : <div className="v2-settings-empty"><strong>Provider فعالی ثبت نشده است</strong><span>اتصال جدید را در فرم پایین تست و فعال کن.</span></div>}
        </div>
        {activeProvider?.lastError ? <div className="v2-provider-error"><strong>Needs attention</strong><code dir="ltr">{activeProvider.lastError}</code></div> : null}
        <div className="v2-provider-form"><PasarGuardProviderForm configured={configured} baseUrl={activeProvider?.baseUrl ?? ""} username={activeProvider?.username ?? ""} />{profileError ? <p className="error">{profileError}</p> : null}</div>
      </section>

      <section className="v2-provider-section">
        <div className="v2-settings-section-head"><div><span className="v2-eyebrow">PLAN MAPPING</span><h2>پلن‌ها و گروه‌ها</h2><p>برای هر پلن NimHUB مقصد Group/Template پنل فعال را یک‌بار مشخص کن.</p></div><span className="v2-settings-state is-neutral">{profiles.length} profile</span></div>
        <div className="v2-provider-form"><PasarGuardPlanMappingForm plans={plans.map((plan) => ({ id: plan.id, label: planLabel(plan) }))} profiles={profiles} /></div>
        <div className="v2-provider-mapping-list">
          {activeMappings.length ? activeMappings.map((mapping) => <div key={mapping.id}><span><strong>{planLabel(mapping.plan)}</strong><small>{mapping.profileName}</small></span><span className={`v2-settings-state is-${mapping.valid ? "success" : "warning"}`}>{mapping.valid ? "Ready" : "Remap required"}</span></div>) : <div className="v2-settings-empty"><strong>Mapping ثبت نشده است</strong><span>پس از Sync پروفایل‌ها، مقصد هر پلن را انتخاب کن.</span></div>}
        </div>
      </section>

      <div className="v2-provider-migration"><PasarGuardMigrationPanel pendingCount={pendingBindings.length} readyCount={readyBindings.length} blockedCount={blockedBindings.length} /></div>

      <details className="v2-provider-advanced">
        <summary><span><strong>Advanced diagnostics</strong><small>Manual binding، وضعیت فنی سرویس‌ها و Sync موردی</small></span><span>+</span></summary>
        <div className="v2-provider-advanced-body">
          <section>
            <div className="v2-settings-section-head"><div><span className="v2-eyebrow">MANUAL BINDING</span><h2>اتصال دستی کاربر</h2><p>برای عیب‌یابی یا موارد استثنایی؛ در جریان عادی لازم نیست.</p></div></div>
            <div className="v2-provider-form"><PasarGuardIntegrationForm configured={configured} quickPingUsers={users.map((user) => ({ id: user.id, label: user.email }))} /></div>
          </section>
          <section>
            <div className="v2-settings-section-head"><div><span className="v2-eyebrow">BINDINGS</span><h2>وضعیت فنی سرویس‌ها</h2><p>Binding، Provider و Nodeهای Sync‌شده.</p></div><span className="v2-settings-state is-neutral">{bindings.length} binding</span></div>
            <div className="v2-provider-binding-table"><table><thead><tr><th>Account</th><th>Plan</th><th>Provider</th><th>Usage</th><th>Nodes</th><th>Sync</th></tr></thead><tbody>{bindings.map((binding) => { const needsMigration = Boolean(activeProvider && binding.providerId !== activeProvider.id); return <tr key={binding.id}><td dir="ltr">{binding.service.user.email}</td><td>{planLabel(binding.service.plan)}</td><td><span className={`v2-settings-state is-${needsMigration ? "warning" : "success"}`}>{needsMigration ? "Migration required" : "Current"}</span></td><td>{formatBytes(binding.service.usedBytes)} / {formatBytes(binding.service.quotaBytes)}</td><td>{needsMigration ? 0 : binding.nodes.length}</td><td><PasarGuardSyncButton bindingId={binding.id} /></td></tr>; })}</tbody></table>{!bindings.length ? <div className="v2-settings-empty"><strong>Binding وجود ندارد</strong><span>هنوز سرویسی به Provider متصل نشده است.</span></div> : null}</div>
          </section>
        </div>
      </details>
    </>
  );
}
