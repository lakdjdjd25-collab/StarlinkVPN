import { CreateNotificationForm } from "@/components/EntityForms";
import { db } from "@/lib/db";
import { formatDate } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function NotificationsPage() {
  const [notifications, users] = await Promise.all([
    db.notification.findMany({
      orderBy: { createdAt: "desc" },
      take: 50,
      include: {
        deliveries: {
          include: { user: { select: { email: true } } },
          orderBy: { user: { email: "asc" } },
        },
      },
    }),
    db.user.findMany({
      where: { status: "ACTIVE" },
      orderBy: { email: "asc" },
      select: { id: true, email: true },
    }),
  ]);

  return (
    <>
      <header className="page-header">
        <div>
          <h1>اعلان‌ها</h1>
          <p>ارسال شخصی، چندکاربره یا برای همه کاربران فعلی با Inbox مستقل هر کاربر</p>
        </div>
      </header>

      <section className="card section">
        <div className="section-title"><h2>ساخت اعلان</h2></div>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          اعلان «همه کاربران» فقط برای کاربرانی Snapshot می‌شود که در زمان ارسال وجود دارند؛ کاربران جدید اعلان‌های قدیمی را دریافت نمی‌کنند.
        </p>
        <CreateNotificationForm users={users.map((user) => ({ id: user.id, label: user.email }))} />
      </section>

      <section className="card section">
        <div className="section-title"><h2>تاریخچه اعلان‌ها</h2></div>
        {notifications.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>موضوع</th>
                  <th>عنوان و متن</th>
                  <th>نوع مخاطب</th>
                  <th>دریافت‌کنندگان</th>
                  <th>خوانده‌شده</th>
                  <th>زمان ارسال</th>
                </tr>
              </thead>
              <tbody>
                {notifications.map((notification) => {
                  const total = notification.deliveries.length;
                  const read = notification.deliveries.filter((delivery) => delivery.readAt).length;
                  const recipientPreview = notification.deliveries.slice(0, 3).map((delivery) => delivery.user.email);
                  const more = Math.max(0, total - recipientPreview.length);
                  return (
                    <tr key={notification.id}>
                      <td><span className="badge blue">{notification.category}</span></td>
                      <td><strong>{notification.title}</strong><br /><small>{notification.body}</small></td>
                      <td>{notification.audience === "ALL" ? "همه کاربران فعلی" : notification.audience === "SELECTED" ? "انتخابی" : notification.audience}</td>
                      <td>
                        <strong>{total}</strong>
                        {recipientPreview.length ? <><br /><small dir="ltr">{recipientPreview.join("، ")}{more ? ` +${more}` : ""}</small></> : null}
                      </td>
                      <td><span className={read === total && total > 0 ? "badge green" : "badge blue"}>{read} / {total}</span></td>
                      <td>{formatDate(notification.publishedAt ?? notification.createdAt)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : <div className="empty">هنوز اعلانی ارسال نشده است.</div>}
      </section>
    </>
  );
}
