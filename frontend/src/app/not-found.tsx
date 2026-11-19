import Link from "next/link";
export default function NotFound() {
  return <div className="load-error"><h2>Page not found</h2><p className="muted">This page doesn&apos;t exist.</p><Link className="btn" href="/campaigns" style={{ alignSelf: "flex-start" }}>Go to campaigns</Link></div>;
}
