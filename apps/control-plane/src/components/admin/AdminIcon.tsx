import type { SVGProps } from "react";

export type AdminIconName =
  | "activity"
  | "bell"
  | "chevron-left"
  | "command"
  | "dashboard"
  | "menu"
  | "search"
  | "server"
  | "settings"
  | "sliders"
  | "users"
  | "x";

type PathDefinition = string | string[];

const paths: Record<AdminIconName, PathDefinition> = {
  activity: "M3 12h4l2.5-6 5 12 2.5-6H21",
  bell: [
    "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9",
    "M10 21h4",
  ],
  "chevron-left": "m15 18-6-6 6-6",
  command: [
    "M18 9a3 3 0 1 0-3-3v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3V6a3 3 0 1 0-3 3h12Z",
  ],
  dashboard: [
    "M4 13h6V4H4z",
    "M14 20h6v-9h-6z",
    "M14 4h6v3h-6z",
    "M4 17h6v3H4z",
  ],
  menu: ["M4 7h16", "M4 12h16", "M4 17h16"],
  search: ["m21 21-4.35-4.35", "M10.8 18a7.2 7.2 0 1 1 0-14.4 7.2 7.2 0 0 1 0 14.4Z"],
  server: [
    "M4 4h16v6H4z",
    "M4 14h16v6H4z",
    "M8 7h.01",
    "M8 17h.01",
  ],
  settings: [
    "M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z",
    "M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.1 3.63-.09-.03a1.7 1.7 0 0 0-1.84.18l-.7.4a1.7 1.7 0 0 0-.84 1.68V23h-4.2v-.1a1.7 1.7 0 0 0-.84-1.68l-.7-.4a1.7 1.7 0 0 0-1.84-.18l-.09.03-2.1-3.63.06-.06A1.7 1.7 0 0 0 4.6 15l-.4-.7a1.7 1.7 0 0 0-1.5-1H2v-4.2h.1a1.7 1.7 0 0 0 1.5-1l.4-.7a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.1-3.63.09.03a1.7 1.7 0 0 0 1.84-.18l.7-.4A1.7 1.7 0 0 0 9.17-.4V-.5h4.2v.1a1.7 1.7 0 0 0 .84 1.68l.7.4a1.7 1.7 0 0 0 1.84.18l.09-.03 2.1 3.63-.06.06a1.7 1.7 0 0 0-.34 1.88l.4.7a1.7 1.7 0 0 0 1.5 1h.1v4.2h-.1a1.7 1.7 0 0 0-1.5 1Z",
  ],
  sliders: ["M4 6h10", "M18 6h2", "M4 12h2", "M10 12h10", "M4 18h8", "M16 18h4", "M14 4v4", "M8 10v4", "M14 16v4"],
  users: [
    "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
    "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z",
    "M22 21v-2a4 4 0 0 0-3-3.87",
    "M16 3.13a4 4 0 0 1 0 7.75",
  ],
  x: ["M6 6l12 12", "M18 6 6 18"],
};

export function AdminIcon({
  name,
  size = 18,
  ...props
}: SVGProps<SVGSVGElement> & { name: AdminIconName; size?: number }) {
  const definition = paths[name];
  const items = Array.isArray(definition) ? definition : [definition];
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...props}
    >
      {items.map((path, index) => <path d={path} key={`${name}-${index}`} />)}
    </svg>
  );
}
