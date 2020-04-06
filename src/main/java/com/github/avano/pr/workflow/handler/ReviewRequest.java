package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import javax.inject.Inject;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles PR review_requested and review_request_removed events.
 */
public class ReviewRequest {
    private static final Logger LOG = LoggerFactory.getLogger(ReviewRequest.class);

    @Inject
    Configuration config;

    @Inject
    Bus eventBus;

    @Inject
    GHClient client;

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#pullrequestevent">pull request</a> review requested event.
     * <p>
     * When a review was requested, the user is added to the assignees and a review-requested label is added to the PR.
     *
     * @param prEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.PR_REVIEW_REQUESTED)
    public void handleReviewRequested(EventMessage prEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.PR_REVIEW_REQUESTED);
        GHPullRequest pr = prEvent.get();
        final String requestedReviewer = prEvent.getInfo(EventMessage.REQUESTED_REVIEWER);
        LOG.info("PR #{}: {} requested review from {}", pr.getNumber(), prEvent.getSender().getLogin(), requestedReviewer);

        LOG.info("PR #{}: Setting requested reviewers as assignees", pr.getNumber());
        client.setAssignees(pr, client.getRequestedReviewers(pr));

        eventBus.publish(Bus.EDIT_LABELS, new LabelsMessage(pr, config.getReviewRequestedLabels(), null));
        eventBus.publish(Bus.CHECK_RUN_CREATE, new CheckRunMessage(pr, GHCheckRun.Status.IN_PROGRESS, null));
    }

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#pullrequestevent">pull request</a> review removed event.
     * <p>
     * When the review request is removed, the user is removed from the assignees. Also if there are no reviewers left, the review-requested label
     * is removed.
     *
     * @param prEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.PR_REVIEW_REQUEST_REMOVED)
    public void handleReviewRequestRemoved(EventMessage prEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.PR_REVIEW_REQUEST_REMOVED);
        GHPullRequest pr = prEvent.get();
        LOG.info("PR #{}: {} removed review request from {}", pr.getNumber(), prEvent.getSender().getLogin(),
            prEvent.getInfo(EventMessage.REQUESTED_REVIEWER));

        LOG.info("PR #{}: Removing reviewers from assignee list", pr.getNumber());
        // getRequestedReviewers return all previous reviewers without this removed one
        client.setAssignees(pr, client.getRequestedReviewers(pr));

        // If there are no reviewers left, remove review-requested labels if present
        if (client.getRequestedReviewers(pr).isEmpty()) {
            eventBus.publish(Bus.EDIT_LABELS, new LabelsMessage(pr, null, config.getReviewRequestedLabels()));
            eventBus.publish(Bus.CHECK_RUN_CREATE, new CheckRunMessage(pr, GHCheckRun.Status.QUEUED, null));
        }
    }
}

