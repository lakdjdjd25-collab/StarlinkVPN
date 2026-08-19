import { CreateRegionForm } from "@/components/EntityForms";
import { VipCreateNodeForm } from "@/components/VipNodeForms";
import { AdminManagedServersV2 } from "@/components/admin/AdminManagedServersV2";
import { AdminServerTabs } from "@/components/admin/AdminServerTabs";
import { db } from "@/lib/db";
import { groupManagedNodesForAdmin } from "@/lib/pasarguard/logical-node";

export const dynamic = "force-dynamic";

export default async function NodesPage() {
  const [nodes, regions] = await Promise.all([
    db.vpnNode.findMany({
      orderBy: [{ region: { priority: "desc" } }, { name: "asc" }],
      include: {
        region: true,
        pasarGuardBinding: { select: { lastSyncAt: true } },
        services: {
          where: { enabled: true },
          select: { service: { select: { userId: true, maxDevices: true } } },
        },
      },
    }),
    db.serverRegion.findMany({
      where: { enabled: true },
      orderBy: [{ priority: "desc" }, { name: "asc" }],
    }),
  ]);

  const groupedNodes = groupManagedNodesForAdmin(nodes.map((node) => ({
    id: node.id,
    name: node.name,
    provider: node.provider,
    providerTag: node.providerTag,
    accessTier: node.accessTier,
    status: node.status,
    regionName: node.region.name,
    countryCode: node.region.countryCode,
    protocol: node.protocol,
    host: node.host,
    port: node.port,
    capacity: node.capacity,
    activeSessions: node.activeSessions,
    lastSeenAt: node.lastSeenAt,
    lastSyncAt: node.pasarGuardBinding?.lastSyncAt ?? null,
    assignments: node.services.map(({ service }) => ({ userId: service.userId, maxDevices: service.maxDevices })),
  })));

  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">SERVER CONTROL CENTER</span>
          <h1>سرورها</h1>
          <p>هر سرور واقعی پاسارگاد فقط یک‌بار نمایش داده می‌شود؛ تعداد کاربران و آخرین Sync بدون نمایش ظرفیت ساختگی.</p>
        </div>
      </header>

      <AdminServerTabs />

      <AdminManagedServersV2
        nodes={groupedNodes.map((node) => ({
          ...node,
          lastSeenAt: node.lastSeenAt?.toISOString() ?? null,
          lastSyncAt: node.lastSyncAt?.toISOString() ?? null,
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
