import { CreateNodeForm, CreateRegionForm, NodeStatusForm } from "@/components/EntityForms";
import { db } from "@/lib/db";
import { formatDate } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function NodesPage() {
  const [nodes, regions] = await Promise.all([
    db.vpnNode.findMany({ orderBy: [{ region: { priority: "desc" } }, { name: "asc" }], include: { region: true } }),
    db.serverRegion.findMany({ where: { enabled: true }, orderBy: [{ priority: "desc" }, { name: "asc" }] }),
  ]);
  return (
    <>
      <header className="page-header"><div><h1>سرورها</h1><p>سلامت، ظرفیت و پیکربندی رمزگذاری‌شده نودها</p></div><span className="badge blue">{nodes.length} نود</span></header>
      <section className="card section"><div className="section-title"><h2>افزودن منطقه</h2></div><CreateRegionForm /></section>
      <section className="card section"><div className="section-title"><h2>افزودن نود</h2></div>{regions.length ? <CreateNodeForm regions={regions.map((region) => ({ id: region.id, label: `${region.name} (${region.countryCode})` }))} /> : <div className="empty">ابتدا یک منطقه در دیتابیس ایجاد کنید.</div>}</section>
      <section className="card section">
        <div className="section-title"><h2>وضعیت نودها</h2></div>
        <div className="table-wrap"><table>
          <thead><tr><th>نام</th><th>منطقه</th><th>پروتکل</th><th>نشانی</th><th>ظرفیت</th><th>آخرین مشاهده</th><th>وضعیت</th><th>کنترل</th></tr></thead>
          <tbody>{nodes.map((node) => <tr key={node.id}>
            <td><strong>{node.name}</strong></td><td>{node.region.name}</td><td>{node.protocol}</td><td dir="ltr">{node.host}:{node.port}</td><td>{node.activeSessions}/{node.capacity}</td><td>{formatDate(node.lastSeenAt)}</td><td><span className={node.status === "ONLINE" ? "badge green" : node.status === "DEGRADED" ? "badge blue" : "badge red"}>{node.status}</span></td>
            <td><NodeStatusForm id={node.id} status={node.status} capacity={node.capacity} /></td>
          </tr>)}</tbody>
        </table></div>
      </section>
    </>
  );
}
