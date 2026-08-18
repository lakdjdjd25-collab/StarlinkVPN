import { AdminShell } from "@/components/admin/AdminShell";
import { requireAdminPage } from "@/lib/admin-session";
import "./admin-v2.css";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  await requireAdminPage();
  return <AdminShell>{children}</AdminShell>;
}
