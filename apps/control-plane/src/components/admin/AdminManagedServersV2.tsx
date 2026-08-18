"use client";

import { useMemo, useState } from "react";
import { VipNodeControlForm } from "@/components/VipNodeForms";
import { AdminIcon } from "./AdminIcon";

type ManagedNode = {
  id: string;
  name: string;
  accessTier: "STANDARD" | "VIP";
  status: string;
  regionName: string;
  countryCode: string;
  protocol: string;
  host: string;
  port: number;
  activeSessions: number;
  capacity: number;
  lastSeenAt: string;
};

function formatNumber(value: number): string {
  return new Intl.NumberFormat("fa-IR").format(value);
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("fa-IR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function statusLabel(value: string): string {
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

function loadPercent(node: ManagedNode): number {
  if (node.capacity <= 0) return 0;
  return Math.min(100, Math.max(0, Math.round(node.activeSessions / node.capacity * 100)));
}

export function AdminManagedServersV2({ nodes }: { nodes: ManagedNode[] }) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [tier, setTier] = useState("ALL");

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return nodes.filter((node) => {
      if (status !== "ALL" && node.status !== status) return false;
      if (tier !== "ALL" && node.accessTier !== tier) return false;
      if (!normalized) return true;
      return `${node.name} ${node.regionName} ${node.countryCode} ${node.host} ${node.protocol}`.toLowerCase().includes(normalized);
    });
  }, [nodes, query, status, tier]);

  const summary = useMemo(() => {
    const online = nodes.filter((node) => node.status === "ONLINE").length;
    const attention = nodes.filter((node) => node.status !== "ONLINE").length;
    const vip = nodes.filter((node) => node.accessTier === "VIP").length;
    const sessions = nodes.reduce((sum, node) => sum + node.activeSessions, 0);
    const capacity = nodes.reduce((sum, node) => sum + node.capacity, 0);
    return { online, attention, vip, sessions, capacity };
  }, [nodes]);

  return (
    <section className="v2-managed-servers">
      <div className="v2-server-summary" aria-label="خلاصه وضعیت سرورها">
        <div><small>کل Managed</small><strong>{formatNumber(nodes.length)}</strong></div>
        <div><small>آنلاین</small><strong>{formatNumber(summary.online)}</strong></div>
        <div><small>نیازمند بررسی</small><strong>{formatNumber(summary.attention)}</strong></div>
        <div><small>VIP</small><strong>{formatNumber(summary.vip)}</strong></div>
        <div><small>Session / Capacity</small><strong dir="ltr">{formatNumber(summary.sessions)} / {formatNumber(summary.capacity)}</strong></div>
      </div>

      <div className="v2-server-toolbar">
        <div className="v2-server-search">
          <AdminIcon name="search" size={16} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="نام، کشور، آدرس یا پروتکل…" aria-label="جست‌وجوی سرورها" />
        </div>
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
          <thead><tr><th>سرور</th><th>Health</th><th>منطقه</th><th>Endpoint</th><th>Protocol</th><th>Load</th><th>آخرین مشاهده</th><th>Access</th><th>کنترل</th></tr></thead>
          <tbody>
            {filtered.map((node) => {
              const percent = loadPercent(node);
              return (
                <tr key={node.id}>
                  <td><div className="v2-server-name"><span className={`v2-status-dot is-${statusTone(node.status)}`} /><span><strong>{node.name}</strong><small dir="ltr">{node.id.slice(0, 8)}</small></span></div></td>
                  <td><span className={`v2-server-state is-${statusTone(node.status)}`}>{statusLabel(node.status)}</span></td>
                  <td><span className="v2-server-region"><strong>{node.regionName}</strong><small dir="ltr">{node.countryCode}</small></span></td>
                  <td><code dir="ltr">{node.host}:{node.port}</code></td>
                  <td><span className="v2-server-protocol">{node.protocol}</span></td>
                  <td><div className="v2-server-load"><span><strong>{formatNumber(node.activeSessions)}</strong><small> / {formatNumber(node.capacity)}</small></span><div><i style={{ width: `${percent}%` }} /></div></div></td>
                  <td><span className="v2-server-last-seen">{formatDateTime(node.lastSeenAt)}</span></td>
                  <td>{node.accessTier === "VIP" ? <span className="v2-server-access is-vip">VIP</span> : <span className="v2-server-access">Standard</span>}</td>
                  <td><VipNodeControlForm id={node.id} status={node.status} capacity={node.capacity} accessTier={node.accessTier} /></td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {!filtered.length ? <div className="v2-server-empty"><AdminIcon name="server" size={22} /><strong>سروری پیدا نشد</strong><span>فیلترها یا عبارت جست‌وجو را تغییر بده.</span></div> : null}
      </div>

      <div className="v2-server-mobile-list">
        {filtered.map((node) => {
          const percent = loadPercent(node);
          return (
            <article className="v2-server-mobile-card" key={node.id}>
              <div className="v2-server-mobile-head"><span className={`v2-status-dot is-${statusTone(node.status)}`} /><span><strong>{node.name}</strong><small>{node.regionName} · <span dir="ltr">{node.countryCode}</span></small></span><span className={`v2-server-state is-${statusTone(node.status)}`}>{statusLabel(node.status)}</span></div>
              <div className="v2-server-mobile-facts"><span><small>Endpoint</small><code dir="ltr">{node.host}:{node.port}</code></span><span><small>Protocol</small><strong>{node.protocol}</strong></span><span><small>Access</small><strong>{node.accessTier === "VIP" ? "VIP" : "Standard"}</strong></span></div>
              <div className="v2-server-load is-mobile"><span><strong>{formatNumber(node.activeSessions)}</strong><small> / {formatNumber(node.capacity)} Session</small></span><div><i style={{ width: `${percent}%` }} /></div></div>
              <div className="v2-server-mobile-control"><VipNodeControlForm id={node.id} status={node.status} capacity={node.capacity} accessTier={node.accessTier} /></div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
