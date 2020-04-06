package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHUser;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.handler.ReviewSubmitted;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import javax.inject.Inject;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ReviewSubmittedTest extends TestParent {
    private static final int CHANGES_REQUESTED_PR_ID = 1000;

    @Inject
    ReviewSubmitted reviewSubmitted;

    @Inject
    Configuration config;

    private GHPullRequest pr;

    @Override
    @BeforeEach
    public void setup() {
        super.setup();

        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls/" + CHANGES_REQUESTED_PR_ID + "/reviews"))
            .willReturn(ok().withBodyFile("reviews/changesRequested.json")));

        stubFor(WireMock.post(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+/assignees"))
            .willReturn(created().withBody("[]")));

        pr = loadPullRequest(PULL_REQUEST_ID);
        setField(pr, "user", getUsers("creator")[0]);
        setField(pr, "assignees", new GHUser[] {});
    }

    @Test
    public void shouldAddApprovedLabelWhenNoChangesAreRequestedTest() {
        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        waitForInvocationsAndAssert(Bus.EDIT_LABELS, 1);

        LabelsMessage labels = (LabelsMessage) getInvocations(Bus.EDIT_LABELS).get(0).getMessage();

        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(config.getApprovedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).contains(config.getReviewRequestedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).contains(config.getChangesRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldntAddApprovedLabelWhenChangesWereRequestedTest() {
        setField(pr, "number", CHANGES_REQUESTED_PR_ID);
        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        waitForInvocationsAndAssert(Bus.EDIT_LABELS, 1);
        LabelsMessage labels = ((LabelsMessage) getInvocations(Bus.EDIT_LABELS).get(0).getMessage());

        assertThat(labels.getPr().getNumber()).isEqualTo(CHANGES_REQUESTED_PR_ID);
        assertThat(labels.getAddLabels()).isEmpty();
        assertThat(labels.getRemoveLabels()).contains(config.getReviewRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldAddChangesRequestedLabelTest() {
        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.CHANGES_REQUESTED));

        waitForInvocationsAndAssert(Bus.EDIT_LABELS, 1);
        LabelsMessage labels = (LabelsMessage) getInvocations(Bus.EDIT_LABELS).get(0).getMessage();
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(config.getChangesRequestedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).contains(config.getReviewRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldDismissApprovedLabelWhenChangesWereRequestedTest() {
        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.CHANGES_REQUESTED));

        waitForInvocationsAndAssert(Bus.EDIT_LABELS, 1);
        LabelsMessage labels = (LabelsMessage) getInvocations(Bus.EDIT_LABELS).get(0).getMessage();
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(config.getChangesRequestedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).contains(config.getApprovedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldAddCommentedLabelTest() {
        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.COMMENTED));

        waitForInvocationsAndAssert(Bus.EDIT_LABELS, 1);
        LabelsMessage labels = (LabelsMessage) getInvocations(Bus.EDIT_LABELS).get(0).getMessage();
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(config.getCommentedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).isEmpty();
    }

    @Test
    public void shouldRemoveFromAssigneesWhenPRWasApprovedTest() {
        setField(pr, "assignees", getUsers("troublemaker", "reviewer"));

        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        assertThat(new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("assignees")).containsExactlyInAnyOrder("troublemaker");
    }

    @Test
    public void shouldntChangeAssigneesWhenCommentedTest() {
        setField(pr, "assignees", getUsers("reviewer"));

        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.COMMENTED));

        assertThat(getRequests(PR_PATCH)).isEmpty();
    }

    @Test
    public void shouldAssignToAuthorWhenChangesWereRequestedTest() {
        // The default PR has 1 reviewer requested, so set him as assignee
        setField(pr, "assignees", getUsers("approved"));

        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.CHANGES_REQUESTED));

        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        JSONArray assignees = new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("assignees");
        assertThat(assignees).containsExactly("creator");
    }

    @Test
    public void shouldCreateSuccessCheckRunWhenApprovedTest() {
        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        waitForInvocationsAndAssert(Bus.CHECK_RUN_CREATE, 1);

        CheckRunMessage msg = ((CheckRunMessage) getInvocations(Bus.CHECK_RUN_CREATE).get(0).getMessage());
        assertThat(msg.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(msg.getConclusion()).isEqualTo(GHCheckRun.Conclusion.SUCCESS);
        assertThat(msg.getStatus()).isEqualTo(GHCheckRun.Status.COMPLETED);
    }

    @Test
    public void shouldntCreateSuccessCheckRunWhenApprovedButChangesAreStillRequestedTest() {
        setField(pr, "number", CHANGES_REQUESTED_PR_ID);
        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        waitForInvocations(Bus.CHECK_RUN_CREATE, 1);
        assertThat(getInvocations(Bus.CHECK_RUN_CREATE)).isEmpty();
    }

    @Test
    public void shouldCreateFailedCheckRunWhenChangesWereRequestedTest() {
        reviewSubmitted.handleReview(getEvent(pr, GHPullRequestReviewState.CHANGES_REQUESTED));
        waitForInvocationsAndAssert(Bus.CHECK_RUN_CREATE, 1);

        CheckRunMessage msg = ((CheckRunMessage) getInvocations(Bus.CHECK_RUN_CREATE).get(0).getMessage());
        assertThat(msg.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(msg.getConclusion()).isEqualTo(GHCheckRun.Conclusion.FAILURE);
        assertThat(msg.getStatus()).isEqualTo(GHCheckRun.Status.COMPLETED);
    }

    private EventMessage getEvent(GHPullRequest pr, GHPullRequestReviewState state) {
        GHPullRequestReview review = getInstance(GHPullRequestReview.class, fields("state", state));
        return new EventMessage(review).withAdditionalInfo(EventMessage.INFO_PR_KEY, pr)
            .withSender(getInstance(GHUser.class, fields("login", "reviewer")));
    }
}
