import { CreateReleaseForm } from "@/components/EntityForms";
import { AdminSettingsTabs } from "@/components/admin/AdminSettingsTabs";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

function formatDateTime(value: Date | null): string {
  if (!value) return "Draft";
  return new Intl.DateTimeFormat("fa-IR", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(value);
}

export default async function ReleasesSettingsPage() {
  const releases = await db.appRelease.findMany({
    orderBy: { versionCode: "desc" },
    take: 30,
  });
  const published = releases.filter((release) => release.publishedAt).length;
  const mandatory = releases.filter((release) => release.mandatory && release.publishedAt).length;
  const latest = releases.find((release) => release.publishedAt) ?? releases[0] ?? null;

  return (
    <>
      <header className="page-header"><div><span className="v2-eyebrow">SYSTEM SETTINGS</span><h1>تنظیمات</h1><p>چرخه انتشار نسخه‌های اپ در همان Settings Center.</p></div></header>
      <AdminSettingsTabs />

      <div className="v2-release-summary">
        <div><small>نسخه‌های اخیر</small><strong>{releases.length}</strong></div>
        <div><small>منتشرشده</small><strong>{published}</strong></div>
        <div><small>اجباری فعال</small><strong>{mandatory}</strong></div>
        <div><small>آخرین Version</small><strong dir="ltr">{latest ? `${latest.versionName} (${latest.versionCode})` : "—"}</strong></div>
      </div>

      <details className="v2-settings-editor">
        <summary><span><strong>انتشار نسخه جدید</strong><small>Version lifecycle و اطلاعات دانلود</small></span><span>+</span></summary>
        <div className="v2-settings-editor-body"><CreateReleaseForm /></div>
      </details>

      <section className="v2-settings-catalog">
        <div className="v2-settings-section-head"><div><span className="v2-eyebrow">RELEASE HISTORY</span><h2>نسخه‌ها</h2><p>اطلاعات واقعی AppRelease؛ بدون داده نمایشی.</p></div></div>
        <div className="v2-release-table-wrap">
          <table className="v2-release-table">
            <thead><tr><th>Platform</th><th>Version</th><th>Minimum</th><th>Mandatory</th><th>Published</th><th>Download</th></tr></thead>
            <tbody>{releases.map((release) => <tr key={release.id}>
              <td><span className="v2-settings-state is-neutral">{release.platform}</span></td>
              <td><span className="v2-release-version"><strong dir="ltr">{release.versionName}</strong><small dir="ltr">code {release.versionCode}</small></span></td>
              <td dir="ltr">{release.minSupportedVersionCode}</td>
              <td><span className={`v2-settings-state is-${release.mandatory ? "warning" : "neutral"}`}>{release.mandatory ? "اجباری" : "اختیاری"}</span></td>
              <td>{formatDateTime(release.publishedAt)}</td>
              <td>{release.downloadUrl ? <a href={release.downloadUrl} target="_blank" rel="noreferrer">Download</a> : "—"}</td>
            </tr>)}</tbody>
          </table>
          {!releases.length ? <div className="v2-settings-empty"><strong>نسخه‌ای ثبت نشده است</strong><span>از بخش بالا اولین Release را ایجاد کن.</span></div> : null}
        </div>
      </section>
    </>
  );
}
