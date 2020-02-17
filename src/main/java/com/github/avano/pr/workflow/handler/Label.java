package com.github.avano.pr.workflow.handler;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;

import javax.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.vertx.ConsumeEvent;

/**
 * Handles label change messages.
 */
public class Label {
    private static final Logger LOG = LoggerFactory.getLogger(Label.class);

    @Inject
    Configuration config;

    @Inject
    Bus eventBus;

    /**
     * Handles label change messages.
     *
     * @param labelsMessage {@link LabelsMessage} instance
     */
    @ConsumeEvent(Bus.EDIT_LABELS)
    public void modifyLabels(LabelsMessage labelsMessage) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.EDIT_LABELS);
        GHPullRequest pr = labelsMessage.getPr();
        List<String> addLabels = labelsMessage.getAddLabels();
        List<String> removeLabels = labelsMessage.getRemoveLabels();
        LOG.info("PR #{}: Removing labels {}, adding labels {}", pr.getNumber(), (removeLabels == null ? "[]" : removeLabels),
            (addLabels == null ? "[]" : addLabels));
        try {
            Set<String> labels = pr.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());
            if (removeLabels != null) {
                labels.removeAll(removeLabels);
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
     * Handles the <a href="https://developer.github.com/v3/activity/events/types/#pullrequestevent">pull request</a> unlabeled event.
     * <p>
     * In case the work-in-progress label was removed, it tries to merge the PR, otherwise does nothing
     *
     * @param unlabeledEvent {@link EventMessage} instance
     */
    @ConsumeEvent(Bus.PR_UNLABELED)
    public void handlePrUnlabeled(EventMessage unlabeledEvent) {
        LOG.trace(Bus.EVENT_RECEIVED_MESSAGE + Bus.PR_UNLABELED);
        GHPullRequest pr = unlabeledEvent.get();
        if (config.getWipLabel().equals(unlabeledEvent.getInfo(EventMessage.LABEL))) {
            LOG.info("PR #{}: Removed work in progress label, attempting to merge", pr.getNumber());
            eventBus.publish(Bus.PR_MERGE, pr);
        } else {
            LOG.debug("PR #{}: Ignoring unlabeled event for label {}", pr.getNumber(), unlabeledEvent.getInfo(EventMessage.LABEL));
        }
    }
}
