import { AdminServerTabs } from "@/components/admin/AdminServerTabs";
import { ManualServerManager } from "@/components/ManualServerManager";

export const dynamic = "force-dynamic";

export default function ManualServersPage() {
  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">SERVER CONTROL CENTER</span>
          <h1>سرورها</h1>
          <p>Manual VLESS در همان مرکز سرورها؛ Unlimited/Limited، زیردسته، حجم، VIP/Standard و محاسبه ترافیک.</p>
        </div>
        <span className="badge blue">Manual</span>
      </header>
      <AdminServerTabs />
      <ManualServerManager />
    </>
  );
}
