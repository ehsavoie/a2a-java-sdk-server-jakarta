package org.wildfly.a2a.jakarta.grpc;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WildFlyGrpcHandlerTest {

    @AfterEach
    void clearStaticBeans() {
        WildFlyGrpcHandler.setStaticBeans(null, null, null, null, null, null, null);
    }

    @Test
    void getAgentCard_returnsNullWhenRouterHasNoDefaultPublicCard() {
        AgentCardRouter router = new AgentCardRouter() {
            @Override
            public AgentCard resolvePublicCard(String tenant) {
                return null;
            }

            @Override
            public AgentCard resolveExtendedCard(String tenant) {
                return null;
            }
        };
        WildFlyGrpcHandler.setStaticBeans(null, null, null, null, null, null, router);

        assertNull(new WildFlyGrpcHandler().getAgentCard());
    }
}
