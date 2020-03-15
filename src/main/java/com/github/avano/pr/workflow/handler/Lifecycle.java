package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import javax.inject.Inject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles Pull Request lifecycle events.
 */
public class Lifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(Lifecycle.class);

    @Inject
    GHClient client;

    @Inject
    Bus eventBus;

    @Inject
    Configuration config;

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#pullrequestevent">pull request</a> reopened event.
     * <p>
     * Tries to merge the PR when it was reopened.
     *
     * @param prEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.PR_REOPENED)
    public void handlePrReopened(EventMessage prEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.PR_REOPENED);
        GHPullRequest pr = prEvent.get();
        LOG.info("PR #{}: Pull request reopened - attempting to merge", pr.getNumber());
        eventBus.publish(Bus.PR_MERGE, pr);
    }

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#pullrequestevent">pull request</a> synchronized event.
     * <p>
     * When the PR is updated (new commits are pushed to the PR), it dismisses all reviews, clears internal tracking and set the labels and
     * assignees to the correct state.
     * <p>
     * All users with a review on the PR will be assigned to the PR and re-requested for review.
     *
     * @param prEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.PR_UPDATED)
    public void handlePrUpdated(EventMessage prEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.PR_REOPENED);
        GHPullRequest pr = prEvent.get();

        LOG.info("PR #{}: Pull request updated - dismissing all reviews", pr.getNumber());
        // Dismiss all approved/changes requested reviews, since the PR was updated
        pr.listReviews().asList().stream()
            .filter(r -> r.getState() == GHPullRequestReviewState.APPROVED || r.getState() == GHPullRequestReviewState.CHANGES_REQUESTED)
            .forEach(r -> {
                try {
                    r.dismiss(config.getReviewDismissMessage());
                } catch (IOException e) {
                    LOG.error("PR #{}: Unable to dismiss review: " + e, pr.getNumber());
                }
            });

        // Re-apply labels to current state
        List<String> addLabels = new ArrayList<>();
        List<String> removeLabels = new ArrayList<>();
        Map<GHUser, GHPullRequestReviewState> reviews = client.getReviews(pr);
        if (!reviews.isEmpty()) {
            // Request review from all previous reviewers (except for the author of the PR - if he responds to some comment, it is counted as
            // "commented" review)
            // Set the assignees back to requested reviewers
            // Don't add "review requested labels", as that will be done by the "review requested" event
            List<GHUser> reviewers = reviews.keySet().stream().filter(u -> !u.equals(client.getAuthor(pr))).collect(Collectors.toList());

            client.requestReviewers(pr, reviewers);
            client.setAssignees(pr, reviewers);
        }

        removeLabels.addAll(config.getChangesRequestedLabels());
        removeLabels.addAll(config.getApprovedLabels());
        removeLabels.addAll(config.getCommentedLabels());

        eventBus.publish(Bus.EDIT_LABELS, new LabelsMessage(pr, addLabels, removeLabels));
    }

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#pullrequestevent">pull request</a> ready_for_review event.
     *
     * @param prEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.PR_READY_FOR_REVIEW)
    public void handleReadyForReview(EventMessage prEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.PR_READY_FOR_REVIEW);
        GHPullRequest pr = prEvent.get();
        LOG.info("PR #{}: Marked as ready, attempting to merge", pr.getNumber());
        eventBus.publish(Bus.PR_MERGE, pr);
    }
}
