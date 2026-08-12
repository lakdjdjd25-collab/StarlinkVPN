import { AssignNodeForm, CreatePlanForm, CreateServiceForm, ServiceUpdateForm } from "@/components/EntityForms";
import { db } from "@/lib/db";
import { formatBytes, formatDate } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function ServicesPage() {
  const [services, users, plans, nodes] = await Promise.all([
    db.service.findMany({ orderBy: { createdAt: "desc" }, take: 100, include: { user: true, plan: true, nodes: { include: { node: true } } } }),
    db.user.findMany({ where: { status: "ACTIVE" }, orderBy: { email: "asc" }, take: 200 }),
    db.plan.findMany({ where: { isActive: true }, orderBy: { price: "asc" } }),
    db.vpnNode.findMany({ orderBy: { name: "asc" }, include: { region: true } }),
  ]);
  return (
    <>
      <header className="page-header"><div><h1>سرویس‌ها</h1><p>حجم، اعتبار، پلن و کلیدهای مجوز</p></div><span className="badge blue">{services.length} سرویس</span></header>
      <section className="card section"><div className="section-title"><h2>ساخت پلن</h2></div><CreatePlanForm /></section>
      <section className="card section"><div className="section-title"><h2>ایجاد سرویس</h2></div><CreateServiceForm users={users.map((user) => ({ id: user.id, label: user.email }))} plans={plans.map((plan) => ({ id: plan.id, label: `${plan.name} — ${plan.price}` }))} /></section>
      <section className="card section"><div className="section-title"><h2>اتصال سرور به سرویس</h2></div><AssignNodeForm services={services.map((service) => ({ id: service.id, label: `${service.name} — ${service.user.email}` }))} nodes={nodes.map((node) => ({ id: node.id, label: `${node.name} — ${node.region.name}` }))} /></section>
      <section className="card section">
        <div className="section-title"><h2>فهرست سرویس‌ها</h2></div>
        <div className="table-wrap"><table>
          <thead><tr><th>نام</th><th>کاربر</th><th>مجوز</th><th>مصرف</th><th>انقضا</th><th>وضعیت</th><th>نودها</th><th>کنترل</th></tr></thead>
          <tbody>{services.map((service) => <tr key={service.id}>
            <td><strong>{service.name}</strong><br /><small>{service.plan.name}</small></td><td>{service.user.email}</td><td dir="ltr">{service.license}</td>
            <td>{formatBytes(service.usedBytes)} / {formatBytes(service.quotaBytes)}</td><td>{formatDate(service.expiresAt)}</td><td><span className={service.status === "ACTIVE" ? "badge green" : "badge red"}>{service.status}</span></td>
            <td>{service.nodes.length ? service.nodes.map(({ node }) => node.name).join("، ") : "—"}</td>
            <td>
              <ServiceUpdateForm
                id={service.id}
                status={service.status}
                quotaGb={Number(service.quotaBytes) / 1024 ** 3}
                maxDevices={service.maxDevices}
                daysLeft={Math.max(0, Math.ceil((service.expiresAt.getTime() - Date.now()) / 86_400_000))}
              />
            </td>
          </tr>)}</tbody>
        </table></div>
      </section>
    </>
  );
}
