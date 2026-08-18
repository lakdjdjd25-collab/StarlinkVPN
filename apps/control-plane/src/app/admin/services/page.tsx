import { AdminUsersV2 } from "@/components/admin/AdminUsersV2";

export const dynamic = "force-dynamic";

export default function ServicesPage() {
  return (
    <>
      <header className="page-header">
        <div>
          <span className="v2-eyebrow">CUSTOMERS & ACCESS</span>
          <h1>کاربران</h1>
          <p>مدیریت اشتراک، حجم، اعتبار، دستگاه‌ها، VIP، مجوز و وضعیت Provider بدون وابستگی صفحه به اتصال زنده پنل VPN</p>
        </div>
      </header>
      <AdminUsersV2 />
    </>
  );
}
