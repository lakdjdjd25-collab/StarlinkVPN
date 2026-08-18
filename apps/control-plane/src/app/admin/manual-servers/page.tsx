import { AdminManualServersV2 } from "@/components/admin/AdminManualServersV2";
import { AdminServerTabs } from "@/components/admin/AdminServerTabs";

export const dynamic = "force-dynamic";

export default function ManualServersPage() {
  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">SERVER CONTROL CENTER</span>
          <h1>سرورها</h1>
          <p>Manual VLESS در همان مرکز کنترل؛ وضعیت، VIP، Unlimited/Limited و ترافیک واقعی در یک نمای فشرده.</p>
        </div>
      </header>
      <AdminServerTabs />
      <AdminManualServersV2 />
    </>
  );
}
