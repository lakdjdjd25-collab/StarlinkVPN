import { CreateRegionForm } from "@/components/EntityForms";
import { VipCreateNodeForm } from "@/components/VipNodeForms";
import { AdminManagedServersV2 } from "@/components/admin/AdminManagedServersV2";
import { AdminServerTabs } from "@/components/admin/AdminServerTabs";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

export default async function NodesPage() {
  const [nodes, regions] = await Promise.all([
    db.vpnNode.findMany({
      orderBy: [{ region: { priority: "desc" } }, { name: "asc" }],
      include: { region: true },
    }),
    db.serverRegion.findMany({
      where: { enabled: true },
      orderBy: [{ priority: "desc" }, { name: "asc" }],
    }),
  ]);

  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">SERVER CONTROL CENTER</span>
          <h1>سرورها</h1>
          <p>نمای عملیاتی Managed و Manual؛ سلامت، ظرفیت و سطح دسترسی بدون تغییر در منطق تحویل سرور.</p>
        </div>
      </header>

      <AdminServerTabs />

      <AdminManagedServersV2
        nodes={nodes.map((node) => ({
          id: node.id,
          name: node.name,
          accessTier: node.accessTier,
          status: node.status,
          regionName: node.region.name,
          countryCode: node.region.countryCode,
          protocol: node.protocol,
          host: node.host,
          port: node.port,
          activeSessions: node.activeSessions,
          capacity: node.capacity,
          lastSeenAt: node.lastSeenAt.toISOString(),
        }))}
      />

      <details className="v2-server-provision">
        <summary>
          <span><strong>Provisioning و تنظیمات زیرساخت</strong><small>ایجاد Region یا Managed Node جدید</small></span>
          <span className="v2-server-provision-marker">+</span>
        </summary>
        <div className="v2-server-provision-body">
          <section>
            <div className="section-title"><h2>افزودن Region</h2><p>Region فقط برای گروه‌بندی و اولویت نودهای Managed استفاده می‌شود.</p></div>
            <CreateRegionForm />
          </section>
          <section>
            <div className="section-title"><h2>افزودن Managed Node</h2><p>کانفیگ Runtime رمزگذاری‌شده و فقط از Backend تحویل Client می‌شود.</p></div>
            {regions.length
              ? <VipCreateNodeForm regions={regions.map((region) => ({ id: region.id, label: `${region.name} (${region.countryCode})` }))} />
              : <div className="empty">ابتدا یک Region ایجاد کنید.</div>}
          </section>
        </div>
      </details>
    </>
  );
}
