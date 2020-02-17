package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.handler.Checks;
import com.github.avano.pr.workflow.message.CommitStatusMessage;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.mock.TrackerMock;
import com.github.avano.pr.workflow.util.CheckState;
import com.github.avano.pr.workflow.util.Invocation;
import com.github.tomakehurst.wiremock.client.WireMock;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ChecksTest extends TestParent {
    private static final String SHA = "6dcb09b5b57875f334f61aebed695e2e4193db5e";

    private static final String SUCCESS_CHECK_NAME = "checkrun-success";
    private static final String IN_PROGRESS_CHECK_NAME = "checkrun-in-progress";
    private static final String FAILED_CHECK_NAME = "checkrun-failed";

    @Inject
    TrackerMock tracker;

    @Inject
    Checks checks;

    private GHPullRequest pr1 = getInstance(GHPullRequest.class, fields("number", 1,
        "owner", getInstance(GHRepository.class, fields("full_name", TEST_REPO))));
    private GHPullRequest pr3 = getInstance(GHPullRequest.class, fields("number", 3,
        "owner", getInstance(GHRepository.class, fields("full_name", TEST_REPO))));

    @Override
    @BeforeEach
    public void setup() {
        super.setup();

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls?state=open"))
            .willReturn(ok().withBodyFile("checks/pullRequests.json")));
    }

    @Override
    @AfterEach
    public void reset() {
        super.reset();
        tracker.cleanUp();
    }

    @Test
    public void shouldTrackCommitStatusTest() {
        GHCommit commit = getInstance(GHCommit.class, fields("sha", SHA));
        CommitStatusMessage status = new CommitStatusMessage(commit, GHCommitState.FAILURE, SUCCESS_CHECK_NAME);

        checks.handleStatusChanged(status);

        assertThat(tracker.getChecks(pr1)).hasSize(1).contains(entry(SUCCESS_CHECK_NAME, CheckState.FAILED));
        assertThat(tracker.getChecks(pr3)).hasSize(1).contains(entry(SUCCESS_CHECK_NAME, CheckState.FAILED));

        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }

    @Test
    public void shouldTryToMergeCommitWithOkStatusTest() {
        GHCommit commit = getInstance(GHCommit.class, fields("sha", SHA));
        CommitStatusMessage status = new CommitStatusMessage(commit, GHCommitState.SUCCESS, SUCCESS_CHECK_NAME);

        checks.handleStatusChanged(status);

        waitForInvocationsAndAssert(2);
        for (Invocation busInvocation : busInvocations) {
            assertThat(busInvocation.getDestination()).isEqualTo(Bus.PR_MERGE);
        }
    }

    @Test
    public void shouldIgnoreNonHeadCommitStatusTest() {
        GHCommit commit = getInstance(GHCommit.class, fields("sha", "nonhead"));
        CommitStatusMessage status = new CommitStatusMessage(commit, GHCommitState.FAILURE, SUCCESS_CHECK_NAME);

        checks.handleStatusChanged(status);

        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
        assertThat(tracker.getTrackers()).isEmpty();
    }

    @Test
    public void shouldTrackFinishedCommitCheckRunTest() {
        GHCheckRun checkRun = getInstance(GHCheckRun.class, fields("headSha", SHA, "conclusion", "failure", "name", FAILED_CHECK_NAME));

        checks.handleCheckRunFinished(new EventMessage(checkRun));

        assertThat(tracker.getChecks(pr1)).hasSize(1).contains(entry(FAILED_CHECK_NAME, CheckState.FAILED));
        assertThat(tracker.getChecks(pr3)).hasSize(1).contains(entry(FAILED_CHECK_NAME, CheckState.FAILED));
        waitFor(() -> !busInvocations.isEmpty(), 2);
        assertThat(busInvocations).isEmpty();
    }

    @Test
    public void shouldTryToMergeCommitWithOkCheckRunTest() {
        GHCheckRun checkRun = getInstance(GHCheckRun.class, fields("headSha", SHA, "conclusion", "success", "name", SUCCESS_CHECK_NAME));

        checks.handleCheckRunFinished(new EventMessage(checkRun));

        waitForInvocationsAndAssert(2);
        for (Invocation busInvocation : busInvocations) {
            assertThat(busInvocation.getDestination()).isEqualTo(Bus.PR_MERGE);
        }
    }

    @Test
    public void shouldIgnoreFinishedNonHeadCommitCheckRunTest() {
        GHCheckRun checkRun = getInstance(GHCheckRun.class, fields("headSha", "nonhead", "conclusion", "failure", "name", FAILED_CHECK_NAME));

        checks.handleCheckRunFinished(new EventMessage(checkRun));

        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
        assertThat(tracker.getTrackers()).isEmpty();
    }

    @Test
    public void shouldTrackCreatedCommitCheckRunTest() {
        GHCheckRun checkRun = getInstance(GHCheckRun.class, fields("headSha", SHA, "name", IN_PROGRESS_CHECK_NAME));

        checks.handleCheckRunCreated(new EventMessage(checkRun));

        assertThat(tracker.getChecks(pr1)).hasSize(1).contains(entry(IN_PROGRESS_CHECK_NAME, CheckState.IN_PROGRESS));
        assertThat(tracker.getChecks(pr3)).hasSize(1).contains(entry(IN_PROGRESS_CHECK_NAME, CheckState.IN_PROGRESS));
        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }

    @Test
    public void shouldIgnoreCreatedNonHeadCommitCheckRunTest() {
        GHCheckRun checkRun = getInstance(GHCheckRun.class, fields("headSha", "nonhead", "name", IN_PROGRESS_CHECK_NAME));

        checks.handleCheckRunCreated(new EventMessage(checkRun));

        assertThat(tracker.getTrackers()).isEmpty();
        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }
}
