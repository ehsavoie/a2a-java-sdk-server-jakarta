package org.wildfly.a2a.jakarta.test.multitenancy.jsonrpc;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.a2aproject.sdk.extras.multitenancy.CdiAgentExecutorRouter;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wildfly.a2a.jakarta.test.multitenancy.MultiTenantAgentCardProducer;

@ArquillianTest
@RunAsClient
public class MultiTenantJsonRpcTest {

    @BeforeAll
    static void restAssuredSetup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 8080;
    }

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
        JavaArchive[] libraries = List.of(
                getJarForClass(org.a2aproject.sdk.util.Assert.class),
                // a2a-java-sdk-http-client: needed by BasePushNotificationSender in server-common
                getJarForClass(org.a2aproject.sdk.client.http.A2AHttpClient.class),
                getJarForClass(org.a2aproject.sdk.server.PublicAgentCard.class),
                getJarForClass(org.a2aproject.sdk.spec.Event.class),
                getJarForClass(org.a2aproject.sdk.grpc.utils.JSONRPCUtils.class),
                // a2a-java-sdk-transport-jsonrpc: JSONRPCHandler
                getJarForClass(org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler.class),
                getJarForClass(org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.class),
                getJarForClass(com.google.gson.Gson.class),
                getJarForClass(com.google.protobuf.InvalidProtocolBufferException.class),
                getJarForClass(com.google.protobuf.util.JsonFormat.class),
                getJarForClass(com.google.api.AnnotationsProto.class),
                getJarForClass(com.google.common.collect.ImmutableSet.class),
                // a2a-jakarta-http-common: filters + A2ARequestAttributes
                getJarForClass(org.wildfly.a2a.jakarta.common.SSESubscriber.class),
                // a2a-jakarta-jsonrpc: resource + delegate
                getJarForClass(org.wildfly.a2a.jakarta.jsonrpc.A2AServerResource.class),
                // microprofile-config: enables reading microprofile-config.properties
                getJarForClass(org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider.class),
                getJarForClass(mutiny.zero.ZeroPublisher.class),
                // extras-multitenancy: CdiAgentExecutorRouter + CdiAgentCardRouter + @Tenant
                getJarForClass(CdiAgentExecutorRouter.class),
                // shared tenant producers
                getJarForClass(MultiTenantAgentCardProducer.class)
        ).toArray(JavaArchive[]::new);

        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addClass(JsonRpcApplication.class)
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsWebInfResource("WEB-INF/web.xml", "web.xml")
                .addAsResource("META-INF/disable-authorization-microprofile-config.properties",
                        "META-INF/microprofile-config.properties");
    }

    // ---------- helpers ----------

    private static String tenantField(String tenant) {
        return tenant != null && !tenant.isBlank() ? ", \"tenant\": \"" + tenant + "\"" : "";
    }

    private static String sendBody(String method, String tenant) {
        return """
                {"jsonrpc":"2.0","id":"1","method":"%s","params":{"message":{"messageId":"%s","role":"ROLE_USER","parts":[{"text":"hello"}]}%s}}"""
                .formatted(method, UUID.randomUUID(), tenantField(tenant));
    }

    private static String extendedCardBody(String tenant) {
        return tenant == null
                ? "{\"jsonrpc\":\"2.0\",\"method\":\"GetExtendedAgentCard\",\"id\":\"1\"}"
                : "{\"jsonrpc\":\"2.0\",\"method\":\"GetExtendedAgentCard\",\"id\":\"1\",\"params\":{\"tenant\":\"" + tenant + "\"}}";
    }

    private static Response rpc(String body) {
        return given().header("A2A-Version", "1.0").contentType("application/json").body(body).when().post("/");
    }

    private static Response getPublicCard(String tenant) {
        String path = tenant != null ? "/.well-known/" + tenant + "/agent-card.json" : "/.well-known/agent-card.json";
        return given().when().get(path);
    }

    // ---------- executor routing ----------

    @Test
    public void knownTenantRoutesToAcmeExecutor() {
        assertThat(rpc(sendBody("SendMessage", "acme")).getBody().asString(), containsString("acme"));
    }

    @Test
    public void secondTenantRoutesToBetaExecutor() {
        assertThat(rpc(sendBody("SendMessage", "beta")).getBody().asString(), containsString("beta"));
    }

    @Test
    public void unknownTenantFallsBackToDefault() {
        assertThat(rpc(sendBody("SendMessage", "unknown-corp")).getBody().asString(), containsString("default"));
    }

    @Test
    public void nullTenantUsesDefault() {
        assertThat(rpc(sendBody("SendMessage", null)).getBody().asString(), containsString("default"));
    }

    // ---------- extended card ----------

    @Test
    public void getExtendedAgentCardWithAcmeTenant() {
        assertThat(rpc(extendedCardBody("acme")).getBody().asString(), containsString("acme-extended"));
    }

    @Test
    public void getExtendedAgentCardWithBetaTenant() {
        assertThat(rpc(extendedCardBody("beta")).getBody().asString(), containsString("beta-extended"));
    }

    @Test
    public void getExtendedAgentCardWithUnknownTenant() {
        assertThat(rpc(extendedCardBody("unknown")).getBody().asString(), containsString("default-extended"));
    }

    @Test
    public void getExtendedAgentCardWithoutTenant() {
        assertThat(rpc(extendedCardBody(null)).getBody().asString(), containsString("default-extended"));
    }

    // ---------- streaming ----------

    @Test
    public void streamingWithKnownTenant() {
        Response r = rpc(sendBody("SendStreamingMessage", "acme"));
        assertEquals(200, r.getStatusCode());
        assertThat(r.getBody().asString(), containsString("\"text\":\"acme\""));
    }

    @Test
    public void streamingWithUnknownTenantUsesDefault() {
        assertThat(rpc(sendBody("SendStreamingMessage", "unknown")).getBody().asString(),
                containsString("\"text\":\"default\""));
    }

    // ---------- public card ----------

    @Test
    public void getPublicAgentCardWithAcmeTenant() {
        assertThat(getPublicCard("acme").getBody().asString(), containsString("Acme Agent"));
    }

    @Test
    public void getPublicAgentCardWithBetaTenant() {
        assertThat(getPublicCard("beta").getBody().asString(), containsString("Beta Agent"));
    }

    @Test
    public void getPublicAgentCardWithUnknownTenantReturns404() {
        assertEquals(404, getPublicCard("unknown").getStatusCode());
    }

    @Test
    public void getPublicAgentCardWithoutTenantReturnsDefault() {
        assertThat(getPublicCard(null).getBody().asString(), containsString("Multi-Tenant Test Agent"));
    }

    // ---------- concurrency ----------

    @Test
    public void concurrentRequestsForDifferentTenants() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<String> acme = pool.submit(() -> rpc(sendBody("SendMessage", "acme")).getBody().asString());
            Future<String> beta = pool.submit(() -> rpc(sendBody("SendMessage", "beta")).getBody().asString());
            Future<String> def = pool.submit(() -> rpc(sendBody("SendMessage", null)).getBody().asString());
            assertThat(acme.get(30, TimeUnit.SECONDS), containsString("acme"));
            assertThat(beta.get(30, TimeUnit.SECONDS), containsString("beta"));
            assertThat(def.get(30, TimeUnit.SECONDS), containsString("default"));
        } finally {
            pool.shutdownNow();
        }
    }
}
