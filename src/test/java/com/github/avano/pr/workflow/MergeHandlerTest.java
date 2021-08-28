package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.json.JSONObject;
import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.MergeHandler;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.ConflictMessage;
import com.github.avano.pr.workflow.util.Invocation;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import javax.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MergeHandlerTest extends TestParent {
    private static final int OK_PR_ID = PULL_REQUEST_ID;
    private static final int MERGED_PR_ID = 1;
    private static final int DRAFT_PR_ID = 2;
    private static final int WIP_PR_ID = 3;
    private static final int NOT_MERGEABLE_PR_ID = 4;
    private static final int DEPENDABOT_PR_ID = 5;

    @Inject
    MergeHandler mergeHandler;

    @Override
    @BeforeEach
    public void setup() {
        super.setup();
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls?state=open"))
            .willReturn(ok().withBody("[]")));
    }

    @Test
    public void shouldMergeTest() {
        GHPullRequest pr = loadPullRequest(PULL_REQUEST_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isTrue();
    }

    @Test
    public void shouldNotMergeMergedTest()  {
        GHPullRequest pr = loadPullRequest(MERGED_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeDraftTest() {
        GHPullRequest pr = loadPullRequest(DRAFT_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWipTest() {
        GHPullRequest pr = loadPullRequest(WIP_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWithNonSuccessCheckTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(ok().withBodyFile("merge/checks/requiredChecks-checkruns.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/commits/asdfgh/check-runs"))
            .willReturn(ok().withBodyFile("merge/checks/oneNonSuccessCheckRun.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/statuses/asdfgh"))
            .willReturn(ok().withBody("[]")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWithFailingStatusTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(ok().withBodyFile("merge/checks/requiredChecks-status.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/commits/asdfgh/check-runs"))
            .willReturn(ok().withBody("{\n" +
                "  \"total_count\": 0,\n" +
                "  \"check_runs\": [\n" +
                "\n" +
                "  ]\n" +
                "}")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/statuses/asdfgh"))
            .willReturn(ok().withBodyFile("merge/checks/oneNonSuccessStatus.json")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldMergeWithPassingCheckTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(ok().withBodyFile("merge/checks/requiredChecks-checkruns.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/commits/asdfgh/check-runs"))
            .willReturn(ok().withBodyFile("merge/checks/successCheckRuns.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/statuses/asdfgh"))
            .willReturn(ok().withBody("[]")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isTrue();
    }

    @Test
    public void shouldMergeWithPassingStatusTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(ok().withBodyFile("merge/checks/requiredChecks-status.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/commits/asdfgh/check-runs"))
            .willReturn(ok().withBody("{\n" +
                "  \"total_count\": 0,\n" +
                "  \"check_runs\": [\n" +
                "\n" +
                "  ]\n" +
                "}")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/statuses/asdfgh"))
            .willReturn(ok().withBodyFile("merge/checks/successStatus.json")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isTrue();
    }

    @Test
    public void shouldIgnoreFailingNotRequiredCheckTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(ok().withBodyFile("merge/checks/requiredChecks-checkruns.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/commits/asdfgh/check-runs"))
            .willReturn(ok().withBodyFile("merge/checks/failingNonRequiredCheckRun.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/statuses/asdfgh"))
            .willReturn(ok().withBody("[]")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isTrue();
    }

    @Test
    public void shouldIgnoreFailingNotRequiredStatusTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(ok().withBodyFile("merge/checks/requiredChecks-status.json")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/commits/asdfgh/check-runs"))
            .willReturn(ok().withBody("{\n" +
                "  \"total_count\": 0,\n" +
                "  \"check_runs\": [\n" +
                "\n" +
                "  ]\n" +
                "}")));

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/statuses/asdfgh"))
            .willReturn(ok().withBodyFile("merge/checks/failingNonRequiredStatus.json")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isTrue();
    }

    @Test
    public void shouldNotMergeWithoutReviewsTest() {
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/reviews"))
            .willReturn(ok().withBody("[]")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWithChangesRequestedTest() {
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/reviews"))
            .willReturn(ok().withBodyFile("reviews/changesRequested.json")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeNotMergeableTest() {
        GHPullRequest pr = loadPullRequest(NOT_MERGEABLE_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldAssignToReviewersTest() {
        GHPullRequest pr = loadPullRequest(PULL_REQUEST_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        assertThat(new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("assignees")).containsExactlyInAnyOrder("approved");
    }

    @Test
    public void shouldCheckConflictsWhenThereArePrOpenedTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls?state=open"))
            .willReturn(ok().withBodyFile("merge/conflict/twoMergeable.json")));
        GHPullRequest pr = loadPullRequest(PULL_REQUEST_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isTrue();
        List<Invocation> conflictInvocations = busInvocations.stream()
            .filter(i -> Constants.PR_CHECK_CONFLICT.equals(i.getDestination())).collect(Collectors.toList());
        assertThat(conflictInvocations).hasSize(1);
    }

    @Test
    public void shouldNotCheckConflictsWhenThereAreNoPrOpenedTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls?state=open"))
            .willReturn(ok().withBody("[]")));
        GHPullRequest pr = loadPullRequest(PULL_REQUEST_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isTrue();
        assertThat(busInvocations.stream().filter(i -> Constants.PR_CHECK_CONFLICT.equals(i.getDestination())).findAny()).isNotPresent();
    }

    @Test
    public void shouldIgnoreNonMergeablePrForConflictCheckTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls?state=open"))
            .willReturn(ok().withBodyFile("merge/conflict/oneNotMergeable.json")));
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/20"))
            .willReturn(ok().withBodyFile("merge/conflict/20_ok.json")));
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/21"))
            .willReturn(ok().withBodyFile("merge/conflict/21_ok.json")));
        stubFor(WireMock.post(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+/comments"))
            .willReturn(aResponse().withStatus(201).withBody("{}")));

        GHPullRequest pr = loadPullRequest(PULL_REQUEST_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isTrue();
        List<Invocation> conflictInvocations = busInvocations.stream()
            .filter(i -> Constants.PR_CHECK_CONFLICT.equals(i.getDestination())).collect(Collectors.toList());
        assertThat(conflictInvocations).hasSize(1);
        assertThat(conflictInvocations.get(0).getMessage().get(ConflictMessage.class).getOpenPullRequests()).hasSize(1);
    }

    @Test
    public void shouldNotAutomergeDependabotPrWhenDisabledTest() {
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/reviews"))
            .willReturn(ok().withBody("[]")));

        GHPullRequest pr = loadPullRequest(DEPENDABOT_PR_ID);
        mergeHandler.merge(new BusMessage(client, pr));

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldAutomergeDependabotPrWhenEnabledTest() {
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/reviews"))
            .willReturn(ok().withBody("[]")));

        client.getRepositoryConfiguration().setAutomergeDependabot(true);
        try {
            GHPullRequest pr = loadPullRequest(DEPENDABOT_PR_ID);
            mergeHandler.merge(new BusMessage(client, pr));

            assertThat(wasMerged(pr)).isTrue();
        } finally {
            client.getRepositoryConfiguration().setAutomergeDependabot(false);
        }
    }

    @Test
    public void shouldntAutomergeUsersPRWhenDependabotIsEnabledTest() {
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/reviews"))
            .willReturn(ok().withBody("[]")));

        client.getRepositoryConfiguration().setAutomergeDependabot(true);
        try {
            GHPullRequest pr = loadPullRequest(OK_PR_ID);
            mergeHandler.merge(new BusMessage(client, pr));

            assertThat(wasMerged(pr)).isFalse();
        } finally {
            client.getRepositoryConfiguration().setAutomergeDependabot(false);
        }

    }

    private boolean wasMerged(GHPullRequest pr) {
        return !getRequests(WireMock.putRequestedFor(urlMatching("/repos/" + TEST_REPO + "/pulls/" + pr.getNumber() + "/merge"))).isEmpty();
    }
}
