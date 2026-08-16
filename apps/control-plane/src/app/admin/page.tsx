import { db } from "@/lib/db";
import { formatDate } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  const [users, activeServices, onlineNodes, unreadNotifications, recentUsers] = await Promise.all([
    db.user.count({ where: { status: "ACTIVE" } }),
    db.service.count({ where: { status: "ACTIVE", expiresAt: { gt: new Date() } } }),
    db.vpnNode.count({ where: { status: "ONLINE" } }),
    db.notification.count({
      where: {
        publishedAt: { not: null },
        OR: [{ expiresAt: null }, { expiresAt: { gt: new Date() } }],
      },
    }),
    db.user.findMany({ orderBy: { createdAt: "desc" }, take: 6 }),
  ]);

  return (
    <>
      <header className="page-header">
        <div>
          <h1>داشبورد</h1>
          <p>وضعیت لحظه‌ای کنترل‌پلین NimHUB Vpn</p>
        </div>
        <span className="badge green">سامانه فعال</span>
      </header>
      <section className="stats-grid">
        <Stat label="کاربران فعال" value={users} note="حساب‌های تعلیق‌نشده" />
        <Stat label="سرویس‌های فعال" value={activeServices} note="دارای حجم و اعتبار" />
        <Stat label="نودهای آنلاین" value={onlineNodes} note="گزارش سلامت اخیر" />
        <Stat label="اعلان‌های فعال" value={unreadNotifications} note="در بازه انتشار" />
      </section>
      <section className="card section">
        <div className="section-title"><h2>ثبت‌نام‌های اخیر</h2></div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>ایمیل</th><th>نقش</th><th>وضعیت</th><th>تاریخ ایجاد</th></tr></thead>
            <tbody>
              {recentUsers.map((user) => (
                <tr key={user.id}>
                  <td><strong>{user.email}</strong></td>
                  <td>{user.role}</td>
                  <td><span className={user.status === "ACTIVE" ? "badge green" : "badge red"}>{user.status}</span></td>
                  <td>{formatDate(user.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

function Stat({ label, value, note }: { label: string; value: number; note: string }) {
  return (
    <article className="card stat">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{new Intl.NumberFormat("fa-IR").format(value)}</div>
      <div className="stat-note">{note}</div>
    </article>
  );
}
