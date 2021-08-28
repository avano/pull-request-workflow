package com.github.avano.pr.workflow.config;

import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a configuration for a single repository.
 */
public class RepositoryConfig {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryConfig.class);

    private AuthMethod auth = AuthMethod.APP;

    private String user;

    private String token;

    private long appId = -1;

    private long installationId = -1;

    private String privateKey;

    private boolean keyIsFile = true;

    private String webhookSecret;

    private String repository;

    private List<String> approvedLabels = List.of("approved");

    private List<String> changesRequestedLabels = List.of("needs-update");

    private List<String> reviewRequestedLabels = List.of("review-requested");

    private List<String> commentedLabels = List.of("commented");

    private String wipLabel = "WIP";

    private String reviewDismissMessage = "Pull request was updated";

    private String mergeMessage = "\uD83E\uDD16 Merged by https://github.com/avano/pull-request-workflow";

    private String conflictMessage = "Pull request #<ID> caused a conflict in this PR";

    private ApprovalStrategy approvalStrategy = ApprovalStrategy.ANY;

    private GHPullRequest.MergeMethod mergeMethod = GHPullRequest.MergeMethod.MERGE;

    private String reviewCheckName = "Code review";

    private boolean automergeDependabot = false;

    public AuthMethod auth() {
        return auth;
    }

    public void setAuth(AuthMethod auth) {
        this.auth = auth;
    }

    public String user() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String token() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long appId() {
        return appId;
    }

    public void setAppId(long appId) {
        this.appId = appId;
    }

    public long installationId() {
        return installationId;
    }

    public void setInstallationId(long installationId) {
        this.installationId = installationId;
    }

    public String privateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public boolean keyIsFile() {
        return keyIsFile;
    }

    public String webhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String repository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public List<String> approvedLabels() {
        return approvedLabels;
    }

    public void setApprovedLabels(String approvedLabels) {
        this.approvedLabels = Arrays.asList(approvedLabels.split(","));
    }

    public List<String> changesRequestedLabels() {
        return changesRequestedLabels;
    }

    public void setChangesRequestedLabels(String changesRequestedLabels) {
        this.changesRequestedLabels = Arrays.asList(changesRequestedLabels.split(","));
    }

    public List<String> reviewRequestedLabels() {
        return reviewRequestedLabels;
    }

    public void setReviewRequestedLabels(String reviewRequestedLabels) {
        this.reviewRequestedLabels = Arrays.asList(reviewRequestedLabels.split(","));
    }

    public List<String> commentedLabels() {
        return commentedLabels;
    }

    public void setCommentedLabels(String commentedLabels) {
        this.commentedLabels = Arrays.asList(commentedLabels.split(","));
    }

    public String wipLabel() {
        return wipLabel;
    }

    public void setWipLabel(String wipLabel) {
        this.wipLabel = wipLabel;
    }

    public String reviewDismissMessage() {
        return reviewDismissMessage;
    }

    public void setReviewDismissMessage(String reviewDismissMessage) {
        this.reviewDismissMessage = reviewDismissMessage;
    }

    public String mergeMessage() {
        return mergeMessage;
    }

    public void setMergeMessage(String mergeMessage) {
        this.mergeMessage = mergeMessage;
    }

    public String conflictMessage() {
        return conflictMessage;
    }

    public void setConflictMessage(String conflictMessage) {
        this.conflictMessage = conflictMessage;
    }

    public ApprovalStrategy approvalStrategy() {
        return approvalStrategy;
    }

    public void setApprovalStrategy(ApprovalStrategy approvalStrategy) {
        this.approvalStrategy = approvalStrategy;
    }

    public GHPullRequest.MergeMethod mergeMethod() {
        return mergeMethod;
    }

    public void setMergeMethod(GHPullRequest.MergeMethod mergeMethod) {
        this.mergeMethod = mergeMethod;
    }

    public String reviewCheckName() {
        return reviewCheckName;
    }

    public void setReviewCheckname(String reviewCheckName) {
        this.reviewCheckName = reviewCheckName;
    }

    public boolean automergeDependabot() {
        return automergeDependabot;
    }

    public void setAutomergeDependabot(boolean automergeDependabot) {
        this.automergeDependabot = automergeDependabot;
    }

    public boolean useChecks() {
        return auth() == AuthMethod.APP;
    }

    /**
     * Checks if the given configuration is valid.
     * @return true/false
     */
    public boolean validate() {
        if (auth() == AuthMethod.TOKEN) {
            if (user() == null) {
                LOG.error("Missing user value when using " + AuthMethod.TOKEN + " auth method");
                return false;
            }
            if (token() == null) {
                LOG.error("Missing token value when using " + AuthMethod.TOKEN + " auth method");
                return false;
            }
        } else {
            if (appId() == -1) {
                LOG.error("Missing appId value when using " + AuthMethod.APP + " auth method");
                return false;
            }
            if (privateKey() == null) {
                LOG.error("Missing privateKey value when using " + AuthMethod.APP + " auth method");
                return false;
            }
            if (!new File(privateKey()).exists()) {
                keyIsFile = false;
            }
        }
        return true;
    }
}
