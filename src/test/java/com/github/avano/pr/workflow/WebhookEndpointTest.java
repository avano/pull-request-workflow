package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.apache.commons.io.FileUtils;

import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.message.CommitStatusMessage;
import com.github.avano.pr.workflow.message.EventMessage;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class WebhookEndpointTest extends TestParent {
    @TestHTTPResource("/webhook")
    private URL url;

    private void sendRequest(String header, String fileName) {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("X-GitHub-Event", header);
            String json = FileUtils.readFileToString(new File("src/test/resources/__files/endpoint/" + fileName), "UTF-8");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Length", Integer.toString(json.length()));
            con.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            assertThat(con.getResponseCode()).isEqualTo(204);
        } catch (IOException e) {
            fail("Unable to send HTTP request", e);
        }
    }

    @Test
    public void shouldSendCheckRunCreatedMessageTest() {
        sendRequest("check_run", "checkRunCreated.json");
        assertThat(lastDestination()).isEqualTo(Bus.CHECK_RUN_CREATED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHCheckRun checkRun = ((EventMessage) msg).get();
        assertThat(checkRun.getName()).isEqualTo("Octocoders-linter-created");
    }

    @Test
    public void shouldSendCheckRunCompletedMessageTest() {
        sendRequest("check_run", "checkRunCompleted.json");
        assertThat(lastDestination()).isEqualTo(Bus.CHECK_RUN_FINISHED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHCheckRun checkRun = ((EventMessage) msg).get();
        assertThat(checkRun.getName()).isEqualTo("Octocoders-linter-completed");
    }

    @Test
    public void shouldSendCheckRunRerequestedMessageTest() {
        sendRequest("check_run", "checkRunRerequested.json");
        assertThat(lastDestination()).isEqualTo(Bus.CHECK_RUN_CREATED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHCheckRun checkRun = ((EventMessage) msg).get();
        assertThat(checkRun.getName()).isEqualTo("Octocoders-linter-rerequested");
    }

    @Test
    public void shouldSendStatusMessageTest() {
        sendRequest("status", "statusEvent.json");
        assertThat(lastDestination()).isEqualTo(Bus.STATUS_CHANGED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(CommitStatusMessage.class);
        CommitStatusMessage status = (CommitStatusMessage) msg;
        assertThat(status.getName()).isEqualTo("status-check");
        assertThat(status.getStatus()).isEqualTo(GHCommitState.SUCCESS);
        assertThat(status.getCommit().getSHA1()).isEqualTo("6113728f27ae82c7b1a177c8d03f9e96e0adf246");
    }

    @Test
    public void shouldSendReviewSubmittedMessageTest() {
        sendRequest("pull_request_review", "reviewSubmitted.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_REVIEW_SUBMITTED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        EventMessage event = (EventMessage) msg;
        GHPullRequestReview review = ((EventMessage) msg).get();
        assertThat(review.getState()).isEqualTo(GHPullRequestReviewState.COMMENTED);
        assertThat(event.getSender().getLogin()).isEqualTo("Codertocat-submitted");
        GHPullRequest pr = ((EventMessage) msg).getInfo(EventMessage.INFO_PR_KEY);
        assertThat(pr.getNumber()).isEqualTo(2);
    }

    @Test
    public void shouldNotSendAnyMessageForReviewEditedTest() {
        sendRequest("pull_request_review", "reviewEdited.json");
        assertThat(lastDestination()).isNull();
    }

    @Test
    public void shouldSendMessageForReviewDismissedTest() {
        sendRequest("pull_request_review", "reviewDismissed.json");
        assertThat(lastDestination()).isNull();
    }

    @Test
    public void shouldSendPrOpenedMessageTest() {
        sendRequest("pull_request", "prOpened.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_OPENED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHPullRequest pr = ((EventMessage) msg).get();
        assertThat(pr.getNumber()).isEqualTo(27);
        assertThat(((EventMessage) msg).getSender().getLogin()).isEqualTo("Codertocat-opened");
    }

    @Test
    public void shouldSendPrClosedMessageTest() {
        sendRequest("pull_request", "prClosed.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_CLOSED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHPullRequest pr = ((EventMessage) msg).get();
        assertThat(pr.getNumber()).isEqualTo(17);
        assertThat(((EventMessage) msg).getSender().getLogin()).isEqualTo("Codertocat-closed");
    }

    @Test
    public void shouldSendPrReopenedMessageTest() {
        sendRequest("pull_request", "prReopened.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_REOPENED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHPullRequest pr = ((EventMessage) msg).get();
        assertThat(pr.getNumber()).isEqualTo(21);
        assertThat(((EventMessage) msg).getSender().getLogin()).isEqualTo("Codertocat-reopened");
    }

    @Test
    public void shouldSendPrReviewRequestedMessageTest() {
        sendRequest("pull_request", "prReviewRequested.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_REVIEW_REQUESTED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        EventMessage e = (EventMessage) msg;
        GHPullRequest pr = e.get();
        assertThat(pr.getNumber()).isEqualTo(50);
        assertThat(e.getSender().getLogin()).isEqualTo("Codertocat-requester");
        String requestedReviewer = e.getInfo(EventMessage.REQUESTED_REVIEWER);
        assertThat(requestedReviewer).isEqualTo("octocat-assignee");
    }

    @Test
    public void shouldSendPrReviewRequestRemovedMessageTest() {
        sendRequest("pull_request", "prReviewRequestRemoved.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_REVIEW_REQUEST_REMOVED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        EventMessage e = (EventMessage) msg;
        GHPullRequest pr = e.get();
        assertThat(pr.getNumber()).isEqualTo(51);
        assertThat(e.getSender().getLogin()).isEqualTo("Codertocat-request-removed");
        String requestedReviewer = e.getInfo(EventMessage.REQUESTED_REVIEWER);
        assertThat(requestedReviewer).isEqualTo("octocat-removed");
    }

    @Test
    public void shouldSendPrReadyForReviewMessageTest() {
        sendRequest("pull_request", "prReadyForReview.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_READY_FOR_REVIEW);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHPullRequest pr = ((EventMessage) msg).get();
        assertThat(pr.getNumber()).isEqualTo(6);
        assertThat(((EventMessage) msg).getSender().getLogin()).isEqualTo("Codertocat-ready");
    }

    @Test
    public void shouldSendPrSynchronizedMessageTest() {
        sendRequest("pull_request", "prSynchronized.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_UPDATED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHPullRequest pr = ((EventMessage) msg).get();
        assertThat(pr.getNumber()).isEqualTo(12);
        assertThat(((EventMessage) msg).getSender().getLogin()).isEqualTo("Codertocat-synchronize");
    }

    @Test
    public void shouldSendPrUnlabeledMessageTest() {
        sendRequest("pull_request", "prUnlabeled.json");
        assertThat(lastDestination()).isEqualTo(Bus.PR_UNLABELED);
        Object msg = lastMessage();
        assertThat(msg).isInstanceOf(EventMessage.class);
        GHPullRequest pr = ((EventMessage) msg).get();
        assertThat(pr.getNumber()).isEqualTo(13);
        assertThat(((EventMessage) msg).getInfo(EventMessage.LABEL).toString()).isEqualTo("testLabel");
    }
}
