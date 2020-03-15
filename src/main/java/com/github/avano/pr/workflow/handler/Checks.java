package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.message.CommitStatusMessage;
import com.github.avano.pr.workflow.message.EventMessage;

import javax.inject.Inject;

import java.util.List;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles checkrun and status events.
 */
public class Checks {
    private static final Logger LOG = LoggerFactory.getLogger(Checks.class);

    @Inject
    GHClient client;

    @Inject
    Bus eventBus;

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#statusevent">commit status changed</a> event.
     * <p>
     * If the status is successful, it tries to merge the PR.
     *
     * @param status {@link CommitStatusMessage} instance
     */
    @ConsumeEvent(Bus.STATUS_CHANGED)
    public void handleStatusChanged(CommitStatusMessage status) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.STATUS_CHANGED);
        LOG.debug("Commit {}: Commit status is {}", status.getCommit().getSHA1(), status.getStatus().name());
        if (status.getStatus() == GHCommitState.SUCCESS) {
            tryToMergePrWithSha(status.getCommit().getSHA1());
        }
    }

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#checkrunevent">checkrun</a> finished event.
     * <p>
     * If the check run is successful, it tries to merge the PR.
     *
     * @param prEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.CHECK_RUN_FINISHED)
    public void handleCheckRunFinished(EventMessage prEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.CHECK_RUN_FINISHED);
        GHCheckRun checkRun = prEvent.get();
        LOG.debug("Commit {}: Commit check run finished, conclusion is: {}", checkRun.getHeadSha(), checkRun.getConclusion());
        if ("success".equalsIgnoreCase(checkRun.getConclusion())) {
            tryToMergePrWithSha(checkRun.getHeadSha());
        }
    }

    /**
     * Tries to merge all PRs where the given commit is HEAD of the PR.
     * @param sha sha
     */
    private void tryToMergePrWithSha(String sha) {
        // Check if this SHA is a HEAD of some PR
        List<GHPullRequest> pullRequestList = client.getPullRequests(sha);
        if (pullRequestList != null && pullRequestList.size() > 0) {
            for (GHPullRequest pr : pullRequestList) {
                LOG.debug("Commit {}: This commit is head of PR #{}", sha, pr.getNumber());
                eventBus.publish(Bus.PR_MERGE, pr);
            }
        } else {
            LOG.info("Commit {}: This commit isn't the HEAD of any PR, ignoring its status change", sha);
        }
    }
}
