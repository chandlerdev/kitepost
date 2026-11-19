import type { NextConfig } from "next";

// The Spring Boot API. The browser only ever talks to this Next.js server;
// /api/* is proxied, so session and CSRF cookies are same-origin and no CORS is needed.
const backend = (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
const dev = process.env.NODE_ENV !== "production";
// Served under a path on a shared domain (e.g. /kitepost)? Set NEXT_PUBLIC_BASE_PATH at build time. Blank = domain root.
const basePath = process.env.NEXT_PUBLIC_BASE_PATH || undefined;

const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${dev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
  "font-src 'self' https://fonts.gstatic.com",
  "img-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'"
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  ...(dev ? [] : [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" }])
];

const nextConfig: NextConfig = {
  output: "standalone",
  basePath,
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  }
};

export default nextConfig;
