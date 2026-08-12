import Link from "next/link";
import { LogoutButton } from "@/components/LogoutButton";
import { requireAdminPage } from "@/lib/admin-session";

const navigation = [
  ["داشبورد", "/admin"],
  ["کاربران", "/admin/users"],
  ["سرویس‌ها", "/admin/services"],
  ["سرورها", "/admin/nodes"],
  ["تنظیمات", "/admin/settings"],
] as const;

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  await requireAdminPage();
  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <Link className="brand-mark" href="/admin">
          <span className="brand-q">Q</span>
          <span>QUICKPING</span>
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
