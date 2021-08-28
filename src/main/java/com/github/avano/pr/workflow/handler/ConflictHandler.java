package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.handler.base.BaseHandler;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.ConflictMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.vertx.ConsumeEvent;

public class ConflictHandler extends BaseHandler {
    /**
     * Checks if a PR merge caused conflict in other opened PRs, if so a comment is added to each open PR and it's assigned back to the author to fix.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_CHECK_CONFLICT)
    public void checkForConflict(BusMessage msg) {
        GHClient client = msg.client();
        ConflictMessage cm = msg.get(ConflictMessage.class);

        if (client.getRepositoryConfiguration().conflictMessage() == null || client.getRepositoryConfiguration().conflictMessage().isEmpty()) {
            LOG.debug("Skipping conflict detection - conflict message not set");
            return;
        }

        for (GHPullRequest openPullRequest : cm.getOpenPullRequests()) {
            try {
                openPullRequest.refresh();
                if ("dirty".equals(openPullRequest.getMergeableState())) {
                    LOG.info("PR #{}: Caused conflict in PR #{}", cm.getMergedPrId(), openPullRequest.getNumber());
                    client.postComment(openPullRequest, client.getRepositoryConfiguration().conflictMessage().replace("<ID>",
                        cm.getMergedPrId() + ""));
                    client.assignToAuthor(openPullRequest);
                    List<String> removeLabels = new ArrayList<>();
                    removeLabels.addAll(client.getRepositoryConfiguration().approvedLabels());
                    removeLabels.addAll(client.getRepositoryConfiguration().reviewRequestedLabels());
                    eventBus.publish(Constants.EDIT_LABELS, new BusMessage(client, new LabelsMessage(openPullRequest,
                        client.getRepositoryConfiguration().changesRequestedLabels(), removeLabels)));
                } else {
                    LOG.trace("PR #{}: No conflict in PR #{}", cm.getMergedPrId(), openPullRequest.getNumber());
                }
            } catch (IOException e) {
                LOG.error("PR #{}: Unable to process PR", openPullRequest.getNumber(), e);
            }
        }
    }
}
