package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.base.BaseHandler;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles label change messages.
 */
public class LabelHandler extends BaseHandler {
    /**
     * Handles label change in the pull request.
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.EDIT_LABELS)
    public void modifyLabels(BusMessage msg) {
        LabelsMessage lm = msg.get(LabelsMessage.class);
        GHPullRequest pr = lm.getPr();
        List<String> addLabels = lm.getAddLabels();
        List<String> removeLabels = lm.getRemoveLabels();
        LOG.info("PR #{}: Removing labels {}, adding labels {}", pr.getNumber(), (removeLabels == null ? "[]" : removeLabels),
            (addLabels == null ? "[]" : addLabels));
        try {
            Set<String> labels = pr.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());
            if (removeLabels != null) {
                removeLabels.forEach(labels::remove);
            }
            if (addLabels != null) {
                labels.addAll(addLabels);
            }
            pr.setLabels(labels.toArray(new String[] {}));
        } catch (IOException e) {
            LOG.error("PR #{}: Unable to modify labels: " + e, pr.getNumber());
        }
    }

    /**
     * Handles the <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pullrequestevent">pull request</a> unlabeled event.
     * <p>
     * In case the work-in-progress label was removed, it tries to merge the PR, otherwise does nothing
     *
     * @param msg {@link BusMessage} instance
     */
    @Log
    @ConsumeEvent(Constants.PR_UNLABELED)
    public void handlePrUnlabeled(BusMessage msg) {
        GHPullRequest pr = msg.get(GHPullRequest.class);
        if (msg.client().getRepositoryConfiguration().wipLabel().equals(msg.get(BusMessage.LABEL, String.class))) {
            LOG.info("PR #{}: Removed work in progress label, attempting to merge", pr.getNumber());
            eventBus.publish(Constants.PR_MERGE, msg);
        } else {
            LOG.debug("PR #{}: Ignoring unlabeled event for label {}", pr.getNumber(), msg.get(BusMessage.LABEL, String.class));
        }
    }
}
