"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AdminIcon } from "./AdminIcon";

const tabs = [
  { href: "/admin/settings", label: "General", note: "تنظیمات عمومی", icon: "settings" as const, exact: true },
  { href: "/admin/management", label: "Management", note: "خرید و پشتیبانی", icon: "users" as const },
  { href: "/admin/integrations/pasarguard", label: "VPN Provider", note: "PasarGuard", icon: "server" as const },
  { href: "/admin/settings/releases", label: "App Releases", note: "انتشار نسخه", icon: "activity" as const },
  { href: "/admin/settings/advanced", label: "Advanced", note: "JSON و عیب‌یابی", icon: "sliders" as const },
];

export function AdminSettingsTabs() {
  const pathname = usePathname();
  return (
    <nav className="v2-settings-tabs" aria-label="بخش‌های تنظیمات">
      {tabs.map((tab) => {
        const active = tab.exact ? pathname === tab.href : pathname.startsWith(tab.href);
        return (
          <Link href={tab.href} className={`v2-settings-tab${active ? " is-active" : ""}`} aria-current={active ? "page" : undefined} key={tab.href}>
            <span><AdminIcon name={tab.icon} size={15} /></span>
            <span><strong>{tab.label}</strong><small>{tab.note}</small></span>
          </Link>
        );
      })}
    </nav>
  );
}
