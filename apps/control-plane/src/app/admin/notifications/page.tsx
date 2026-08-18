import { AdminNotificationsV2 } from "@/components/admin/AdminNotificationsV2";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

export default async function NotificationsPage() {
  const [notifications, users] = await Promise.all([
    db.notification.findMany({
      orderBy: { createdAt: "desc" },
      take: 100,
      include: {
        deliveries: {
          include: { user: { select: { email: true } } },
          orderBy: { user: { email: "asc" } },
        },
      },
    }),
    db.user.findMany({
      where: { role: "CUSTOMER", status: { not: "DELETED" } },
      orderBy: { email: "asc" },
      select: { id: true, email: true },
    }),
  ]);

  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">NOTIFICATIONS</span>
          <h1>اعلان‌ها</h1>
          <p>ارسال هدفمند و مشاهده وضعیت خوانده‌شدن با حفظ Snapshot مستقل Inbox هر کاربر.</p>
        </div>
      </header>

      <AdminNotificationsV2
        users={users}
        notifications={notifications.map((notification) => ({
          id: notification.id,
          title: notification.title,
          body: notification.body,
          category: notification.category,
          audience: notification.audience,
          createdAt: notification.createdAt.toISOString(),
          publishedAt: notification.publishedAt?.toISOString() ?? null,
          recipientCount: notification.deliveries.length,
          readCount: notification.deliveries.filter((delivery) => Boolean(delivery.readAt)).length,
          recipients: notification.deliveries.map((delivery) => delivery.user.email),
        }))}
      />
    </>
  );
}
