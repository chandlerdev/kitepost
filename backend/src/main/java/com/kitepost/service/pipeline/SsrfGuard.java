package com.kitepost.service.pipeline;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Stops the website reader from being used to reach internal systems (SSRF):
 * only http/https on ports 80/443, and never loopback, private, link-local,
 * carrier-grade NAT, multicast or cloud metadata addresses. Checked for every redirect.
 */
public final class SsrfGuard {
    private SsrfGuard() {}

    public static class BlockedException extends IOException {
        public BlockedException(String msg) { super(msg); }
    }

    public static void check(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new BlockedException("only http and https are allowed");
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) throw new BlockedException("port " + port + " is not allowed");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new BlockedException("missing host");
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.equals("metadata.google.internal"))
            throw new BlockedException("internal host names are not allowed");
        if (uri.getUserInfo() != null) throw new BlockedException("credentials in URLs are not allowed");
        InetAddress[] addresses;
        try { addresses = InetAddress.getAllByName(host); }
        catch (UnknownHostException e) { throw new IOException("unknown host " + host); }
        for (InetAddress a : addresses) if (isInternal(a)) throw new BlockedException(host + " resolves to an internal address");
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            if (b0 == 0) return true;                                  // 0.0.0.0/8
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return true;       // 100.64.0.0/10 carrier-grade NAT
            if (b0 == 192 && b1 == 0 && (b[2] & 0xff) == 0) return true; // 192.0.0.0/24
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true;      // 198.18.0.0/15 benchmarking
            if (b0 >= 224) return true;                                // multicast and reserved
            return false;
        }
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return true;                    // fc00::/7 unique local
            boolean mapped = true;                                     // ::ffff:a.b.c.d → check the IPv4 part
            for (int i = 0; i < 10; i++) if (b[i] != 0) { mapped = false; break; }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try { return isInternal(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]})); }
                catch (UnknownHostException e) { return true; }
            }
        }
        return false;
    }
}
