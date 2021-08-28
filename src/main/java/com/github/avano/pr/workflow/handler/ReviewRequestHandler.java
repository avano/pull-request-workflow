package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.handler.base.BaseHandler;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles PR review_requested and review_request_removed events.
 */
public class ReviewRequestHandler extends BaseHandler {
    /**
     * Handles the <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pullrequestevent">pull request</a> review requested event.
     * <p>
     * When a review was requested, the user is added to the assignees and a review-requested label is added to the PR.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_REVIEW_REQUESTED)
    public void handleReviewRequested(BusMessage msg) {
        GHClient client = msg.client();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        final String requestedReviewer = msg.get(BusMessage.REQUESTED_REVIEWER, String.class);
        LOG.info("PR #{}: {} requested review from {}", pr.getNumber(), msg.getSender().getLogin(), requestedReviewer);

        LOG.info("PR #{}: Setting requested reviewers as assignees", pr.getNumber());
        client.setAssignees(pr, client.getRequestedReviewers(pr));

        eventBus.publish(Constants.EDIT_LABELS,
            new BusMessage(client, new LabelsMessage(pr, client.getRepositoryConfiguration().reviewRequestedLabels(), null)));
        eventBus.publish(Constants.CHECK_RUN_CREATE, new BusMessage(client, new CheckRunMessage(pr, GHCheckRun.Status.IN_PROGRESS, null)));
    }

    /**
     * Handles the <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pullrequestevent">pull request</a> review removed event.
     * <p>
     * When the review request is removed, the user is removed from the assignees. Also if there are no reviewers left, the review-requested label
     * is removed.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_REVIEW_REQUEST_REMOVED)
    public void handleReviewRequestRemoved(BusMessage msg) {
        GHClient client = msg.client();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        LOG.info("PR #{}: {} removed review request from {}", pr.getNumber(), msg.getSender().getLogin(),
            msg.get(BusMessage.REQUESTED_REVIEWER, String.class));

        LOG.info("PR #{}: Removing reviewers from assignee list", pr.getNumber());
        // getRequestedReviewers return all previous reviewers without this removed one
        client.setAssignees(pr, client.getRequestedReviewers(pr));

        // If there are no reviewers left, remove review-requested labels if present
        if (client.getRequestedReviewers(pr).isEmpty()) {
            eventBus.publish(Constants.EDIT_LABELS,
                new BusMessage(client, new LabelsMessage(pr, null, client.getRepositoryConfiguration().reviewRequestedLabels())));
            eventBus.publish(Constants.CHECK_RUN_CREATE, new BusMessage(client, new CheckRunMessage(pr, GHCheckRun.Status.QUEUED, null)));
        }
    }
}

