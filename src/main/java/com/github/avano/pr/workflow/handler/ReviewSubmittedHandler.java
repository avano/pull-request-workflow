package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHUser;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.handler.base.BaseHandler;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles the review events.
 */
public class ReviewSubmittedHandler extends BaseHandler {
    /**
     * Handles the
     * <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pull_request_review">pull request review</a> submitted event.
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
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_REVIEW_SUBMITTED)
    public void handleReview(BusMessage msg) {
        GHClient client = msg.client();
        GHPullRequestReview review = msg.get(GHPullRequestReview.class);
        GHPullRequest pr = msg.get(BusMessage.INFO_PR_KEY, GHPullRequest.class);
        List<String> addLabels = new ArrayList<>();
        List<String> removeLabels = new ArrayList<>();

        switch (review.getState()) {
            case APPROVED:
                LOG.info("PR #{}: Approved by {}", pr.getNumber(), msg.getSender().getLogin());

                List<GHUser> assignees = new ArrayList<>(pr.getAssignees());
                if (assignees.contains(msg.getSender())) {
                    assignees.remove(msg.getSender());
                    LOG.info("PR #{}: Removing {} from assignees", pr.getNumber(), msg.getSender().getLogin());
                    client.setAssignees(pr, assignees);
                } else {
                    LOG.debug("PR #{}: {} wasn't in assignees list and provided a review", pr.getNumber(), msg.getSender().getLogin());
                }

                // Only add approved label if there are no "changes required" reviews left
                if (!client.changesRequested(pr)) {
                    addLabels.addAll(client.getRepositoryConfiguration().approvedLabels());
                    removeLabels.addAll(client.getRepositoryConfiguration().changesRequestedLabels());
                    eventBus.publish(Constants.CHECK_RUN_CREATE,
                        new BusMessage(client, new CheckRunMessage(pr, GHCheckRun.Status.COMPLETED, GHCheckRun.Conclusion.SUCCESS)));
                }
                // Review was provided, dismiss review requested label
                removeLabels.addAll(client.getRepositoryConfiguration().reviewRequestedLabels());
                eventBus.publish(Constants.EDIT_LABELS, new BusMessage(client, new LabelsMessage(pr, addLabels, removeLabels)));
                // Don't merge here, it will be handled by the checkrun event
                break;
            case CHANGES_REQUESTED:
                LOG.info("PR #{}: Changes requested by {}", pr.getNumber(), msg.getSender().getLogin());
                client.assignToAuthor(pr);
                eventBus.publish(Constants.CHECK_RUN_CREATE,
                    new BusMessage(client, new CheckRunMessage(pr, GHCheckRun.Status.COMPLETED, GHCheckRun.Conclusion.FAILURE)));
                removeLabels.addAll(client.getRepositoryConfiguration().approvedLabels());
                removeLabels.addAll(client.getRepositoryConfiguration().reviewRequestedLabels());
                eventBus.publish(Constants.EDIT_LABELS, new BusMessage(client, new LabelsMessage(pr,
                    client.getRepositoryConfiguration().changesRequestedLabels(), removeLabels)));
                break;
            case COMMENTED:
                LOG.info("PR #{}: Commented by {}", pr.getNumber(), msg.getSender().getLogin());

                addLabels.addAll(client.getRepositoryConfiguration().commentedLabels());
                eventBus.publish(Constants.EDIT_LABELS, new BusMessage(client, new LabelsMessage(pr, addLabels, removeLabels)));
                break;
            default:
                LOG.info("PR #{}: Ignoring review state {}", pr.getNumber(), review.getState().name().toLowerCase());
                break;
        }
    }
}
