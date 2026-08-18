import { GlobalSettingForm } from "@/components/EntityForms";
import { AdminOperatorAccountsV2 } from "@/components/admin/AdminOperatorAccountsV2";
import { AdminSettingsTabs } from "@/components/admin/AdminSettingsTabs";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

export default async function AdvancedSettingsPage() {
  const [settings, operators] = await Promise.all([
    db.globalSetting.findMany({ orderBy: { key: "asc" } }),
    db.user.findMany({
      where: { role: { in: ["ADMIN", "SUPPORT"] }, status: { not: "DELETED" } },
      orderBy: { createdAt: "desc" },
      select: { id: true, email: true, role: true, status: true, createdAt: true },
    }),
  ]);

  return (
    <>
      <header className="page-header"><div><span className="v2-eyebrow">SYSTEM SETTINGS</span><h1>تنظیمات</h1><p>ابزارهای فنی، Operator accounts و JSON خام؛ خارج از جریان روزمره مشتری.</p></div></header>
      <AdminSettingsTabs />

      <div className="v2-settings-warning">
        <strong>Advanced configuration</strong>
        <p>برای Management، Provider و App Release از تب‌های اختصاصی استفاده کن. این بخش برای عملیات سیستمی و تنظیماتی است که UI تایپ‌شده ندارند.</p>
      </div>

      <details className="v2-settings-editor" id="accounts">
        <summary><span><strong>Admin / Support accounts</strong><small>مدیریت Operatorهای پنل؛ حساب‌های مشتری در Users V2 مدیریت می‌شوند</small></span><span>+</span></summary>
        <div className="v2-settings-editor-body">
          <AdminOperatorAccountsV2 operators={operators.map((operator) => ({
            id: operator.id,
            email: operator.email,
            role: operator.role === "ADMIN" ? "ADMIN" : "SUPPORT",
            status: operator.status,
            createdAt: operator.createdAt.toISOString(),
          }))} />
        </div>
      </details>

      <details className="v2-settings-editor">
        <summary><span><strong>ثبت یا ویرایش Raw Setting</strong><small>کلید + JSON؛ فقط برای تنظیماتی که UI تایپ‌شده ندارند</small></span><span>+</span></summary>
        <div className="v2-settings-editor-body"><GlobalSettingForm /></div>
      </details>

      <section className="v2-settings-catalog">
        <div className="v2-settings-section-head"><div><span className="v2-eyebrow">RAW CONFIGURATION</span><h2>Global Settings</h2><p>نمای فنی مقدارهای فعلی دیتابیس.</p></div></div>
        <div className="v2-settings-raw-list">
          {settings.length ? settings.map((setting) => (
            <article key={setting.key}>
              <header><code dir="ltr">{setting.key}</code><span className="v2-settings-state is-neutral">{setting.description ? "Documented" : "Raw"}</span></header>
              {setting.description ? <p>{setting.description}</p> : null}
              <pre dir="ltr">{JSON.stringify(setting.value, null, 2)}</pre>
            </article>
          )) : <div className="v2-settings-empty"><strong>GlobalSetting خالی است</strong><span>هیچ override خامی ثبت نشده است.</span></div>}
        </div>
      </section>
    </>
  );
}
