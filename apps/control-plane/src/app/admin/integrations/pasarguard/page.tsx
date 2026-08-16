import {
  PasarGuardIntegrationForm,
  PasarGuardPlanMappingForm,
  PasarGuardProviderForm,
  PasarGuardSyncButton,
} from "@/components/EntityForms";
import { db } from "@/lib/db";
import { formatBytes, formatDate } from "@/lib/format";
import {
  activePasarGuardProviderSummary,
  isPasarGuardConfigured,
  syncActivePasarGuardProfiles,
} from "@/lib/pasarguard/provider";

export const dynamic = "force-dynamic";

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
        service: { include: { user: { select: { email: true } }, plan: { select: { id: true, name: true } } } },
        nodes: { select: { id: true, name: true, status: true } },
      },
    }),
    db.plan.findMany({ where: { isActive: true }, orderBy: { name: "asc" }, select: { id: true, name: true } }),
    db.pasarGuardPlanMapping.findMany({
      orderBy: { updatedAt: "desc" },
      include: { provider: { select: { id: true, name: true, active: true } }, plan: { select: { id: true, name: true } } },
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

  return (
    <>
      <header className="page-header">
        <div>
          <h1>اتصال پاسارگارد</h1>
          <p>تعویض امن Provider، Sync پویا و Mapping مستقل پلن‌ها</p>
        </div>
        <span className={configured && activeProvider ? "badge green" : "badge red"}>
          {configured && activeProvider ? "پنل فعال متصل است" : "نیازمند تنظیم اتصال"}
        </span>
      </header>

      <section className="card section">
        <div className="section-title"><h2>پنل فعال و تعویض Provider</h2></div>
        {activeProvider ? (
          <div style={{ marginBottom: 16, color: "var(--muted)" }}>
            <strong style={{ color: "var(--text)" }}>{activeProvider.name}</strong><br />
            <span dir="ltr">{activeProvider.baseUrl}</span><br />
            <span dir="ltr">{activeProvider.username}</span><br />
            آخرین تست: {formatDate(activeProvider.lastTestAt)} — آخرین Sync: {formatDate(activeProvider.lastSyncAt)}
            {activeProvider.lastError ? <><br /><span className="error">{activeProvider.lastError}</span></> : null}
          </div>
        ) : <div className="empty" style={{ marginBottom: 16 }}>هنوز Provider فعالی ثبت نشده است.</div>}
        <PasarGuardProviderForm
          configured={configured}
          baseUrl={activeProvider?.baseUrl ?? ""}
          username={activeProvider?.username ?? ""}
        />
        {profileError ? <p className="error">{profileError}</p> : null}
      </section>

      <section className="card section">
        <div className="section-title"><h2>Mapping پلن NimHUB به Group / Template</h2></div>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          نام و IDهای PasarGuard در کد ثابت نیستند. بعد از تعویض پنل، Mapping نامعتبر باید صریحاً به Group/Template جدید متصل شود.
        </p>
        <PasarGuardPlanMappingForm
          plans={plans.map((plan) => ({ id: plan.id, label: plan.name }))}
          profiles={profiles}
        />
        <div className="table-wrap" style={{ marginTop: 18 }}><table>
          <thead><tr><th>پلن NimHUB</th><th>Provider</th><th>Group / Template</th><th>وضعیت</th><th>بررسی</th></tr></thead>
          <tbody>{mappings.map((mapping) => <tr key={mapping.id}>
            <td>{mapping.plan.name}</td>
            <td>{mapping.provider.name}</td>
            <td>{mapping.profileName}<br /><small dir="ltr">{mapping.profileKey}</small></td>
            <td><span className={mapping.valid && mapping.provider.active ? "badge green" : "badge red"}>{mapping.valid && mapping.provider.active ? "معتبر" : "نیازمند تنظیم مجدد"}</span></td>
            <td>{formatDate(mapping.lastValidatedAt)}</td>
          </tr>)}</tbody>
        </table></div>
      </section>

      <section className="card section">
        <div className="section-title"><h2>اتصال دستی کاربر پنل به حساب NimHUB</h2></div>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          عملیات روی Provider فعال انجام می‌شود. رمز مدیر و Config خام هیچ‌وقت به مرورگر یا Android ارسال نمی‌شود.
        </p>
        <PasarGuardIntegrationForm configured={configured} quickPingUsers={users.map((user) => ({ id: user.id, label: user.email }))} />
      </section>

      <section className="card section">
        <div className="section-title"><h2>Bindingهای سرویس</h2></div>
        {bindings.length ? (
          <div className="table-wrap"><table>
            <thead><tr><th>کاربر پاسارگارد</th><th>حساب NimHUB</th><th>پلن</th><th>Provider</th><th>مصرف</th><th>سرورها</th><th>Sync</th><th>کنترل</th></tr></thead>
            <tbody>{bindings.map((binding) => {
              const needsMigration = Boolean(activeProvider && binding.providerId !== activeProvider.id);
              return <tr key={binding.id}>
                <td><strong dir="ltr">{binding.externalUsername}</strong><br /><small dir="ltr">#{String(binding.externalUserId)}</small></td>
                <td dir="ltr">{binding.service.user.email}</td>
                <td>{binding.service.plan.name}</td>
                <td>{binding.provider?.name ?? "Legacy"}<br />{needsMigration ? <span className="badge red">نیازمند انتقال</span> : <span className="badge green">فعال</span>}</td>
                <td>{formatBytes(binding.service.usedBytes)} / {formatBytes(binding.service.quotaBytes)}</td>
                <td><span className="badge blue">{binding.nodes.length} سرور</span></td>
                <td>{formatDate(binding.lastSyncAt)}{binding.lastError ? <><br /><span className="error">{binding.lastError}</span></> : null}</td>
                <td><PasarGuardSyncButton bindingId={binding.id} /></td>
              </tr>;
            })}</tbody>
          </table></div>
        ) : <div className="empty">هنوز کاربری از پاسارگارد متصل نشده است.</div>}
      </section>
    </>
  );
}
