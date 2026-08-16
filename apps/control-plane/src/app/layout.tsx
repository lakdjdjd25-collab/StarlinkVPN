import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "مدیریت NimHUB Vpn",
  description: "کنترل‌پلین خصوصی سرویس NimHUB Vpn",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fa" dir="rtl">
      <body>{children}</body>
    </html>
  );
}
