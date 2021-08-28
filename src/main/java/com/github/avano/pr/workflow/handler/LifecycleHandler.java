package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHUser;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.handler.base.BaseHandler;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles Pull Request lifecycle events.
 */
public class LifecycleHandler extends BaseHandler {
    /**
     * Handles the
     * <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pull_request">pull request</a>
     * reopened event.
     * <p>
     * Tries to merge the PR when it was reopened.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_REOPENED)
    public void handlePrReopened(BusMessage msg) {
        LOG.info("PR #{}: Pull request reopened - attempting to merge", msg.get(GHPullRequest.class).getNumber());
        eventBus.publish(Constants.PR_MERGE, msg);
    }

    /**
     * Handles the
     * <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pull_request">pull request</a>
     * synchronized event.
     * <p>
     * When the PR is updated (new commits are pushed to the PR), it dismisses all reviews, clears internal tracking and set the labels and
     * assignees to the correct state.
     * <p>
     * All users with a review on the PR will be assigned to the PR and re-requested for review.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_UPDATED)
    public void handlePrUpdated(BusMessage msg) {
        GHClient client = msg.client();
        GHPullRequest pr = msg.get(GHPullRequest.class);

        LOG.info("PR #{}: Pull request updated - dismissing all reviews", pr.getNumber());
        // Dismiss all approved/changes requested reviews, since the PR was updated
        try {
            pr.listReviews().toList().stream()
                .filter(r -> r.getState() == GHPullRequestReviewState.APPROVED || r.getState() == GHPullRequestReviewState.CHANGES_REQUESTED)
                .forEach(r -> {
                    try {
                        r.dismiss(client.getRepositoryConfiguration().reviewDismissMessage());
                    } catch (IOException e) {
                        LOG.error("PR #{}: Unable to dismiss review: " + e, pr.getNumber());
                    }
                });
        } catch (IOException e) {
            LOG.error("PR #{}: Unable to list all pull request reviews: " + e, pr.getNumber());
        }

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

        removeLabels.addAll(client.getRepositoryConfiguration().changesRequestedLabels());
        removeLabels.addAll(client.getRepositoryConfiguration().approvedLabels());
        removeLabels.addAll(client.getRepositoryConfiguration().commentedLabels());

        eventBus.publish(Constants.EDIT_LABELS, new BusMessage(client, new LabelsMessage(pr, addLabels, removeLabels)));
    }

    /**
     * Handles the
     * <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pull_request">pull request</a>
     * ready_for_review event.
     * <p>
     * When changing state from draft to ready, it tries to merge the merge request.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_READY_FOR_REVIEW)
    public void handleReadyForReview(BusMessage msg) {
        LOG.info("PR #{}: Marked as ready, attempting to merge", msg.get(GHPullRequest.class).getNumber());
        eventBus.publish(Constants.PR_MERGE, msg);
    }
}
