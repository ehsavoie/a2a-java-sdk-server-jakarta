package org.wildfly.a2a.jakarta.test.multitenancy;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.extras.multitenancy.Tenant;
import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;

@ApplicationScoped
public class MultiTenantAgentCardProducer {

    private static final String BASE_URL = "http://localhost:8080";

    @Produces
    @PublicAgentCard
    public AgentCard publicCard() {
        return card("Multi-Tenant Test Agent", BASE_URL, true);
    }

    @Produces
    @ExtendedAgentCard
    public AgentCard defaultExtendedCard() {
        return card("default-extended", BASE_URL, false);
    }

    @Produces
    @Tenant("acme")
    @ExtendedAgentCard
    public AgentCard acmeExtendedCard() {
        return card("acme-extended", BASE_URL + "/acme", false);
    }

    // Tenant public cards carry ONLY @Tenant (no @PublicAgentCard) — CdiAgentCardRouter
    // resolvePublicCard matches @Tenant beans that carry neither card marker.
    @Produces
    @Tenant("acme")
    public AgentCard acmePublicCard() {
        return card("Acme Agent", BASE_URL + "/acme", true);
    }

    @Produces
    @Tenant("beta")
    @ExtendedAgentCard
    public AgentCard betaExtendedCard() {
        return card("beta-extended", BASE_URL + "/beta", false);
    }

    @Produces
    @Tenant("beta")
    public AgentCard betaPublicCard() {
        return card("Beta Agent", BASE_URL + "/beta", true);
    }

    private static AgentCard card(String name, String httpUrl, boolean withCapabilities) {
        AgentCapabilities capabilities = withCapabilities
                ? AgentCapabilities.builder().streaming(true).extendedAgentCard(true).build()
                : AgentCapabilities.builder().build();
        return AgentCard.builder()
                .name(name)
                .description(name)
                .version("1.0.0")
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .capabilities(capabilities)
                .skills(List.of())
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), BASE_URL),
                        new AgentInterface(TransportProtocol.HTTP_JSON.asString(), httpUrl)))
                .build();
    }
}
