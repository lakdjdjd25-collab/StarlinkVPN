import { ManualServerManager } from "@/components/ManualServerManager";

export const dynamic = "force-dynamic";

export default function ManualServersPage() {
  return (
    <>
      <header className="page-header">
        <div>
          <h1>سرورهای دستی</h1>
          <p>VLESS اشتراکی، Unlimited/Limited، زیردسته، حجم، VIP/Standard و محاسبه ترافیک</p>
        </div>
        <span className="badge blue">Server-Driven</span>
      </header>
      <ManualServerManager />
    </>
  );
}
