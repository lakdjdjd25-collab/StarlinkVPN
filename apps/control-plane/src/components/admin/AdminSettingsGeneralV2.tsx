import Link from "next/link";
import { AdminIcon } from "./AdminIcon";

type SettingItem = {
  key: string;
  value: unknown;
  description: string | null;
};

function valueSummary(value: unknown): string {
  if (value === null) return "Null";
  if (Array.isArray(value)) return `${value.length} item`;
  if (typeof value === "object") return `${Object.keys(value as Record<string, unknown>).length} field`;
  if (typeof value === "boolean") return value ? "Enabled" : "Disabled";
  if (typeof value === "string") return value ? "Configured" : "Empty";
  if (typeof value === "number") return "Configured";
  return "Configured";
}

function friendlyName(key: string): string {
  if (key === "client.bootstrap") return "Client Bootstrap";
  return key.split(".").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" / ");
}

export function AdminSettingsGeneralV2({ settings }: { settings: SettingItem[] }) {
  const bootstrap = settings.find((setting) => setting.key === "client.bootstrap") ?? null;
  const other = settings.filter((setting) => setting.key !== "client.bootstrap");

  return (
    <div className="v2-settings-general">
      <section className="v2-settings-overview-grid">
        <article className="v2-settings-overview-card">
          <span className="v2-settings-card-icon"><AdminIcon name="activity" size={17} /></span>
          <div><small>Client Bootstrap</small><strong>{bootstrap ? "Configured" : "Default / Empty"}</strong><p>تنظیمات عمومی‌ای که Backend در پاسخ Bootstrap به کلاینت تحویل می‌دهد.</p></div>
          <span className={`v2-settings-state is-${bootstrap ? "success" : "neutral"}`}>{bootstrap ? valueSummary(bootstrap.value) : "No override"}</span>
        </article>
        <article className="v2-settings-overview-card">
          <span className="v2-settings-card-icon"><AdminIcon name="users" size={17} /></span>
          <div><small>Management</small><strong>Typed setting</strong><p>اطلاعات خرید و پشتیبانی از بخش Management و بدون ویرایش JSON مدیریت می‌شود.</p></div>
          <Link href="/admin/management">باز کردن</Link>
        </article>
        <article className="v2-settings-overview-card">
          <span className="v2-settings-card-icon"><AdminIcon name="server" size={17} /></span>
          <div><small>VPN Provider</small><strong>Isolated provider settings</strong><p>اتصال، Sync، Mapping و Migration پاسارگارد از تنظیمات عمومی جدا است.</p></div>
          <Link href="/admin/integrations/pasarguard">باز کردن</Link>
        </article>
        <article className="v2-settings-overview-card">
          <span className="v2-settings-card-icon"><AdminIcon name="activity" size={17} /></span>
          <div><small>App Releases</small><strong>Version lifecycle</strong><p>انتشار Android، نسخه اجباری و لینک دانلود در صفحه اختصاصی Release مدیریت می‌شود.</p></div>
          <Link href="/admin/settings/releases">باز کردن</Link>
        </article>
      </section>

      <section className="v2-settings-catalog">
        <div className="v2-settings-section-head">
          <div><span className="v2-eyebrow">CONFIGURATION CATALOG</span><h2>تنظیمات شناخته‌شده</h2><p>مقادیر خام در این صفحه نمایش داده نمی‌شوند؛ JSON فقط در Advanced قابل ویرایش است.</p></div>
          <Link className="v2-settings-secondary-link" href="/admin/settings/advanced"><AdminIcon name="sliders" size={14} />Advanced</Link>
        </div>
        <div className="v2-settings-catalog-list">
          {settings.length ? settings.map((setting) => (
            <div className="v2-settings-catalog-row" key={setting.key}>
              <span><strong>{friendlyName(setting.key)}</strong><code dir="ltr">{setting.key}</code></span>
              <span>{setting.description || (setting.key === "client.bootstrap" ? "تنظیمات عمومی Bootstrap کلاینت" : "بدون توضیح ثبت‌شده")}</span>
              <span className="v2-settings-state is-neutral">{valueSummary(setting.value)}</span>
            </div>
          )) : <div className="v2-settings-empty"><AdminIcon name="settings" size={20} /><strong>Override عمومی ثبت نشده است</strong><span>سامانه از رفتار پیش‌فرض Backend استفاده می‌کند.</span></div>}
        </div>
      </section>

      {other.length ? <div className="v2-settings-note"><AdminIcon name="sliders" size={15} /><span>{other.length} کلید سفارشی دیگر ثبت شده است؛ برای جلوگیری از نمایش داده فنی در جریان روزمره، مقدار آن‌ها فقط در Advanced دیده می‌شود.</span></div> : null}
    </div>
  );
}
