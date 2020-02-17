package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.handler.Merge;
import com.github.avano.pr.workflow.mock.TrackerMock;
import com.github.avano.pr.workflow.util.CheckState;
import com.github.tomakehurst.wiremock.client.WireMock;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MergeTest extends TestParent {
    private static final String PASSING_CHECK_NAME = "travis-passing";
    private static final String IN_PROGRESS_CHECK_NAME = "travis-in-progress";
    private static final String FAILING_CHECK_NAME = "travis-failing";

    private static final int OK_PR_ID = PULL_REQUEST_ID;
    private static final int MERGED_PR_ID = 1;
    private static final int DRAFT_PR_ID = 2;
    private static final int WIP_PR_ID = 3;
    private static final int NOT_MERGEABLE_PR_ID = 4;


    @Inject
    Merge merge;

    @Inject
    TrackerMock tracker;

    @AfterEach
    public void reset() {
        super.reset();
        tracker.cleanUp();
    }

    @Test
    public void shouldMergeTest() {
        GHPullRequest pr = loadPullRequest(PULL_REQUEST_ID);
        tracker.setCheckState(pr, PASSING_CHECK_NAME, CheckState.SUCCESS);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isTrue();
    }

    @Test
    public void shouldNotMergeMergedTest()  {
        GHPullRequest pr = loadPullRequest(MERGED_PR_ID);
        tracker.setCheckState(pr, PASSING_CHECK_NAME, CheckState.SUCCESS);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeDraftTest() {
        GHPullRequest pr = loadPullRequest(DRAFT_PR_ID);
        tracker.setCheckState(pr, PASSING_CHECK_NAME, CheckState.SUCCESS);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWipTest() {
        GHPullRequest pr = loadPullRequest(WIP_PR_ID);
        tracker.setCheckState(pr, PASSING_CHECK_NAME, CheckState.SUCCESS);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWithFailedCheckTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(ok().withBodyFile("merge/checks/failingCheckOnly.json")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        tracker.setCheckState(pr, FAILING_CHECK_NAME, CheckState.FAILED);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWithPendingCheckTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/branches/master/protection"))
            .willReturn(ok().withBodyFile("merge/checks/inProgressCheckOnly.json")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        tracker.setCheckState(pr, IN_PROGRESS_CHECK_NAME, CheckState.IN_PROGRESS);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWithoutReviewsTest() {
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/reviews"))
            .willReturn(ok().withBody("[]")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        tracker.setCheckState(pr, PASSING_CHECK_NAME, CheckState.SUCCESS);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeWithChangesRequestedTest() {
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/\\d+/reviews"))
            .willReturn(ok().withBodyFile("reviews/changesRequested.json")));

        GHPullRequest pr = loadPullRequest(OK_PR_ID);
        tracker.setCheckState(pr, PASSING_CHECK_NAME, CheckState.SUCCESS);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isFalse();
    }

    @Test
    public void shouldNotMergeNotMergeableTest() {
        GHPullRequest pr = loadPullRequest(NOT_MERGEABLE_PR_ID);
        tracker.setCheckState(pr, PASSING_CHECK_NAME, CheckState.SUCCESS);
        merge.merge(pr);

        assertThat(wasMerged(pr)).isFalse();
    }

    private boolean wasMerged(GHPullRequest pr) {
        return !getRequests(WireMock.putRequestedFor(urlMatching("/repos/" + TEST_REPO + "/pulls/" + pr.getNumber() + "/merge"))).isEmpty();
    }
}
