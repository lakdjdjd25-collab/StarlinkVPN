import { ManagementInfoForm } from "@/components/EntityForms";
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
          <h1>اطلاعات مدیریت</h1>
          <p>اطلاعاتی که اپ بدون انتشار APK جدید از Backend دریافت می‌کند</p>
        </div>
      </header>

      <section className="card section">
        <div className="section-title"><h2>تلگرام مدیریت و خرید سرویس</h2></div>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          دکمه Upgrade / خرید سرویس در Android از همین مقدار استفاده می‌کند. تغییر این مقدار برای کاربران نیاز به انتشار نسخه جدید ندارد.
        </p>
        <ManagementInfoForm telegramUsername={telegramUsername} />
      </section>

      <section className="card section">
        <div className="section-title"><h2>وضعیت فعلی</h2></div>
        <div className="table-wrap">
          <table>
            <tbody>
              <tr><th>آیدی تلگرام فعال</th><td dir="ltr">@{telegramUsername.replace(/^@/, "")}</td></tr>
              <tr><th>منبع Android</th><td>Bootstrap / Backend</td></tr>
              <tr><th>نیاز به APK جدید برای تغییر آیدی</th><td><span className="badge green">خیر</span></td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
