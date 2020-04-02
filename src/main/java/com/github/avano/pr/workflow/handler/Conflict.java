package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.message.ConflictMessage;

import javax.inject.Inject;

import java.io.IOException;

import io.quarkus.vertx.ConsumeEvent;

public class Conflict {
    private static final Logger LOG = LoggerFactory.getLogger(Conflict.class);

    @Inject
    Configuration config;

    @Inject
    GHClient client;

    /**
     * Checks if a PR merge caused conflict in other opened PRs.
     * @param msg {@link ConflictMessage} instance
     */
    @ConsumeEvent(Bus.PR_CHECK_CONFLICT)
    public void checkForConflict(ConflictMessage msg) {
        for (GHPullRequest openPullRequest : msg.getOpenPullRequests()) {
            try {
                openPullRequest.refresh();
                if ("dirty".equals(openPullRequest.getMergeableState())) {
                    LOG.info("PR #{}: Caused conflict in PR #{}", msg.getMergedPrId(), openPullRequest.getNumber());
                    client.postComment(openPullRequest, config.getConflictMessage(msg.getMergedPrId()));
                    client.assignToAuthor(openPullRequest);
                } else {
                    LOG.trace("PR #{}: No conflict in PR #{}", msg.getMergedPrId(), openPullRequest.getNumber());
                }
            } catch (IOException e) {
                LOG.error("PR #{}: Unable to process PR: {}", openPullRequest.getNumber(), e);
            }
        }
    }
}
