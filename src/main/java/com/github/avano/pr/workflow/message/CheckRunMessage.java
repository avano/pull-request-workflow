package com.github.avano.pr.workflow.message;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;

/**
 * Represents the bus message for creating new check-run.
 */
public class CheckRunMessage {
    private GHPullRequest pr;
    private GHCheckRun.Status status;
    private GHCheckRun.Conclusion conclusion;

    public CheckRunMessage(GHPullRequest pr, GHCheckRun.Status status, GHCheckRun.Conclusion conclusion) {
        this.pr = pr;
        this.status = status;
        this.conclusion = conclusion;
    }

    public GHPullRequest getPr() {
        return pr;
    }

    public void setPr(GHPullRequest pr) {
        this.pr = pr;
    }

    public GHCheckRun.Status getStatus() {
        return status;
    }

    public void setStatus(GHCheckRun.Status status) {
        this.status = status;
    }

    public GHCheckRun.Conclusion getConclusion() {
        return conclusion;
    }

    public void setConclusion(GHCheckRun.Conclusion conclusion) {
        this.conclusion = conclusion;
    }
}
