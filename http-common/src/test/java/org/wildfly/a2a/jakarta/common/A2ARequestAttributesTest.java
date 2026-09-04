package org.wildfly.a2a.jakarta.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class A2ARequestAttributesTest {

    @Test
    void extractTenant_reservedRootsReturnEmpty() {
        assertEquals("", A2ARequestAttributes.extractTenant("message:send"));
        assertEquals("", A2ARequestAttributes.extractTenant("/message:send"));
        assertEquals("", A2ARequestAttributes.extractTenant("tasks/abc"));
        assertEquals("", A2ARequestAttributes.extractTenant("card"));
        assertEquals("", A2ARequestAttributes.extractTenant("extendedAgentCard"));
        assertEquals("", A2ARequestAttributes.extractTenant(".well-known/agent-card.json"));
        assertEquals("", A2ARequestAttributes.extractTenant(""));
        assertEquals("", A2ARequestAttributes.extractTenant(null));
    }

    @Test
    void extractTenant_tenantPrefixedPathsReturnTenant() {
        assertEquals("acme", A2ARequestAttributes.extractTenant("acme/message:send"));
        assertEquals("acme", A2ARequestAttributes.extractTenant("/acme/message:send"));
        assertEquals("beta", A2ARequestAttributes.extractTenant("beta/tasks/123"));
        assertEquals("acme", A2ARequestAttributes.extractTenant("acme/extendedAgentCard"));
    }

    @Test
    void extractWellKnownTenant() {
        assertEquals("", A2ARequestAttributes.extractWellKnownTenant("/.well-known/agent-card.json"));
        assertEquals("", A2ARequestAttributes.extractWellKnownTenant(".well-known/agent-card.json"));
        assertEquals("acme", A2ARequestAttributes.extractWellKnownTenant("/.well-known/acme/agent-card.json"));
        assertEquals("beta", A2ARequestAttributes.extractWellKnownTenant(".well-known/beta/agent-card.json"));
    }
}
