import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Study Frog · Creative Studio",
  icons: { icon: "/studyfrog-icon.png" },
  description: "Local image and video creation for Study Frog.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return <html lang="en"><body>{children}</body></html>;
}
