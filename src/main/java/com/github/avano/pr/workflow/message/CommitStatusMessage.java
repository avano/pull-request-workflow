package com.github.avano.pr.workflow.message;

import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitState;

/**
 * Represents the bus message with commit status.
 */
public class CommitStatusMessage {
    private GHCommit commit;
    private GHCommitState status;
    private String name;

    public CommitStatusMessage(GHCommit commit, GHCommitState status, String name) {
        this.commit = commit;
        this.status = status;
        this.name = name;
    }

    public GHCommit getCommit() {
        return commit;
    }

    public void setCommit(GHCommit commit) {
        this.commit = commit;
    }

    public GHCommitState getStatus() {
        return status;
    }

    public void setStatus(GHCommitState status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
