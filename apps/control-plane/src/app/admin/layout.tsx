import { AdminShell } from "@/components/admin/AdminShell";
import { requireAdminPage } from "@/lib/admin-session";
import "./admin-v2.css";
import "./dashboard-v2.css";
import "./users-v2.css";
import "./servers-v2.css";
import "./manual-servers-v2.css";
import "./notifications-v2.css";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  await requireAdminPage();
  return <AdminShell>{children}</AdminShell>;
}
