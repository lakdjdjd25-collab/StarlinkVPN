import { CreateRegionForm } from "@/components/EntityForms";
import { AdminServerTabs } from "@/components/admin/AdminServerTabs";
import { VipCreateNodeForm, VipNodeControlForm } from "@/components/VipNodeForms";
import { db } from "@/lib/db";
import { formatDate } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function NodesPage() {
  const [nodes, regions] = await Promise.all([
    db.vpnNode.findMany({ orderBy: [{ region: { priority: "desc" } }, { name: "asc" }], include: { region: true } }),
    db.serverRegion.findMany({ where: { enabled: true }, orderBy: [{ priority: "desc" }, { name: "asc" }] }),
  ]);
  const vipCount = nodes.filter((node) => node.accessTier === "VIP").length;
  return (
    <>
      <header className="page-header">
        <div><span className="v2-eyebrow">SERVER CONTROL CENTER</span><h1>سرورها</h1><p>Managed و Manual در یک بخش؛ سلامت، ظرفیت، VIP و تنظیمات هر نوع سرور مستقل باقی می‌ماند.</p></div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <span className="badge blue">{nodes.length} Managed</span>
          {vipCount ? <span className="badge" style={{ background: "linear-gradient(90deg,#6758cf,#9b7fd0,#d9da87)", color: "#fff" }}>{vipCount} VIP</span> : null}
        </div>
      </header>
      <AdminServerTabs />
      <section className="card section"><div className="section-title"><h2>افزودن منطقه</h2></div><CreateRegionForm /></section>
      <section className="card section"><div className="section-title"><h2>افزودن نود Managed</h2></div>{regions.length ? <VipCreateNodeForm regions={regions.map((region) => ({ id: region.id, label: `${region.name} (${region.countryCode})` }))} /> : <div className="empty">ابتدا یک منطقه در دیتابیس ایجاد کنید.</div>}</section>
      <section className="card section">
        <div className="section-title"><h2>وضعیت نودهای Managed</h2><p>کنترل دسترسی واقعی سمت سرور اعمال می‌شود؛ Manual Serverها از Tab مجاور مدیریت می‌شوند.</p></div>
        <div className="table-wrap"><table>
          <thead><tr><th>نام</th><th>نوع</th><th>منطقه</th><th>پروتکل</th><th>نشانی</th><th>ظرفیت</th><th>آخرین مشاهده</th><th>وضعیت</th><th>کنترل</th></tr></thead>
          <tbody>{nodes.map((node) => <tr key={node.id}>
            <td><strong>{node.name}</strong></td>
            <td>{node.accessTier === "VIP" ? <span className="badge" style={{ background: "linear-gradient(90deg,#6758cf,#9b7fd0,#d9da87)", color: "#fff" }}>VIP</span> : <span className="badge blue">Standard</span>}</td>
            <td>{node.region.name}</td><td>{node.protocol}</td><td dir="ltr">{node.host}:{node.port}</td><td>{node.activeSessions}/{node.capacity}</td><td>{formatDate(node.lastSeenAt)}</td><td><span className={node.status === "ONLINE" ? "badge green" : node.status === "DEGRADED" ? "badge blue" : "badge red"}>{node.status}</span></td>
            <td><VipNodeControlForm id={node.id} status={node.status} capacity={node.capacity} accessTier={node.accessTier} /></td>
          </tr>)}</tbody>
        </table></div>
      </section>
    </>
  );
}
