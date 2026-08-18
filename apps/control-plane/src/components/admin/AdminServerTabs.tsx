"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AdminIcon } from "./AdminIcon";

const tabs = [
  {
    href: "/admin/nodes",
    label: "Managed",
    description: "نودهای مدیریت‌شده و Provider",
    icon: "server" as const,
  },
  {
    href: "/admin/manual-servers",
    label: "Manual",
    description: "سرورهای VLESS دستی",
    icon: "sliders" as const,
  },
];

export function AdminServerTabs() {
  const pathname = usePathname();

  return (
    <nav className="v2-server-tabs" aria-label="نوع سرورها">
      {tabs.map((tab) => {
        const active = pathname.startsWith(tab.href);
        return (
          <Link
            href={tab.href}
            className={`v2-server-tab${active ? " is-active" : ""}`}
            aria-current={active ? "page" : undefined}
            key={tab.href}
          >
            <span className="v2-server-tab-icon"><AdminIcon name={tab.icon} size={17} /></span>
            <span><strong>{tab.label}</strong><small>{tab.description}</small></span>
          </Link>
        );
      })}
    </nav>
  );
}
