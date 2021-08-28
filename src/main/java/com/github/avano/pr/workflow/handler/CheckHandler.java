package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.handler.base.BaseHandler;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.CheckRunMessage;
import com.github.avano.pr.workflow.message.CommitStatusMessage;

import java.util.List;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles checkrun and status events.
 */
public class CheckHandler extends BaseHandler {
    /**
     * Handles the <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#status">commit status changed</a> event.
     * <p>
     * If the status is successful, it tries to merge the PR.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.STATUS_CHANGED)
    public void handleStatusChanged(BusMessage msg) {
        CommitStatusMessage csm = msg.get(CommitStatusMessage.class);
        LOG.debug("Commit {}: Commit status is {}", csm.getCommit().getSHA1(), csm.getStatus().name());
        if (csm.getStatus() == GHCommitState.SUCCESS) {
            tryToMergePrWithSha(msg.client(), csm.getCommit().getSHA1());
        }
    }

    /**
     * Handles the <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#check_run">checkrun</a> finished event.
     * <p>
     * If the check run is successful, it tries to merge the PR.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.CHECK_RUN_FINISHED)
    public void handleCheckRunFinished(BusMessage msg) {
        GHCheckRun cr = msg.get(GHCheckRun.class);
        LOG.info("Commit {}: Commit check run finished, conclusion is: {}", cr.getHeadSha(), cr.getConclusion());
        if (cr.getConclusion() == GHCheckRun.Conclusion.SUCCESS) {
            tryToMergePrWithSha(msg.client(), cr.getHeadSha());
        }
    }

    /**
     * Creates a checkrun if the repository is using app authentication.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.CHECK_RUN_CREATE)
    public void handleCheckRunCreate(BusMessage msg) {
        CheckRunMessage crm = msg.get(CheckRunMessage.class);
        if (!msg.client().getRepositoryConfiguration().useChecks()) {
            LOG.info("PR #{}: Not using app auth, not creating review check", crm.getPr().getNumber());
        } else {
            msg.client().createCheckRun(crm.getPr(), crm.getStatus(), crm.getConclusion());
        }
    }

    /**
     * Tries to merge all PRs where the given commit is HEAD of the PR.
     *
     * @param client {@link GHClient} instance
     * @param sha commit sha
     */
    private void tryToMergePrWithSha(GHClient client, String sha) {
        // Check if this SHA is a HEAD of some PR
        List<GHPullRequest> pullRequestList = client.getPullRequests(sha);
        if (pullRequestList != null && pullRequestList.size() > 0) {
            for (GHPullRequest pr : pullRequestList) {
                LOG.debug("Commit {}: This commit is head of PR #{}", sha, pr.getNumber());
                eventBus.publish(Constants.PR_MERGE, new BusMessage(client, pr));
            }
        } else {
            LOG.info("Commit {}: This commit isn't the HEAD of any PR, ignoring its status change", sha);
        }
    }
}
