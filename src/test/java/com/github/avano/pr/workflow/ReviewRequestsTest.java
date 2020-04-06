package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.json.JSONObject;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.handler.ReviewRequest;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ReviewRequestsTest extends TestParent {
    private static final String DEFAULT_REVIEWER = "reviewer";
    private static final String SECOND_REVIEWER = "troublemaker";

    @Inject
    ReviewRequest reviewRequest;

    @Inject
    Configuration config;

    @Test
    public void shouldAddReviewRequestedLabelTest() {
        EventMessage e = getEvent(getPullRequestWithReviewers(DEFAULT_REVIEWER), DEFAULT_REVIEWER);

        reviewRequest.handleReviewRequested(e);

        waitForInvocationsAndAssert(Bus.EDIT_LABELS, 1);

        LabelsMessage labels = (LabelsMessage) getInvocations(Bus.EDIT_LABELS).get(0).getMessage();
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(config.getReviewRequestedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).isNull();
    }

    @Test
    public void shouldAddAsAssigneeTest() {
        reviewRequest.handleReviewRequested(getEvent(getPullRequestWithReviewers(DEFAULT_REVIEWER), DEFAULT_REVIEWER));

        waitForInvocationsAndAssert(2);

        assertThat(new JSONObject(getRequests(PR_PATCH).get(0).getBodyAsString()).getJSONArray("assignees")).containsExactly(DEFAULT_REVIEWER);
    }

    @Test
    public void shouldRemoveAssigneeTest() {
        reviewRequest.handleReviewRequestRemoved(getEvent(getPullRequestWithReviewers(), DEFAULT_REVIEWER));

        waitForInvocationsAndAssert(2);

        assertThat(new JSONObject(getRequests(PR_PATCH).get(0).getBodyAsString()).getJSONArray("assignees")).hasSize(0);
    }

    @Test
    public void shouldRemoveReviewRequestedLabelTest() {
        EventMessage e = getEvent(getPullRequestWithReviewers(), DEFAULT_REVIEWER);

        reviewRequest.handleReviewRequestRemoved(e);

        waitForInvocationsAndAssert(Bus.EDIT_LABELS, 1);

        LabelsMessage labels = (LabelsMessage) getInvocations(Bus.EDIT_LABELS).get(0).getMessage();
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).isNull();
        assertThat(labels.getRemoveLabels()).containsExactly(config.getReviewRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldAddAnotherAssigneeTest() {
        GHPullRequest pr = getPullRequestWithReviewers(DEFAULT_REVIEWER, SECOND_REVIEWER);
        setField(pr, "assignees", getUsers(DEFAULT_REVIEWER));

        reviewRequest.handleReviewRequested(getEvent(pr, SECOND_REVIEWER));

        assertThat(getRequests(PR_PATCH)).hasSize(1);
        assertThat(new JSONObject(getRequests(PR_PATCH).get(0).getBodyAsString()).getJSONArray("assignees"))
            .containsExactlyInAnyOrder(DEFAULT_REVIEWER, SECOND_REVIEWER);
    }

    @Test
    public void shouldRemoveOneReviewerTest() {
        GHPullRequest pr = getPullRequestWithReviewers(DEFAULT_REVIEWER);
        setField(pr, "assignees", getUsers(DEFAULT_REVIEWER, SECOND_REVIEWER));

        reviewRequest.handleReviewRequestRemoved(getEvent(pr, SECOND_REVIEWER));

        assertThat(getRequests(PR_PATCH)).hasSize(1);
        assertThat(new JSONObject(getRequests(PR_PATCH).get(0).getBodyAsString()).getJSONArray("assignees"))
            .containsExactlyInAnyOrder(DEFAULT_REVIEWER);
    }

    @Test
    public void shouldRemoveLabelWhenAllReviewersWereUnassignedTest() {
        EventMessage e = getEvent(getPullRequestWithReviewers(), DEFAULT_REVIEWER);

        reviewRequest.handleReviewRequestRemoved(e);

        waitForInvocationsAndAssert(Bus.EDIT_LABELS, 1);

        LabelsMessage labels = (LabelsMessage) getInvocations(Bus.EDIT_LABELS).get(0).getMessage();
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).isNull();
        assertThat(labels.getRemoveLabels()).containsExactly(config.getReviewRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldCreateCheckRunWhenReviewWasRequestedTest() {
        GHPullRequest pr = getPullRequestWithReviewers(DEFAULT_REVIEWER);
        EventMessage e = getEvent(pr, DEFAULT_REVIEWER);

        reviewRequest.handleReviewRequested(e);

        waitForInvocationsAndAssert(Bus.CHECK_RUN_CREATE, 1);

        CheckRunMessage msg = (CheckRunMessage) getInvocations(Bus.CHECK_RUN_CREATE).get(0).getMessage();
        assertThat(msg.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(msg.getConclusion()).isNull();
        assertThat(msg.getStatus()).isEqualTo(GHCheckRun.Status.IN_PROGRESS);
    }

    @Test
    public void shouldCreateQueuedCheckRunWhenReviewWasRemovedAndThereAreNoAssigneesLeftTest() {
        GHPullRequest pr = getPullRequestWithReviewers();
        EventMessage e = getEvent(pr, DEFAULT_REVIEWER);

        reviewRequest.handleReviewRequestRemoved(e);

        waitForInvocationsAndAssert(Bus.CHECK_RUN_CREATE, 1);

        CheckRunMessage msg = (CheckRunMessage) getInvocations(Bus.CHECK_RUN_CREATE).get(0).getMessage();
        assertThat(msg.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(msg.getConclusion()).isNull();
        assertThat(msg.getStatus()).isEqualTo(GHCheckRun.Status.QUEUED);
    }

    @Test
    public void shouldntChangeCheckRunWhenReviewWasRemovedAndThereAreAssigneesLeftTest() {
        GHPullRequest pr = getPullRequestWithReviewers(DEFAULT_REVIEWER);
        setField(pr, "assignees", getUsers(DEFAULT_REVIEWER, SECOND_REVIEWER));

        reviewRequest.handleReviewRequestRemoved(getEvent(pr, SECOND_REVIEWER));

        waitForInvocations(Bus.CHECK_RUN_CREATE, 1);
        assertThat(getInvocations(Bus.CHECK_RUN_CREATE)).isEmpty();
    }

    private EventMessage getEvent(GHPullRequest pr, String requestedReviewer) {
        return new EventMessage(pr)
            .withSender(getInstance(GHUser.class, fields("login", "reviewer")))
            .withAdditionalInfo(EventMessage.REQUESTED_REVIEWER, requestedReviewer);
    }
}
