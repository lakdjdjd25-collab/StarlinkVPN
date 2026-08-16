import { CreateReleaseForm, GlobalSettingForm } from "@/components/EntityForms";
import { db } from "@/lib/db";
import { formatDate } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function SettingsPage() {
  const [settings, releases] = await Promise.all([
    db.globalSetting.findMany({ orderBy: { key: "asc" } }),
    db.appRelease.findMany({ orderBy: { versionCode: "desc" }, take: 10 }),
  ]);
  return (
    <>
      <header className="page-header"><div><h1>تنظیمات سامانه</h1><p>Bootstrap، تنظیمات کلاینت و انتشار نسخه‌ها</p></div></header>
      <section className="card section">
        <div className="section-title"><h2>ثبت یا ویرایش تنظیم</h2></div>
        <GlobalSettingForm />
      </section>
      <section className="card section">
        <div className="section-title"><h2>تنظیمات کلاینت</h2></div>
        {settings.filter((setting) => setting.key !== "client.management").length ? settings.filter((setting) => setting.key !== "client.management").map((setting) => <div key={setting.key} style={{ padding: "12px 0", borderBottom: "1px solid var(--border-soft)" }}><strong dir="ltr">{setting.key}</strong><pre dir="ltr" style={{ color: "var(--muted)", whiteSpace: "pre-wrap" }}>{JSON.stringify(setting.value, null, 2)}</pre></div>) : <div className="empty">تنظیمی ثبت نشده است.</div>}
      </section>
      <section className="card section"><div className="section-title"><h2>انتشار نسخه</h2></div><CreateReleaseForm /></section>
      <section className="card section">
        <div className="section-title"><h2>نسخه‌های منتشرشده</h2></div>
        <div className="table-wrap"><table><thead><tr><th>پلتفرم</th><th>نسخه</th><th>اجباری</th><th>انتشار</th></tr></thead><tbody>{releases.map((release) => <tr key={release.id}><td>{release.platform}</td><td dir="ltr">{release.versionName} ({release.versionCode})</td><td>{release.mandatory ? "بله" : "خیر"}</td><td>{formatDate(release.publishedAt)}</td></tr>)}</tbody></table></div>
      </section>
    </>
  );
}
