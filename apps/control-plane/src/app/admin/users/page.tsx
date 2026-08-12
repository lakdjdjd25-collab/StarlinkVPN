import { CreateUserForm, UserAccessForm } from "@/components/EntityForms";
import { db } from "@/lib/db";
import { formatDate } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function UsersPage() {
  const users = await db.user.findMany({
    orderBy: { createdAt: "desc" },
    take: 100,
    include: { _count: { select: { services: true, devices: true } } },
  });
  return (
    <>
      <header className="page-header"><div><h1>کاربران</h1><p>مدیریت حساب‌ها، نقش‌ها و دستگاه‌ها</p></div><span className="badge blue">{users.length} رکورد</span></header>
      <section className="card section"><div className="section-title"><h2>کاربر جدید</h2></div><CreateUserForm /></section>
      <section className="card section">
        <div className="section-title"><h2>فهرست کاربران</h2></div>
        <div className="table-wrap"><table>
          <thead><tr><th>ایمیل</th><th>نقش</th><th>سرویس</th><th>دستگاه</th><th>وضعیت</th><th>ایجاد</th><th>دسترسی</th></tr></thead>
          <tbody>{users.map((user) => <tr key={user.id}>
            <td><strong>{user.email}</strong></td><td>{user.role}</td><td>{user._count.services}</td><td>{user._count.devices}</td>
            <td><span className={user.status === "ACTIVE" ? "badge green" : "badge red"}>{user.status}</span></td><td>{formatDate(user.createdAt)}</td>
            <td><UserAccessForm id={user.id} status={user.status} role={user.role} /></td>
          </tr>)}</tbody>
        </table></div>
      </section>
    </>
  );
}
