package org.wildfly.a2a.jakarta.common;

import java.util.Set;

/**
 * Single source of truth for the tenant request attribute and the reserved-segment
 * logic used to distinguish a tenant path prefix from an A2A operation root.
 */
public final class A2ARequestAttributes {

    /** Request attribute under which the @PreMatching filters stash the parsed tenant. */
    public static final String A2A_TENANT_ATTR = "org.wildfly.a2a.tenant";

    /** Leading REST path segments that denote an A2A operation root, never a tenant. */
    private static final Set<String> RESERVED_REST_SEGMENTS =
            Set.of("message", "tasks", "card", "extendedAgentCard", ".well-known");

    private static final String WELL_KNOWN = ".well-known/";
    private static final String AGENT_CARD_JSON = "agent-card.json";

    private A2ARequestAttributes() {
    }

    public static String extractTenant(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.isEmpty()) {
            return "";
        }
        int slash = p.indexOf('/');
        int colon = p.indexOf(':');
        String first;
        if (colon >= 0 && (slash < 0 || colon < slash)) {
            first = p.substring(0, colon);
        } else if (slash > 0) {
            first = p.substring(0, slash);
        } else {
            first = p;
        }
        return RESERVED_REST_SEGMENTS.contains(first) ? "" : first;
    }

    public static String extractWellKnownTenant(String path) {
        if (path == null) {
            return "";
        }
        int idx = path.indexOf(WELL_KNOWN);
        if (idx < 0) {
            return "";
        }
        String after = path.substring(idx + WELL_KNOWN.length());
        int slash = after.indexOf('/');
        if (slash < 0) {
            return "";
        }
        String candidate = after.substring(0, slash);
        return candidate.equals(AGENT_CARD_JSON) ? "" : candidate;
    }
}
