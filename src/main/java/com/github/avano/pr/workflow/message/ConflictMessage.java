package com.github.avano.pr.workflow.message;

import org.kohsuke.github.GHPullRequest;

import java.util.List;

/**
 * Represents the bus message related to checking if conflict was created.
 */
public class ConflictMessage {
    private final int mergedPrId;

    private final List<GHPullRequest> openPullRequests;

    public ConflictMessage(int mergedPrId, List<GHPullRequest> openPullRequests) {
        this.mergedPrId = mergedPrId;
        this.openPullRequests = openPullRequests;
    }

    public int getMergedPrId() {
        return mergedPrId;
    }

    public List<GHPullRequest> getOpenPullRequests() {
        return openPullRequests;
    }
}
