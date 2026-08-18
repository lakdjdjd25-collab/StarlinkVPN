import { ManagementInfoForm } from "@/components/EntityForms";
import { AdminSettingsTabs } from "@/components/admin/AdminSettingsTabs";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

export default async function ManagementPage() {
  const management = await db.globalSetting.findUnique({ where: { key: "client.management" } });
  const managementValue = management?.value as { telegramUsername?: unknown } | null;
  const telegramUsername = typeof managementValue?.telegramUsername === "string"
    ? managementValue.telegramUsername
    : "Folwn";

  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">SYSTEM SETTINGS</span>
          <h1>تنظیمات</h1>
          <p>اطلاعات خرید و پشتیبانی که Client مستقیماً از Backend دریافت می‌کند.</p>
        </div>
      </header>
      <AdminSettingsTabs />

      <section className="v2-settings-catalog">
        <div className="v2-settings-section-head"><div><span className="v2-eyebrow">MANAGEMENT</span><h2>خرید و پشتیبانی</h2><p>تنظیم تایپ‌شده `client.management`؛ بدون نیاز به ویرایش JSON.</p></div></div>
        <div className="v2-settings-form-body">
          <ManagementInfoForm telegramUsername={telegramUsername} />
        </div>
      </section>

      <section className="v2-settings-catalog">
        <div className="v2-settings-section-head"><div><span className="v2-eyebrow">CURRENT STATE</span><h2>وضعیت فعلی</h2></div></div>
        <div className="v2-management-facts">
          <div><small>Telegram</small><strong dir="ltr">@{telegramUsername.replace(/^@/, "")}</strong></div>
          <div><small>Source</small><strong>Bootstrap / Backend</strong></div>
          <div><small>نیاز به انتشار نسخه جدید</small><span className="v2-settings-state is-success">خیر</span></div>
        </div>
      </section>
    </>
  );
}
