package com.github.avano.pr.workflow.message;

import org.kohsuke.github.GHPullRequest;

import java.util.List;

/**
 * Represents the bus message with labels changes.
 */
public class LabelsMessage {
    private final GHPullRequest pr;
    private final List<String> addLabels;
    private final List<String> removeLabels;

    public LabelsMessage(GHPullRequest pr, List<String> addLabels, List<String> removeLabels) {
        this.pr = pr;
        this.addLabels = addLabels;
        this.removeLabels = removeLabels;
    }

    public GHPullRequest getPr() {
        return pr;
    }

    public List<String> getAddLabels() {
        return addLabels;
    }

    public List<String> getRemoveLabels() {
        return removeLabels;
    }
}
