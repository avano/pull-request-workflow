package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPerson;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHUser;

import com.github.avano.pr.workflow.config.ApprovalStrategy;
import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.handler.base.BaseHandler;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.ConflictMessage;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Merges the Pull request if it is possible.
 */
public class MergeHandler extends BaseHandler {
    /**
     * Merges the PR if all prerequisities are fulfilled.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_MERGE)
    public void merge(BusMessage msg) {
        GHClient client = msg.client();
        GHPullRequest pr = msg.get(GHPullRequest.class);
        try {
            // Refresh the PR to work with latest state
            pr.refresh();
            if (pr.isMerged()) {
                LOG.info("PR #{}: Not merging - already merged", pr.getNumber());
                return;
            }

            if (pr.isDraft()) {
                LOG.info("PR #{}: Not merging - draft state", pr.getNumber());
                return;
            }

            if (pr.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet()).contains(client.getRepositoryConfiguration().wipLabel())) {
                LOG.info("PR #{}: Not merging - work in progress", pr.getNumber());
                return;
            }

            // Check if all required checks passed
            final String targetBranch = pr.getBase().getRef();
            Collection<String> requiredChecks = client.getRequiredChecks(targetBranch);
            if (requiredChecks != null && !requiredChecks.isEmpty()) {
                LOG.info("PR #{}: Required checks: {}", pr.getNumber(), String.join(", ", requiredChecks));
                final StringBuilder logMsg = new StringBuilder("PR #").append(pr.getNumber()).append(": Checks - ");
                client.getChecks(pr).forEach((name, result) -> {
                    logMsg.append("[").append(name).append(": ").append(result).append("], ");
                    if ("success".equalsIgnoreCase(result)) {
                        requiredChecks.remove(name);
                    }
                });

                LOG.info(logMsg.substring(0, logMsg.length() - 2));
                if (!requiredChecks.isEmpty()) {
                    LOG.info("PR #{}: Not merging - some of the required checks did not pass", pr.getNumber());
                    return;
                }
            } else {
                LOG.debug("PR #{}: No required checks defined for branch {}", pr.getNumber(), targetBranch);
            }

            if (pr.getMergeable() == null || !pr.getMergeable()) {
                LOG.warn("PR #{}: Not merging - not mergeable", pr.getNumber());
                return;
            }

            if (Constants.DEPENDABOT_NAME.equals(client.getAuthor(pr).getLogin()) && client.getRepositoryConfiguration().automergeDependabot()) {
                LOG.info("PR #{}: Automerging dependabot PR", pr.getNumber());
                mergePullRequest(msg);
                return;
            }

            if (client.getRepositoryConfiguration().automergeOwnerPRs() && client.getAuthor(pr).getLogin()
                .equals(client.getRepositoryConfiguration().repository().split("/")[0])) {
                LOG.info("PR #{}: Automerging owner's PR", pr.getNumber());
                mergePullRequest(msg);
                return;
            }

            Map<GHUser, GHPullRequestReviewState> reviews = client.getReviews(pr);
            if (reviews.size() == 0) {
                LOG.info("PR #{}: Not merging - no reviews", pr.getNumber());
                return;
            }

            if (reviews.values().stream().noneMatch(r -> r == GHPullRequestReviewState.APPROVED)) {
                LOG.info("PR #{}: Not merging - no approvals", pr.getNumber());
                return;
            }

            if (client.changesRequested(pr)) {
                LOG.info("PR #{}: Not merging - at least one \"changes requested\" review present", pr.getNumber());
                return;
            }

            if (ApprovalStrategy.ALL == client.getRepositoryConfiguration().approvalStrategy()) {
                if (pr.getRequestedReviewers().size() != reviews.size() ||
                    reviews.values().stream().anyMatch(r -> r != GHPullRequestReviewState.APPROVED)) {
                    LOG.info("PR #{}: Not merging - approval from some reviewer missing (using \"all\" strategy)", pr.getNumber());
                    return;
                }
            }

            mergePullRequest(msg);
        } catch (IOException e) {
            LOG.error("PR #{}: Unable to process merge", pr.getNumber(), e);
        }
    }

    /**
     * Set the reviewers as assignees and merge the pull request.
     *
     * @param msg {@link BusMessage} instance
     */
    private void mergePullRequest(BusMessage msg) {
        GHClient client = msg.client();
        GHPullRequest pr = msg.get(GHPullRequest.class);

        try {
            if (!Constants.DEPENDABOT_NAME.equals(client.getAuthor(pr).getLogin())) {
                // Assign the PR to all users who provided a review, so that it will be visible who was involved
                Set<GHUser> reviewers = client.getReviews(pr).keySet();
                reviewers.remove(pr.getUser());
                LOG.info("PR #{}: Setting assignees to: {}", pr.getNumber(),
                    reviewers.stream().map(GHPerson::getLogin).collect(Collectors.joining(", ")));
                client.setAssignees(pr, reviewers);
            }

            // Save open PRs so that we can check later if merging this PR caused a conflict in some other PR
            List<GHPullRequest> mergeableOpenPullRequests = client.listOpenPullRequests().stream().filter(pullRequest -> {
                try {
                    // Filter out this PR and all that are not mergeable
                    return pr.getNumber() != pullRequest.getNumber() && pullRequest.getMergeable() != null && pullRequest.getMergeable();
                } catch (IOException e) {
                    LOG.error("PR #{}: Unable to determine mergeable state", pullRequest.getNumber());
                }
                return false;
            }).collect(Collectors.toList());

            LOG.info("PR #{}: Merging", pr.getNumber());
            pr.merge(client.getRepositoryConfiguration().mergeMessage(), null, client.getRepositoryConfiguration().mergeMethod());
            LOG.info("PR #{}: Merged", pr.getNumber());

            if (!mergeableOpenPullRequests.isEmpty()) {
                eventBus.publish(Constants.PR_CHECK_CONFLICT, new BusMessage(client, new ConflictMessage(pr.getNumber(), mergeableOpenPullRequests)));
            }
        } catch (IOException e) {
            LOG.error("PR #{}: Unable to process merge", pr.getNumber(), e);
        }
    }
}
