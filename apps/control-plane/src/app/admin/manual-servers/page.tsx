import { ManualServerManager } from "@/components/ManualServerManager";

export const dynamic = "force-dynamic";

export default function ManualServersPage() {
  return (
    <>
      <header className="page-header">
        <div>
          <h1>سرورهای دستی</h1>
          <p>افزودن VLESS اشتراکی، دسته‌بندی، VIP/Public، ترتیب و آمار مصرف</p>
        </div>
        <span className="badge blue">Server-Driven</span>
      </header>
      <ManualServerManager />
    </>
  );
}
