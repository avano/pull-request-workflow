package com.github.avano.pr.workflow;

import static org.junit.jupiter.api.Assertions.fail;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import com.github.avano.pr.workflow.mock.GHClientMock;
import com.github.avano.pr.workflow.util.Invocation;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import javax.inject.Inject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.vertx.mutiny.core.eventbus.DeliveryContext;
import io.vertx.mutiny.core.eventbus.EventBus;

public class TestParent {
    public static final String TEST_REPO = "test/repo";
    public static final int PULL_REQUEST_ID = 1337;

    public static final String PR_PATCH_URL = "/repos/" + TEST_REPO + "/issues/\\d+";
    public static final RequestPatternBuilder PR_PATCH = WireMock.patchRequestedFor(urlPathMatching(PR_PATCH_URL));

    @Inject
    GHClientMock client;

    @Inject
    EventBus bus;

    protected static List<Invocation> busInvocations = Collections.synchronizedList(new ArrayList<>());
    protected static WireMockServer server;

    private Consumer<DeliveryContext<Object>> testInterceptor = dc -> busInvocations.add(new Invocation(dc.message().address(), dc.message().body()));

    protected GHPullRequest loadPullRequest(int id) {
        try {
            return client.getRepository().getPullRequest(id);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @BeforeAll
    static void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().port(29999).extensions(new ResponseTemplateTransformer(true)));
        server.start();
        WireMock.configureFor("localhost", 29999);
    }

    @BeforeEach
    public void setup() {
        bus.addInboundInterceptor(testInterceptor);

        if (!client.isInitialized()) {
            client.init(-1);
        }

        // Repository object Json
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO))
            .willReturn(ok().withBodyFile("repository/repo.json")));

        // Loading PRs from Json
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+"))
            .willReturn(ok().withBodyFile("pullrequests/{{request.requestLine.pathSegments.[4]}}.json")));

        // For labels, the client uses "issues" instead of "pulls"
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+"))
            .willReturn(ok().withBodyFile("pullrequests/{{request.requestLine.pathSegments.[4]}}.json")));

        // Merge endpoint for any PR
        stubFor(WireMock.put(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/merge"))
            .willReturn(ok()));

        // User object Json
        stubFor(WireMock.get(urlPathMatching("/users/.*"))
            .willReturn(ok().withBodyFile("users/{{request.requestLine.pathSegments.[1]}}.json")));

        // Branch protection
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master"))
            .willReturn(ok().withBodyFile("merge/checks/branchProtection.json")));

        // For most of the tests, don't use any required check
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(status(404).withBody("Branch not protected")));

        // By default all PRs are approved
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/reviews"))
            .willReturn(ok().withBodyFile("reviews/approvedReviewOnly.json")));

        // For labels
        stubFor(WireMock.patch(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+"))
            .willReturn(ok()));

        // Return empty reviewers
        stubFor(WireMock.post(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/requested_reviewers"))
            .willReturn(created().withBody("[]")));

        // Return OK for PR patches (assigning assignees and stuff)
        stubFor(WireMock.put(urlPathMatching(PR_PATCH_URL))
            .willReturn(ok()));
    }

    @AfterEach
    public void reset() {
        server.resetMappings();
        WireMock.resetAllRequests();
        bus.removeInboundInterceptor(testInterceptor);
        busInvocations.clear();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    public void waitFor(BooleanSupplier bs, int timeoutInSeconds) {
        int elapsed = 0;
        while (!bs.getAsBoolean() && elapsed < timeoutInSeconds * 1000L) {
            sleep(50L);
            elapsed += 50;
        }
    }

    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            fail("Sleep interrupted", e);
        }
    }

    public String lastDestination() {
        waitFor(() -> !busInvocations.isEmpty(), 1);
        return busInvocations.isEmpty() ? null : busInvocations.get(busInvocations.size() - 1).getDestination();
    }

    public Object lastMessage() {
        waitFor(() -> !busInvocations.isEmpty(), 1);
        return busInvocations.isEmpty() ? null : busInvocations.get(busInvocations.size() - 1).getMessage();
    }

    protected List<LoggedRequest> getRequests(RequestPatternBuilder pattern) {
        return WireMock.findAll(pattern);
    }

    protected List<Invocation> getInvocations(String destination) {
        // Avoid concurrentmodificationexception by creating a new array first
        return new ArrayList<>(busInvocations).stream().filter(i -> destination.equals(i.getDestination())).collect(Collectors.toList());
    }

    protected void waitForInvocations(String destination, int count) {
        waitFor(() -> getInvocations(destination).size() == count, 5);
    }

    protected void waitForInvocations(int count) {
        waitFor(() -> busInvocations.size() == count, 5);
    }

    protected void waitForInvocationsAndAssert(int count) {
        waitForInvocations(count);
        assertThat(busInvocations).hasSize(count);
    }

    protected void waitForInvocationsAndAssert(String destination, int count) {
        waitForInvocations(destination, count);
        assertThat(getInvocations(destination)).hasSize(count);
    }

    protected <T> T getInstance(Class<T> clazz, Map<String, Object> fields) {
        try {
            T inst = clazz.newInstance();
            fields.forEach((k, v) -> setField(inst, k, v));
            return inst;
        } catch (Exception e) {
            fail("Unable to create instance: ", e);
        }
        return null;
    }

    protected void setField(Object pr, String fieldName, Object fieldValue) {
        Field f = null;
        try {
            f = pr.getClass().getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // Try superclass
            try {
                f = pr.getClass().getSuperclass().getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                fail("Unable to find field with name " + fieldName);
            }
        }

        f.setAccessible(true);
        try {
            f.set(pr, fieldValue);
        } catch (IllegalAccessException e) {
            fail("Unable to set value " + fieldValue.toString() + " to field " + fieldName + ": " + e);
        }
    }

    public Map<String, Object> fields(Object... values) {
        if (values.length % 2 != 0) {
            fail("Incorrect parameters count for fields");
        }
        Map<String, Object> fields = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            fields.put(values[i].toString(), values[i + 1]);
        }
        return fields;
    }

    protected GHPullRequest getPullRequestWithReviewers(String... reviewers) {
        GHPullRequest pr = loadPullRequest(PULL_REQUEST_ID);
        setField(pr, "requested_reviewers", getUsers(reviewers));
        return pr;
    }

    protected GHUser[] getUsers(String... users) {
        GHUser[] usersArray = new GHUser[users.length];
        for (int i = 0; i < users.length; i++) {
            usersArray[i] = getInstance(GHUser.class, fields("login", users[i]));
        }
        return usersArray;
    }
}
