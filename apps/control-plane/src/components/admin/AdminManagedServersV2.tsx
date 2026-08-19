"use client";

import { useMemo, useState } from "react";
import { VipNodeControlForm } from "@/components/VipNodeForms";
import { AdminIcon } from "./AdminIcon";

type ManagedNode = {
  id: string;
  name: string;
  provider: string;
  providerTag: string | null;
  isPasarGuard: boolean;
  logicalCopies: number;
  accessTier: "STANDARD" | "VIP";
  mixedAccessTier: boolean;
  status: string;
  mixedStatus: boolean;
  regionName: string;
  countryCode: string;
  protocol: string;
  host: string;
  port: number;
  assignedUsers: number;
  allowedDevices: number;
  capacity: number | null;
  activeSessions: number | null;
  lastSeenAt: string | null;
  lastSyncAt: string | null;
};

function formatNumber(value: number): string {
  return new Intl.NumberFormat("fa-IR").format(value);
}

function formatDateTime(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("fa-IR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function statusLabel(value: string, mixed = false): string {
  if (mixed) return "ترکیبی";
  if (value === "ONLINE") return "آنلاین";
  if (value === "DEGRADED") return "اختلال";
  if (value === "MAINTENANCE") return "نگهداری";
  return "آفلاین";
}

function statusTone(value: string): string {
  if (value === "ONLINE") return "success";
  if (value === "DEGRADED" || value === "MAINTENANCE") return "warning";
  return "danger";
}

export function AdminManagedServersV2({ nodes }: { nodes: ManagedNode[] }) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [tier, setTier] = useState("ALL");
  const [provider, setProvider] = useState("ALL");

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return nodes.filter((node) => {
      if (status !== "ALL" && node.status !== status) return false;
      if (tier !== "ALL" && node.accessTier !== tier) return false;
      if (provider === "PASARGUARD" && !node.isPasarGuard) return false;
      if (provider === "OTHER" && node.isPasarGuard) return false;
      if (!normalized) return true;
      return `${node.name} ${node.providerTag ?? ""} ${node.regionName} ${node.countryCode} ${node.host} ${node.protocol}`.toLowerCase().includes(normalized);
    });
  }, [nodes, provider, query, status, tier]);

  const summary = useMemo(() => {
    const online = nodes.filter((node) => node.status === "ONLINE" && !node.mixedStatus).length;
    const attention = nodes.length - online;
    const vip = nodes.filter((node) => node.accessTier === "VIP" && !node.mixedAccessTier).length;
    const pasarGuard = nodes.filter((node) => node.isPasarGuard).length;
    return { online, attention, vip, pasarGuard };
  }, [nodes]);

  return (
    <section className="v2-managed-servers">
      <div className="v2-server-summary" aria-label="خلاصه وضعیت سرورها">
        <div><small>کل سرورهای واقعی</small><strong>{formatNumber(nodes.length)}</strong></div>
        <div><small>Pasargard</small><strong>{formatNumber(summary.pasarGuard)}</strong></div>
        <div><small>آنلاین</small><strong>{formatNumber(summary.online)}</strong></div>
        <div><small>نیازمند بررسی</small><strong>{formatNumber(summary.attention)}</strong></div>
        <div><small>VIP</small><strong>{formatNumber(summary.vip)}</strong></div>
      </div>

      <div className="v2-server-toolbar">
        <div className="v2-server-search">
          <AdminIcon name="search" size={16} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="نام، کشور، آدرس یا پروتکل…" aria-label="جست‌وجوی سرورها" />
        </div>
        <select value={provider} onChange={(event) => setProvider(event.target.value)} aria-label="فیلتر Provider">
          <option value="ALL">همه Providerها</option>
          <option value="PASARGUARD">Pasargard</option>
          <option value="OTHER">سایر Managed</option>
        </select>
        <select value={status} onChange={(event) => setStatus(event.target.value)} aria-label="فیلتر وضعیت سرور">
          <option value="ALL">همه وضعیت‌ها</option>
          <option value="ONLINE">Online</option>
          <option value="DEGRADED">Degraded</option>
          <option value="OFFLINE">Offline</option>
          <option value="MAINTENANCE">Maintenance</option>
        </select>
        <select value={tier} onChange={(event) => setTier(event.target.value)} aria-label="فیلتر سطح دسترسی">
          <option value="ALL">Standard + VIP</option>
          <option value="STANDARD">Standard</option>
          <option value="VIP">VIP</option>
        </select>
        <span className="v2-server-result-count">{formatNumber(filtered.length)} نتیجه</span>
      </div>

      <div className="v2-server-table-wrap">
        <table className="v2-server-table">
          <thead><tr><th>سرور</th><th>Provider</th><th>Health</th><th>منطقه</th><th>Endpoint</th><th>کاربران</th><th>آخرین Sync</th><th>Access</th><th>کنترل</th></tr></thead>
          <tbody>
            {filtered.map((node) => (
              <tr key={`${node.isPasarGuard ? "pg" : "node"}-${node.id}`}>
                <td>
                  <div className="v2-server-name">
                    <span className={`v2-status-dot is-${statusTone(node.status)}`} />
                    <span>
                      <strong>{node.name}</strong>
                      <small dir="ltr">{node.protocol}{node.isPasarGuard && node.logicalCopies > 1 ? ` · ${formatNumber(node.logicalCopies)} copies grouped` : ""}</small>
                    </span>
                  </div>
                </td>
                <td><span className="v2-server-protocol">{node.isPasarGuard ? "Pasargard" : node.provider}</span></td>
                <td><span className={`v2-server-state is-${statusTone(node.status)}`}>{statusLabel(node.status, node.mixedStatus)}</span></td>
                <td><span className="v2-server-region"><strong>{node.regionName}</strong><small dir="ltr">{node.countryCode}</small></span></td>
                <td><code dir="ltr">{node.host}:{node.port}</code></td>
                <td>
                  <span className="v2-server-region">
                    <strong>{formatNumber(node.assignedUsers)} کاربر</strong>
                    <small>{formatNumber(node.allowedDevices)} دستگاه مجاز</small>
                  </span>
                </td>
                <td><span className="v2-server-last-seen">{formatDateTime(node.isPasarGuard ? node.lastSyncAt : node.lastSeenAt)}</span></td>
                <td>
                  {node.mixedAccessTier
                    ? <span className="v2-server-access is-vip">Mixed</span>
                    : node.accessTier === "VIP"
                      ? <span className="v2-server-access is-vip">VIP</span>
                      : <span className="v2-server-access">Standard</span>}
                </td>
                <td>
                  <VipNodeControlForm
                    id={node.id}
                    status={node.status}
                    capacity={node.isPasarGuard ? null : node.capacity}
                    accessTier={node.accessTier}
                    logicalScope={node.isPasarGuard ? "PASARGUARD" : undefined}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!filtered.length ? <div className="v2-server-empty"><AdminIcon name="server" size={22} /><strong>سروری پیدا نشد</strong><span>فیلترها یا عبارت جست‌وجو را تغییر بده.</span></div> : null}
      </div>

      <div className="v2-server-mobile-list">
        {filtered.map((node) => (
          <article className="v2-server-mobile-card" key={`mobile-${node.id}`}>
            <div className="v2-server-mobile-head"><span className={`v2-status-dot is-${statusTone(node.status)}`} /><span><strong>{node.name}</strong><small>{node.regionName} · {node.isPasarGuard ? "Pasargard" : node.provider}</small></span><span className={`v2-server-state is-${statusTone(node.status)}`}>{statusLabel(node.status, node.mixedStatus)}</span></div>
            <div className="v2-server-mobile-facts"><span><small>Endpoint</small><code dir="ltr">{node.host}:{node.port}</code></span><span><small>Users</small><strong>{formatNumber(node.assignedUsers)}</strong></span><span><small>Access</small><strong>{node.mixedAccessTier ? "Mixed" : node.accessTier === "VIP" ? "VIP" : "Standard"}</strong></span></div>
            <div className="v2-server-mobile-control"><VipNodeControlForm id={node.id} status={node.status} capacity={node.isPasarGuard ? null : node.capacity} accessTier={node.accessTier} logicalScope={node.isPasarGuard ? "PASARGUARD" : undefined} /></div>
          </article>
        ))}
      </div>
    </section>
  );
}
