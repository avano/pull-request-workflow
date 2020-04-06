package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles the review events.
 */
public class ReviewSubmitted {
    private static final Logger LOG = LoggerFactory.getLogger(ReviewSubmitted.class);

    @Inject
    Configuration config;

    @Inject
    GHClient client;

    @Inject
    Bus eventBus;

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#pullrequestreviewevent">pull request review</a> submitted event.
     * <p>
     * When the user provided a review, he is removed from the assignee list.
     * <p>
     * When the PR is approved and there are no changes-requested reviews from other reviewers, approved label is added to the PR and it is tried
     * to merge the PR, otherwise the approved review is ignored.
     * <p>
     * When changes are requested, the PR is assigned back to author and corresponding label is added and approved label is removed.
     * <p>
     * When the PR is commented (not only PR's "comment only" action, but also all conversation), commented label is applied and if the author of
     * the comment is in the assignees list, he is removed (as he provided a "review")
     *
     * @param reviewEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.PR_REVIEW_SUBMITTED)
    public void handleReview(EventMessage reviewEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.PR_REVIEW_SUBMITTED);
        GHPullRequestReview review = reviewEvent.get();
        GHPullRequest pr = reviewEvent.getInfo(EventMessage.INFO_PR_KEY);
        List<String> addLabels = new ArrayList<>();
        List<String> removeLabels = new ArrayList<>();

        switch (review.getState()) {
            case APPROVED:
                LOG.info("PR #{}: Approved by {}", pr.getNumber(), reviewEvent.getSender().getLogin());

                List<GHUser> assignees = new ArrayList<>(pr.getAssignees());
                if (assignees.contains(reviewEvent.getSender())) {
                    assignees.remove(reviewEvent.getSender());
                    LOG.info("PR #{}: Removing {} from assignees", pr.getNumber(), reviewEvent.getSender().getLogin());
                    client.setAssignees(pr, assignees);
                } else {
                    LOG.debug("PR #{}: {} wasn't in assignees list and provided a review", pr.getNumber(), reviewEvent.getSender().getLogin());
                }

                // Only add approved label if there are no "changes required" reviews left
                if (!client.changesRequested(pr)) {
                    addLabels.addAll(config.getApprovedLabels());
                    removeLabels.addAll(config.getChangesRequestedLabels());
                    eventBus.publish(Bus.CHECK_RUN_CREATE, new CheckRunMessage(pr, GHCheckRun.Status.COMPLETED, GHCheckRun.Conclusion.SUCCESS));
                }
                // Review was provided, dismiss review requested label
                removeLabels.addAll(config.getReviewRequestedLabels());
                eventBus.publish(Bus.EDIT_LABELS, new LabelsMessage(pr, addLabels, removeLabels));
                // Don't merge here, it will be handled by the checkrun event
                break;
            case CHANGES_REQUESTED:
                LOG.info("PR #{}: Changes requested by {}", pr.getNumber(), reviewEvent.getSender().getLogin());
                client.assignToAuthor(pr);
                eventBus.publish(Bus.CHECK_RUN_CREATE, new CheckRunMessage(pr, GHCheckRun.Status.COMPLETED, GHCheckRun.Conclusion.FAILURE));
                break;
            case COMMENTED:
                LOG.info("PR #{}: Commented by {}", pr.getNumber(), reviewEvent.getSender().getLogin());

                addLabels.addAll(config.getCommentedLabels());
                eventBus.publish(Bus.EDIT_LABELS, new LabelsMessage(pr, addLabels, removeLabels));
                break;
        }
    }
}
