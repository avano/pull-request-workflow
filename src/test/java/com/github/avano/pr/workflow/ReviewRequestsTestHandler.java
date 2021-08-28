package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.json.JSONObject;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.ReviewRequestHandler;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ReviewRequestsTestHandler extends TestParent {
    private static final String DEFAULT_REVIEWER = "reviewer";
    private static final String SECOND_REVIEWER = "troublemaker";

    @Inject
    ReviewRequestHandler reviewRequestHandler;

    @Test
    public void shouldAddReviewRequestedLabelTest() {
        BusMessage e = getMessage(getPullRequestWithReviewers(DEFAULT_REVIEWER), DEFAULT_REVIEWER);

        reviewRequestHandler.handleReviewRequested(e);

        waitForInvocationsAndAssert(Constants.EDIT_LABELS, 1);

        LabelsMessage labels = getInvocations(Constants.EDIT_LABELS).get(0).getMessage().get(LabelsMessage.class);
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(client.getRepositoryConfiguration().reviewRequestedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).isNull();
    }

    @Test
    public void shouldAddAsAssigneeTest() {
        reviewRequestHandler.handleReviewRequested(getMessage(getPullRequestWithReviewers(DEFAULT_REVIEWER), DEFAULT_REVIEWER));

        waitForInvocationsAndAssert(2);

        assertThat(new JSONObject(getRequests(PR_PATCH).get(0).getBodyAsString()).getJSONArray("assignees")).containsExactly(DEFAULT_REVIEWER);
    }

    @Test
    public void shouldRemoveAssigneeTest() {
        reviewRequestHandler.handleReviewRequestRemoved(getMessage(getPullRequestWithReviewers(), DEFAULT_REVIEWER));

        waitForInvocationsAndAssert(2);

        assertThat(new JSONObject(getRequests(PR_PATCH).get(0).getBodyAsString()).getJSONArray("assignees")).hasSize(0);
    }

    @Test
    public void shouldRemoveReviewRequestedLabelTest() {
        BusMessage e = getMessage(getPullRequestWithReviewers(), DEFAULT_REVIEWER);

        reviewRequestHandler.handleReviewRequestRemoved(e);

        waitForInvocationsAndAssert(Constants.EDIT_LABELS, 1);

        LabelsMessage labels = getInvocations(Constants.EDIT_LABELS).get(0).getMessage().get(LabelsMessage.class);
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).isNull();
        assertThat(labels.getRemoveLabels()).containsExactly(client.getRepositoryConfiguration().reviewRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldAddAnotherAssigneeTest() {
        GHPullRequest pr = getPullRequestWithReviewers(DEFAULT_REVIEWER, SECOND_REVIEWER);
        setField(pr, "assignees", getUsers(DEFAULT_REVIEWER));

        reviewRequestHandler.handleReviewRequested(getMessage(pr, SECOND_REVIEWER));

        assertThat(getRequests(PR_PATCH)).hasSize(1);
        assertThat(new JSONObject(getRequests(PR_PATCH).get(0).getBodyAsString()).getJSONArray("assignees"))
            .containsExactlyInAnyOrder(DEFAULT_REVIEWER, SECOND_REVIEWER);
    }

    @Test
    public void shouldRemoveOneReviewerTest() {
        GHPullRequest pr = getPullRequestWithReviewers(DEFAULT_REVIEWER);
        setField(pr, "assignees", getUsers(DEFAULT_REVIEWER, SECOND_REVIEWER));

        reviewRequestHandler.handleReviewRequestRemoved(getMessage(pr, SECOND_REVIEWER));

        assertThat(getRequests(PR_PATCH)).hasSize(1);
        assertThat(new JSONObject(getRequests(PR_PATCH).get(0).getBodyAsString()).getJSONArray("assignees"))
            .containsExactlyInAnyOrder(DEFAULT_REVIEWER);
    }

    @Test
    public void shouldRemoveLabelWhenAllReviewersWereUnassignedTest() {
        BusMessage e = getMessage(getPullRequestWithReviewers(), DEFAULT_REVIEWER);

        reviewRequestHandler.handleReviewRequestRemoved(e);

        waitForInvocationsAndAssert(Constants.EDIT_LABELS, 1);

        LabelsMessage labels = getInvocations(Constants.EDIT_LABELS).get(0).getMessage().get(LabelsMessage.class);
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).isNull();
        assertThat(labels.getRemoveLabels()).containsExactly(client.getRepositoryConfiguration().reviewRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldCreateCheckRunWhenReviewWasRequestedTest() {
        GHPullRequest pr = getPullRequestWithReviewers(DEFAULT_REVIEWER);
        BusMessage e = getMessage(pr, DEFAULT_REVIEWER);

        reviewRequestHandler.handleReviewRequested(e);

        waitForInvocationsAndAssert(Constants.CHECK_RUN_CREATE, 1);

        CheckRunMessage msg = getInvocations(Constants.CHECK_RUN_CREATE).get(0).getMessage().get(CheckRunMessage.class);
        assertThat(msg.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(msg.getConclusion()).isNull();
        assertThat(msg.getStatus()).isEqualTo(GHCheckRun.Status.IN_PROGRESS);
    }

    @Test
    public void shouldCreateQueuedCheckRunWhenReviewWasRemovedAndThereAreNoAssigneesLeftTest() {
        GHPullRequest pr = getPullRequestWithReviewers();
        BusMessage e = getMessage(pr, DEFAULT_REVIEWER);

        reviewRequestHandler.handleReviewRequestRemoved(e);

        waitForInvocationsAndAssert(Constants.CHECK_RUN_CREATE, 1);

        CheckRunMessage msg = getInvocations(Constants.CHECK_RUN_CREATE).get(0).getMessage().get(CheckRunMessage.class);
        assertThat(msg.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(msg.getConclusion()).isNull();
        assertThat(msg.getStatus()).isEqualTo(GHCheckRun.Status.QUEUED);
    }

    @Test
    public void shouldntChangeCheckRunWhenReviewWasRemovedAndThereAreAssigneesLeftTest() {
        GHPullRequest pr = getPullRequestWithReviewers(DEFAULT_REVIEWER);
        setField(pr, "assignees", getUsers(DEFAULT_REVIEWER, SECOND_REVIEWER));

        reviewRequestHandler.handleReviewRequestRemoved(getMessage(pr, SECOND_REVIEWER));

        waitForInvocations(Constants.CHECK_RUN_CREATE, 1);
        assertThat(getInvocations(Constants.CHECK_RUN_CREATE)).isEmpty();
    }

    private BusMessage getMessage(GHPullRequest pr, String requestedReviewer) {
        return new BusMessage(client, pr)
            .withSender(getInstance(GHUser.class, fields("login", "reviewer")))
            .with(BusMessage.REQUESTED_REVIEWER, requestedReviewer);
    }
}
