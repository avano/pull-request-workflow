package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

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
import com.github.tomakehurst.wiremock.client.WireMock;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ChecksTest extends TestParent {
    private static final String SHA = "6dcb09b5b57875f334f61aebed695e2e4193db5e";

    private static final String SUCCESS_CHECK_NAME = "checkrun-success";

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

    @Test
    public void shouldTryToMergeWhenCheckRunIsCompletedTest() {
        GHCheckRun checkRun = getInstance(GHCheckRun.class, fields("headSha", SHA, "conclusion", "success", "name", SUCCESS_CHECK_NAME));

        checks.handleCheckRunFinished(new EventMessage(checkRun));
        waitForInvocationsAndAssert(2);
        assertThat(busInvocations.get(0).getDestination()).isEqualTo(Bus.PR_MERGE);
        assertThat(((GHPullRequest) busInvocations.get(0).getMessage()).getNumber()).isEqualTo(pr1.getNumber());
        assertThat(busInvocations.get(1).getDestination()).isEqualTo(Bus.PR_MERGE);
        assertThat(((GHPullRequest) busInvocations.get(1).getMessage()).getNumber()).isEqualTo(pr3.getNumber());
    }

    @Test
    public void shouldTryToMergeWhenStatusWasChangedTest() {
        GHCommit commit = getInstance(GHCommit.class, fields("sha", SHA));
        CommitStatusMessage status = new CommitStatusMessage(commit, GHCommitState.SUCCESS, SUCCESS_CHECK_NAME);

        checks.handleStatusChanged(status);
        waitForInvocationsAndAssert(2);
        assertThat(busInvocations.get(0).getDestination()).isEqualTo(Bus.PR_MERGE);
        assertThat(((GHPullRequest) busInvocations.get(0).getMessage()).getNumber()).isEqualTo(pr1.getNumber());
        assertThat(busInvocations.get(1).getDestination()).isEqualTo(Bus.PR_MERGE);
        assertThat(((GHPullRequest) busInvocations.get(1).getMessage()).getNumber()).isEqualTo(pr3.getNumber());
    }

    @Test
    public void shouldNotTryToMergeWhenStatusFailedTest() {
        GHCommit commit = getInstance(GHCommit.class, fields("sha", SHA));
        CommitStatusMessage status = new CommitStatusMessage(commit, GHCommitState.FAILURE, SUCCESS_CHECK_NAME);

        checks.handleStatusChanged(status);
        waitForInvocations(1);
        assertThat(busInvocations).hasSize(0);
    }

    @Test
    public void shouldNotTryToMergeWhenCheckRunFailedTest() {
        GHCheckRun checkRun = getInstance(GHCheckRun.class, fields("headSha", SHA, "conclusion", "failure", "name", SUCCESS_CHECK_NAME));

        checks.handleCheckRunFinished(new EventMessage(checkRun));
        waitForInvocations(1);
        assertThat(busInvocations).hasSize(0);
    }

    @Test
    public void shouldIgnoreNonHeadCommitStatusTest() {
        GHCommit commit = getInstance(GHCommit.class, fields("sha", "nonhead"));
        CommitStatusMessage status = new CommitStatusMessage(commit, GHCommitState.SUCCESS, SUCCESS_CHECK_NAME);

        checks.handleStatusChanged(status);

        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }

    @Test
    public void shouldIgnoreNonHeadCommitCheckRunTest() {
        GHCheckRun checkRun = getInstance(GHCheckRun.class, fields("headSha", "nonhead", "conclusion", "success", "name", SUCCESS_CHECK_NAME));

        checks.handleCheckRunFinished(new EventMessage(checkRun));

        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }
}
