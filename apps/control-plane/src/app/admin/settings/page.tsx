import { AdminSettingsTabs } from "@/components/admin/AdminSettingsTabs";
import { AdminSettingsGeneralV2 } from "@/components/admin/AdminSettingsGeneralV2";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

export default async function SettingsPage() {
  const settings = await db.globalSetting.findMany({ orderBy: { key: "asc" } });

  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">SYSTEM SETTINGS</span>
          <h1>تنظیمات</h1>
          <p>تنظیمات روزمره، اطلاعات مدیریت، Provider و انتشار نسخه‌ها در یک مرکز واحد.</p>
        </div>
      </header>
      <AdminSettingsTabs />
      <AdminSettingsGeneralV2
        settings={settings
          .filter((setting) => setting.key !== "client.management")
          .map((setting) => ({
            key: setting.key,
            value: setting.value,
            description: setting.description,
          }))}
      />
    </>
  );
}
