package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.handler.Lifecycle;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import javax.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class LifecycleTest extends TestParent {
    private static final int PR_NO_REVIEWERS_ID = 1338;
    private static final String PR_REVIEW_DISMISS_PATH_REGEX = "/repos/" + TEST_REPO + "/pulls/" + PULL_REQUEST_ID + "/reviews/\\d+/dismissals";

    @Inject
    Lifecycle lifecycle;

    @Inject
    Configuration config;

    private GHPullRequest pr;

    @Override
    @BeforeEach
    public void setup() {
        super.setup();

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls/" + PULL_REQUEST_ID + "/reviews"))
            .willReturn(ok().withBodyFile("lifecycle/reviews.json")));

        stubFor(WireMock.put(urlPathMatching(PR_REVIEW_DISMISS_PATH_REGEX))
            .willReturn(ok()));

        // Override default mapping
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/" + PR_NO_REVIEWERS_ID + "/reviews"))
            .willReturn(ok().withBody("[]")));
    }

    @BeforeEach
    public void loadDefaultPr() {
        pr = getInstance(GHPullRequest.class, fields("number", PULL_REQUEST_ID,
            "owner", getInstance(GHRepository.class, fields("full_name", TEST_REPO))));
    }

    @Test
    public void shouldTryToMergeReopenedPrTest() {
        lifecycle.handlePrReopened(new EventMessage(pr));
        waitForInvocations(1);
        assertThat(busInvocations.get(0).getDestination()).isEqualTo(Bus.PR_MERGE);
    }

    @Test
    public void shouldDismissReviewsOnUpdateTest() {
        pr = loadPullRequest(PULL_REQUEST_ID);
        lifecycle.handlePrUpdated(new EventMessage(pr));
        List<LoggedRequest> requests = getRequests(WireMock.putRequestedFor(urlPathMatching(PR_REVIEW_DISMISS_PATH_REGEX)));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getBodyAsString()).contains(config.getReviewDismissMessage());
    }

    @Test
    public void shouldAddReviewersAsAssigneesWhenPrWasUpdatedTest() {
        pr = loadPullRequest(PULL_REQUEST_ID);
        lifecycle.handlePrUpdated(new EventMessage(pr));
        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        JSONArray assignees = new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("assignees");
        assertThat(assignees).hasSize(2);
        assertThat(assignees).containsExactlyInAnyOrder("approved", "troublemaker");
    }

    @Test
    public void shouldRemoveAllLabelsOnUpdateWithNoReviewersTest() {
        pr = loadPullRequest(PR_NO_REVIEWERS_ID);
        lifecycle.handlePrUpdated(new EventMessage(pr));
        waitForInvocationsAndAssert(1);
        LabelsMessage labels = (LabelsMessage) busInvocations.get(0).getMessage();
        assertThat(labels.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(labels.getAddLabels()).isEmpty();
        Set<String> removeLabels = new HashSet<>(config.getApprovedLabels());
        removeLabels.addAll(config.getChangesRequestedLabels());
        removeLabels.addAll(config.getCommentedLabels());
        assertThat(labels.getRemoveLabels()).containsExactlyInAnyOrder(removeLabels.toArray(new String[0]));
    }

    @Test
    public void shouldTryToMergeWhenMarkedAsReadyTest() {
        lifecycle.handleReadyForReview(new EventMessage(loadPullRequest(PULL_REQUEST_ID)));
        assertThat(lastDestination()).isEqualTo(Bus.PR_MERGE);
    }

    @Test
    public void shouldntRequestReviewFromAuthorTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls/" + PULL_REQUEST_ID + "/reviews"))
            .willReturn(ok().withBodyFile("lifecycle/reviewsWithAuthor.json")));
        pr = loadPullRequest(PULL_REQUEST_ID);
        lifecycle.handlePrUpdated(new EventMessage(pr));
        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        JSONArray assignees = new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("assignees");
        assertThat(assignees).hasSize(2);
        assertThat(assignees).containsExactlyInAnyOrder("approved", "troublemaker");
    }
}
