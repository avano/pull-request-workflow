package com.github.avano.pr.workflow.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.json.PullRequest;
import com.github.avano.pr.workflow.message.BusMessage;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PullRequestTest extends JsonHandlerTest {
    @Inject
    PullRequest pullRequest;

    @Test
    public void shouldSendPrReopenedMessageTest() {
        pullRequest.handlePullRequestEvent(jsonBody("prReopened.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.PR_REOPENED);
        BusMessage msg = lastMessage();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        assertThat(pr.getNumber()).isEqualTo(21);
        assertThat(msg.getSender().getLogin()).isEqualTo("Codertocat-reopened");
    }

    @Test
    public void shouldSendPrReviewRequestedMessageTest() {
        pullRequest.handlePullRequestEvent(jsonBody("prReviewRequested.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.PR_REVIEW_REQUESTED);
        BusMessage msg = lastMessage();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        assertThat(pr.getNumber()).isEqualTo(50);
        assertThat(msg.getSender().getLogin()).isEqualTo("Codertocat-requester");
        String requestedReviewer = msg.get(BusMessage.REQUESTED_REVIEWER, String.class);
        assertThat(requestedReviewer).isEqualTo("octocat-assignee");
    }

    @Test
    public void shouldSendPrReviewRequestRemovedMessageTest() {
        pullRequest.handlePullRequestEvent(jsonBody("prReviewRequestRemoved.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.PR_REVIEW_REQUEST_REMOVED);
        BusMessage msg = lastMessage();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        assertThat(pr.getNumber()).isEqualTo(51);
        assertThat(msg.getSender().getLogin()).isEqualTo("Codertocat-request-removed");
        String requestedReviewer = msg.get(BusMessage.REQUESTED_REVIEWER, String.class);
        assertThat(requestedReviewer).isEqualTo("octocat-removed");
    }

    @Test
    public void shouldSendPrReadyForReviewMessageTest() {
        pullRequest.handlePullRequestEvent(jsonBody("prReadyForReview.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.PR_READY_FOR_REVIEW);
        BusMessage msg = lastMessage();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        assertThat(pr.getNumber()).isEqualTo(6);
        assertThat(msg.getSender().getLogin()).isEqualTo("Codertocat-ready");
    }

    @Test
    public void shouldSendPrSynchronizedMessageTest() {
        pullRequest.handlePullRequestEvent(jsonBody("prSynchronized.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.PR_UPDATED);
        BusMessage msg = lastMessage();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        assertThat(pr.getNumber()).isEqualTo(12);
        assertThat(msg.getSender().getLogin()).isEqualTo("Codertocat-synchronize");
    }

    @Test
    public void shouldSendPrUnlabeledMessageTest() {
        pullRequest.handlePullRequestEvent(jsonBody("prUnlabeled.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.PR_UNLABELED);
        BusMessage msg = lastMessage();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        assertThat(pr.getNumber()).isEqualTo(13);
        assertThat(msg.get(BusMessage.LABEL, String.class)).isEqualTo("testLabel");
    }

    @Test
    public void shouldIgnoreClosedMessageTest() {
        pullRequest.handlePullRequestEvent(jsonBody("prClosed.json"));
        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }
}
