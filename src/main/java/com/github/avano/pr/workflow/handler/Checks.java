package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.util.CheckState;
import com.github.avano.pr.workflow.message.CommitStatusMessage;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.track.Tracker;

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

    @Inject
    Tracker prTracker;

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#statusevent">commit status changed</a> event.
     * <p>
     * If the commit related to the status is a HEAD of some PR, it will track the status in the internal tracking.
     * <p>
     * If the status is successful, it tries to merge the PR.
     *
     * @param status {@link CommitStatusMessage} instance
     */
    @ConsumeEvent(Bus.STATUS_CHANGED)
    public void handleStatusChanged(CommitStatusMessage status) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.STATUS_CHANGED);
        LOG.debug("Commit {}: Commit status is {}", status.getCommit().getSHA1(), status.getStatus().name());
        // Check if this SHA is a HEAD of some PR
        List<GHPullRequest> pullRequestList = client.getPullRequests(status.getCommit().getSHA1());
        if (pullRequestList != null) {
            if (pullRequestList.size() > 0) {
                for (GHPullRequest pr : pullRequestList) {
                    LOG.debug("Commit {}: This commit is head of PR #{}", status.getCommit().getSHA1(), pr.getNumber());
                    // Track the state of the check in the PR
                    CheckState state;
                    switch (status.getStatus()) {
                        case SUCCESS:
                            state = CheckState.SUCCESS;
                            break;
                        case PENDING:
                            state = CheckState.IN_PROGRESS;
                            break;
                        default:
                            state = CheckState.FAILED;
                            break;
                    }
                    prTracker.setCheckState(pr, status.getName(), state);
                    if (state == CheckState.SUCCESS) {
                        // Try to merge the PR
                        eventBus.publish(Bus.PR_MERGE, pr);
                    }
                }
            } else {
                LOG.info("Commit {}: This commit isn't the HEAD of any PR, ignoring its status change", status.getCommit().getSHA1());
            }
        }
    }

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#checkrunevent">checkrun</a> finished event.
     * <p>
     * If the commit related to the check run is a HEAD of some PR, it will track the check run conclusion in the internal tracking.
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
        // Check if this SHA is a HEAD of some PR
        List<GHPullRequest> pullRequestList = client.getPullRequests(checkRun.getHeadSha());
        if (pullRequestList != null) {
            if (pullRequestList.size() > 0) {
                for (GHPullRequest pr : pullRequestList) {
                    LOG.debug("Commit {}: This commit is head of PR #{}", checkRun.getHeadSha(), pr.getNumber());
                    CheckState state = "success".equals(checkRun.getConclusion()) ? CheckState.SUCCESS : CheckState.FAILED;
                    // Track the state of the check in the PR
                    prTracker.setCheckState(pr, checkRun.getName(), state);
                    if (state == CheckState.SUCCESS) {
                        // Try to merge the PR
                        eventBus.publish(Bus.PR_MERGE, pr);
                    }
                }
            } else {
                LOG.info("Commit {}: This commit isn't the HEAD of any PR, ignoring its checkrun result", checkRun.getHeadSha());
            }
        }
    }

    /**
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#checkrunevent">checkrun</a> created event.
     * <p>
     * If the commit related to the check run is a HEAD of some PR, it will track the check run in the internal tracking.
     *
     * @param prEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.CHECK_RUN_CREATED)
    public void handleCheckRunCreated(EventMessage prEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.CHECK_RUN_CREATED);
        GHCheckRun checkRun = prEvent.get();
        // Check if this SHA is a HEAD of some PR
        List<GHPullRequest> pullRequestList = client.getPullRequests(checkRun.getHeadSha());
        if (pullRequestList != null) {
            if (pullRequestList.size() > 0) {
                for (GHPullRequest pr : pullRequestList) {
                    LOG.debug("Commit {}: This commit is head of PR #{}", checkRun.getHeadSha(), pr.getNumber());
                    // Track the state of the check in the PR
                    prTracker.setCheckState(pr, checkRun.getName(), CheckState.IN_PROGRESS);
                }
            } else {
                LOG.info("Commit {}: This commit isn't the HEAD of any PR, ignoring its {} event", checkRun.getHeadSha(),
                    (prEvent.getInfo(EventMessage.INFO_CHECK_RUN_ACTION) == null ? "created" : prEvent.getInfo(EventMessage.INFO_CHECK_RUN_ACTION)));
            }
        }
    }
}
