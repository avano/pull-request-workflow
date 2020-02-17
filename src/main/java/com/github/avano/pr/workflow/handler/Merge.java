package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.ApprovalStrategy;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.track.Tracker;
import com.github.avano.pr.workflow.util.CheckState;

import javax.inject.Inject;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Merges the Pull request if it is possible.
 */
public class Merge {
    private static final Logger LOG = LoggerFactory.getLogger(Merge.class);

    @Inject
    Configuration config;

    @Inject
    GHClient client;

    @Inject
    Tracker tracker;

    /**
     * Merges the PR if all prerequisities are fulfilled.
     *
     * @param pr pull request
     */
    @ConsumeEvent(Bus.PR_MERGE)
    public void merge(GHPullRequest pr) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.PR_MERGE);
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

            if (pr.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet()).contains(config.getWipLabel())) {
                LOG.info("PR #{}: Not merging - work in progress", pr.getNumber());
                return;
            }

            // Check if all required checks passed
            final String targetBranch = pr.getBase().getRef();
            Collection<String> requiredChecks = client.getRequiredChecks(targetBranch);
            if (requiredChecks != null && !requiredChecks.isEmpty()) {
                final StringBuilder logMsg = new StringBuilder("PR #").append(pr.getNumber()).append(": Checks - ");
                boolean proceed = true;
                Map<String, CheckState> prChecks = tracker.getChecks(pr);
                for (String check : requiredChecks) {
                    // If there is no tracking information, either the merge is called really fast, or the check was done before this app started
                    // and we don't have the corresponding event, so there is nothing we can do about it, so ignore it
                    // If it is a required check, the PR won't be mergeable, so it will be caught later
                    if (prChecks.get(check) == null) {
                        logMsg.append("[").append(check).append(": unknown], ");
                    } else {
                        logMsg.append("[").append(check).append(": ").append(prChecks.get(check).name()).append("], ");
                        proceed = proceed && (prChecks.get(check) == CheckState.SUCCESS);
                    }
                }

                LOG.info(logMsg.substring(0, logMsg.length() - 2));
                if (!proceed) {
                    LOG.info("PR #{}: Not merging - some of the required checks did not pass", pr.getNumber());
                    return;
                }
            } else {
                LOG.debug("PR #{}: No required checks defined for branch {}", pr.getNumber(), targetBranch);
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

            if (ApprovalStrategy.ALL == config.getApprovalStrategy()) {
                if (pr.getRequestedReviewers().size() != reviews.size() ||
                    reviews.values().stream().anyMatch(r -> r != GHPullRequestReviewState.APPROVED)) {
                    LOG.info("PR #{}: Not merging - approval from some reviewer missing (using \"all\" strategy)", pr.getNumber());
                    return;
                }
            }

            if (pr.getMergeable() == null || !pr.getMergeable()) {
                LOG.warn("PR #{}: Not merging - not mergeable", pr.getNumber());
                return;
            }

            LOG.info("PR #{}: Merging", pr.getNumber());
            pr.merge(config.getMergeMessage(), null, config.getMergeMethod());
            LOG.info("PR #{}: Merged", pr.getNumber());
        } catch (IOException e) {
            LOG.error("Unable to process merge: " + e);
        }
    }
}
