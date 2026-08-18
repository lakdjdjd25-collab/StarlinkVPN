"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { LogoutButton } from "@/components/LogoutButton";
import { AdminIcon, type AdminIconName } from "./AdminIcon";

type NavItem = {
  label: string;
  href: string;
  icon: AdminIconName;
  aliases?: string[];
  description: string;
};

type NavGroup = {
  label: string;
  items: NavItem[];
};

const navigation: NavGroup[] = [
  {
    label: "مرکز کنترل",
    items: [
      { label: "داشبورد", href: "/admin", icon: "dashboard", description: "نمای عملیاتی سامانه" },
    ],
  },
  {
    label: "عملیات",
    items: [
      {
        label: "کاربران",
        href: "/admin/services",
        icon: "users",
        aliases: ["/admin/users"],
        description: "کاربران، مجوزها و اشتراک‌ها",
      },
      {
        label: "سرورها",
        href: "/admin/nodes",
        icon: "server",
        aliases: ["/admin/manual-servers"],
        description: "Managed و Manual",
      },
      {
        label: "اعلان‌ها",
        href: "/admin/notifications",
        icon: "bell",
        description: "ارسال و تاریخچه اعلان‌ها",
      },
    ],
  },
  {
    label: "سیستم",
    items: [
      {
        label: "تنظیمات",
        href: "/admin/settings",
        icon: "settings",
        aliases: ["/admin/management", "/admin/integrations/pasarguard"],
        description: "عمومی، مدیریت، Provider و انتشار",
      },
    ],
  },
];

const flatItems = navigation.flatMap((group) => group.items);

function matchesPath(pathname: string, item: NavItem): boolean {
  if (item.href === "/admin") return pathname === "/admin";
  return pathname.startsWith(item.href) || Boolean(item.aliases?.some((alias) => pathname.startsWith(alias)));
}

function pageTitle(pathname: string): string {
  return flatItems.find((item) => matchesPath(pathname, item))?.label ?? "NimHUB";
}

function NavContent({ pathname, onNavigate, collapsed = false }: {
  pathname: string;
  onNavigate?: () => void;
  collapsed?: boolean;
}) {
  return (
    <nav className="v2-nav" aria-label="ناوبری مدیریت">
      {navigation.map((group) => (
        <div className="v2-nav-group" key={group.label}>
          <div className="v2-nav-group-label" aria-hidden={collapsed}>{collapsed ? "" : group.label}</div>
          <div className="v2-nav-items">
            {group.items.map((item) => {
              const active = matchesPath(pathname, item);
              return (
                <Link
                  className={`v2-nav-link${active ? " is-active" : ""}`}
                  href={item.href}
                  key={item.href}
                  onClick={onNavigate}
                  aria-current={active ? "page" : undefined}
                  title={collapsed ? item.label : undefined}
                >
                  <span className="v2-nav-icon"><AdminIcon name={item.icon} /></span>
                  {!collapsed ? <span className="v2-nav-label">{item.label}</span> : null}
                </Link>
              );
            })}
          </div>
        </div>
      ))}
    </nav>
  );
}

function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [query, setQuery] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const pathname = usePathname();

  const results = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return flatItems;
    return flatItems.filter((item) => `${item.label} ${item.description}`.toLowerCase().includes(normalized));
  }, [query]);

  useEffect(() => {
    if (!open) return;
    setQuery("");
    const timer = window.setTimeout(() => inputRef.current?.focus(), 20);
    return () => window.clearTimeout(timer);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function keydown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="v2-command-layer" role="presentation" onMouseDown={(event) => {
      if (event.currentTarget === event.target) onClose();
    }}>
      <div className="v2-command" role="dialog" aria-modal="true" aria-label="جست‌وجو و دستورات مدیریت">
        <div className="v2-command-search">
          <AdminIcon name="search" size={19} />
          <input
            ref={inputRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="جست‌وجوی بخش‌های کنترل سنتر…"
            aria-label="جست‌وجوی مدیریت"
          />
          <kbd>ESC</kbd>
        </div>
        <div className="v2-command-results">
          {results.length ? results.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`v2-command-item${matchesPath(pathname, item) ? " is-current" : ""}`}
              onClick={onClose}
            >
              <span className="v2-command-item-icon"><AdminIcon name={item.icon} /></span>
              <span><strong>{item.label}</strong><small>{item.description}</small></span>
              <AdminIcon name="chevron-left" size={16} />
            </Link>
          )) : <div className="v2-command-empty">نتیجه‌ای پیدا نشد.</div>}
        </div>
        <div className="v2-command-footer">
          <span>مرحله بعد: جست‌وجوی سراسری کاربر، مجوز و سرور به همین Command Center متصل می‌شود.</span>
        </div>
      </div>
    </div>
  );
}

export function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [commandOpen, setCommandOpen] = useState(false);
  const [health, setHealth] = useState<"checking" | "online" | "offline">("checking");

  useEffect(() => {
    const stored = window.localStorage.getItem("nimhub-admin-sidebar-collapsed");
    setCollapsed(stored === "1");
  }, []);

  useEffect(() => {
    function keydown(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setCommandOpen(true);
      }
    }
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, []);

  useEffect(() => {
    let cancelled = false;
    async function check() {
      try {
        const response = await fetch("/api/v1/health/vip", { cache: "no-store" });
        if (!cancelled) setHealth(response.ok ? "online" : "offline");
      } catch {
        if (!cancelled) setHealth("offline");
      }
    }
    void check();
    const interval = window.setInterval(check, 60_000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, []);

  function toggleCollapsed() {
    setCollapsed((current) => {
      const next = !current;
      window.localStorage.setItem("nimhub-admin-sidebar-collapsed", next ? "1" : "0");
      return next;
    });
  }

  return (
    <div className={`admin-v2-shell${collapsed ? " is-collapsed" : ""}`}>
      <aside className="v2-sidebar">
        <div className="v2-sidebar-head">
          <Link className="v2-brand" href="/admin" aria-label="NimHUB Control Center">
            <Image className="v2-brand-logo" src="/nimhub-logo.png" width={38} height={38} alt="" priority />
            {!collapsed ? <span><strong>NIMHUB</strong><small>CONTROL CENTER</small></span> : null}
          </Link>
          <button
            type="button"
            className="v2-sidebar-collapse"
            onClick={toggleCollapsed}
            aria-label={collapsed ? "باز کردن سایدبار" : "جمع کردن سایدبار"}
          >
            <AdminIcon name="chevron-left" size={17} />
          </button>
        </div>
        <NavContent pathname={pathname} collapsed={collapsed} />
        <div className="v2-sidebar-foot">
          {!collapsed ? (
            <div className={`v2-health-card is-${health}`}>
              <span className="v2-health-dot" />
              <span><strong>Control Plane</strong><small>{health === "online" ? "Operational" : health === "offline" ? "Unavailable" : "Checking…"}</small></span>
            </div>
          ) : <span className={`v2-health-dot standalone is-${health}`} title={`Control Plane: ${health}`} />}
          {!collapsed ? <LogoutButton /> : null}
        </div>
      </aside>

      <div className="v2-workspace">
        <header className="v2-topbar">
          <div className="v2-topbar-start">
            <button
              className="v2-icon-button v2-mobile-menu"
              type="button"
              onClick={() => setMobileOpen(true)}
              aria-label="باز کردن منوی مدیریت"
              aria-expanded={mobileOpen}
            >
              <AdminIcon name="menu" />
            </button>
            <div className="v2-page-context">
              <span>NimHUB /</span>
              <strong>{pageTitle(pathname)}</strong>
            </div>
          </div>
          <div className="v2-topbar-actions">
            <button className="v2-search-trigger" type="button" onClick={() => setCommandOpen(true)}>
              <AdminIcon name="search" size={17} />
              <span>جست‌وجو</span>
              <kbd>Ctrl K</kbd>
            </button>
            <div className={`v2-system-pill is-${health}`} title="وضعیت Control Plane">
              <span className="v2-health-dot" />
              <span>{health === "online" ? "سالم" : health === "offline" ? "اختلال" : "بررسی"}</span>
            </div>
            <Link className="v2-icon-button" href="/admin/notifications" aria-label="اعلان‌ها">
              <AdminIcon name="bell" />
            </Link>
          </div>
        </header>

        <main className="v2-main-content">{children}</main>
      </div>

      {mobileOpen ? (
        <div className="v2-mobile-layer" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target) setMobileOpen(false);
        }}>
          <aside className="v2-mobile-drawer" aria-label="منوی مدیریت موبایل">
            <div className="v2-mobile-drawer-head">
              <Link className="v2-brand" href="/admin" onClick={() => setMobileOpen(false)}>
                <Image className="v2-brand-logo" src="/nimhub-logo.png" width={36} height={36} alt="" />
                <span><strong>NIMHUB</strong><small>CONTROL CENTER</small></span>
              </Link>
              <button className="v2-icon-button" type="button" onClick={() => setMobileOpen(false)} aria-label="بستن منو">
                <AdminIcon name="x" />
              </button>
            </div>
            <NavContent pathname={pathname} onNavigate={() => setMobileOpen(false)} />
            <div className="v2-mobile-drawer-foot"><LogoutButton /></div>
          </aside>
        </div>
      ) : null}

      <CommandPalette open={commandOpen} onClose={() => setCommandOpen(false)} />
    </div>
  );
}
