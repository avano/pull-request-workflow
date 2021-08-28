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

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.ReviewSubmittedHandler;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import javax.inject.Inject;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ReviewSubmittedHandlerTest extends TestParent {
    private static final int CHANGES_REQUESTED_PR_ID = 1000;

    @Inject
    ReviewSubmittedHandler reviewSubmittedHandler;

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
        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        waitForInvocationsAndAssert(Constants.EDIT_LABELS, 1);

        LabelsMessage labels = getInvocations(Constants.EDIT_LABELS).get(0).getMessage().get(LabelsMessage.class);

        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(client.getRepositoryConfiguration().approvedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).contains(client.getRepositoryConfiguration().reviewRequestedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).contains(client.getRepositoryConfiguration().changesRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldntAddApprovedLabelWhenChangesWereRequestedTest() {
        setField(pr, "number", CHANGES_REQUESTED_PR_ID);
        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        waitForInvocationsAndAssert(Constants.EDIT_LABELS, 1);
        LabelsMessage labels = getInvocations(Constants.EDIT_LABELS).get(0).getMessage().get(LabelsMessage.class);

        assertThat(labels.getPr().getNumber()).isEqualTo(CHANGES_REQUESTED_PR_ID);
        assertThat(labels.getAddLabels()).isEmpty();
        assertThat(labels.getRemoveLabels()).contains(client.getRepositoryConfiguration().reviewRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldAddChangesRequestedLabelTest() {
        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.CHANGES_REQUESTED));

        waitForInvocationsAndAssert(Constants.EDIT_LABELS, 1);
        LabelsMessage labels = getInvocations(Constants.EDIT_LABELS).get(0).getMessage().get(LabelsMessage.class);
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(client.getRepositoryConfiguration().changesRequestedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).contains(client.getRepositoryConfiguration().reviewRequestedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldDismissApprovedLabelWhenChangesWereRequestedTest() {
        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.CHANGES_REQUESTED));

        waitForInvocationsAndAssert(Constants.EDIT_LABELS, 1);
        LabelsMessage labels = getInvocations(Constants.EDIT_LABELS).get(0).getMessage().get(LabelsMessage.class);
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(client.getRepositoryConfiguration().changesRequestedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).contains(client.getRepositoryConfiguration().approvedLabels().toArray(new String[0]));
    }

    @Test
    public void shouldAddCommentedLabelTest() {
        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.COMMENTED));

        waitForInvocationsAndAssert(Constants.EDIT_LABELS, 1);
        LabelsMessage labels = getInvocations(Constants.EDIT_LABELS).get(0).getMessage().get(LabelsMessage.class);
        assertThat(labels.getPr().getNumber()).isEqualTo(PULL_REQUEST_ID);
        assertThat(labels.getAddLabels()).containsExactly(client.getRepositoryConfiguration().commentedLabels().toArray(new String[0]));
        assertThat(labels.getRemoveLabels()).isEmpty();
    }

    @Test
    public void shouldRemoveFromAssigneesWhenPRWasApprovedTest() {
        setField(pr, "assignees", getUsers("troublemaker", "reviewer"));

        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        assertThat(new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("assignees")).containsExactlyInAnyOrder("troublemaker");
    }

    @Test
    public void shouldntChangeAssigneesWhenCommentedTest() {
        setField(pr, "assignees", getUsers("reviewer"));

        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.COMMENTED));

        assertThat(getRequests(PR_PATCH)).isEmpty();
    }

    @Test
    public void shouldAssignToAuthorWhenChangesWereRequestedTest() {
        // The default PR has 1 reviewer requested, so set him as assignee
        setField(pr, "assignees", getUsers("approved"));

        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.CHANGES_REQUESTED));

        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        JSONArray assignees = new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("assignees");
        assertThat(assignees).containsExactly("creator");
    }

    @Test
    public void shouldCreateSuccessCheckRunWhenApprovedTest() {
        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        waitForInvocationsAndAssert(Constants.CHECK_RUN_CREATE, 1);

        CheckRunMessage msg = getInvocations(Constants.CHECK_RUN_CREATE).get(0).getMessage().get(CheckRunMessage.class);
        assertThat(msg.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(msg.getConclusion()).isEqualTo(GHCheckRun.Conclusion.SUCCESS);
        assertThat(msg.getStatus()).isEqualTo(GHCheckRun.Status.COMPLETED);
    }

    @Test
    public void shouldntCreateSuccessCheckRunWhenApprovedButChangesAreStillRequestedTest() {
        setField(pr, "number", CHANGES_REQUESTED_PR_ID);
        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.APPROVED));

        waitForInvocations(Constants.CHECK_RUN_CREATE, 1);
        assertThat(getInvocations(Constants.CHECK_RUN_CREATE)).isEmpty();
    }

    @Test
    public void shouldCreateFailedCheckRunWhenChangesWereRequestedTest() {
        reviewSubmittedHandler.handleReview(getEvent(pr, GHPullRequestReviewState.CHANGES_REQUESTED));
        waitForInvocationsAndAssert(Constants.CHECK_RUN_CREATE, 1);

        CheckRunMessage msg = getInvocations(Constants.CHECK_RUN_CREATE).get(0).getMessage().get(CheckRunMessage.class);
        assertThat(msg.getPr().getNumber()).isEqualTo(pr.getNumber());
        assertThat(msg.getConclusion()).isEqualTo(GHCheckRun.Conclusion.FAILURE);
        assertThat(msg.getStatus()).isEqualTo(GHCheckRun.Status.COMPLETED);
    }

    private BusMessage getEvent(GHPullRequest pr, GHPullRequestReviewState state) {
        GHPullRequestReview review = getInstance(GHPullRequestReview.class, fields("state", state));
        return new BusMessage(client, review).with(BusMessage.INFO_PR_KEY, pr)
            .withSender(getInstance(GHUser.class, fields("login", "reviewer")));
    }
}
