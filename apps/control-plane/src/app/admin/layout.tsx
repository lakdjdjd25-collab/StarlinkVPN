import Link from "next/link";
import Image from "next/image";
import { LogoutButton } from "@/components/LogoutButton";
import { requireAdminPage } from "@/lib/admin-session";

const navigation = [
  ["داشبورد", "/admin"],
  ["کاربران", "/admin/users"],
  ["سرویس‌ها", "/admin/services"],
  ["سرورها", "/admin/nodes"],
  ["پاسارگارد", "/admin/integrations/pasarguard"],
  ["تنظیمات", "/admin/settings"],
] as const;

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  await requireAdminPage();
  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <Link className="brand-mark" href="/admin">
          <Image className="brand-logo" src="/nimhub-logo.png" width={42} height={42} alt="" />
          <span>NIMHUB</span>
        </Link>
        <nav className="nav-list" aria-label="ناوبری مدیریت">
          {navigation.map(([label, href]) => (
            <Link className="nav-link" href={href} key={href}>
              {label}
            </Link>
          ))}
        </nav>
        <div className="sidebar-footer">
          <LogoutButton />
        </div>
      </aside>
      <main className="main-content">{children}</main>
    </div>
  );
}
