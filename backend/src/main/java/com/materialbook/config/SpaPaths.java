package com.materialbook.config;

import jakarta.servlet.http.HttpServletRequest;

/** Which URL paths belong to the server (API, actuator, docs) and which are the browser app's (static files and client routes). */
public final class SpaPaths {
    private static final String[] SERVER_PREFIXES = {"/api", "/actuator", "/v3", "/swagger-ui"};

    private SpaPaths() {}

    /** True for paths that must never be answered with index.html (they are API/ops endpoints; an unknown one is a 404/401). */
    public static boolean isServerPath(String path) {
        for (String p : SERVER_PREFIXES) {
            if (path.equals(p) || path.startsWith(p + "/") || (p.equals("/swagger-ui") && path.equals("/swagger-ui.html"))) {
                return true;
            }
        }
        return false;
    }

    /** A client-side route: not a server path and the last segment has no file extension (so /suppliers/1/ledger, not /x.js). */
    public static boolean isClientRoute(String path) {
        if (isServerPath(path)) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }

    public static boolean isStaticRequest(HttpServletRequest request) {
        String m = request.getMethod();
        return ("GET".equals(m) || "HEAD".equals(m)) && !isServerPath(request.getRequestURI());
    }
}
