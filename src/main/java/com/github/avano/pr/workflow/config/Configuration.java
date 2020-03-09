package com.github.avano.pr.workflow.config;

import org.kohsuke.github.GHPullRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.quarkus.arc.config.ConfigProperties;

/**
 * Configuration class containing all the configuration that can be changed via usual means.
 */
@ConfigProperties(prefix = "config")
public class Configuration {
    public String auth;

    public Optional<String> user;
    public Optional<String> token;

    public String repository;

    public String[] approvedLabels;
    public String[] changesRequestedLabels;
    public String[] commentedLabels;
    public String[] reviewRequestedLabels;
    public String wipLabel;

    public String reviewDismissMessage;
    public String mergeMessage;

    public String approvalStrategy;

    public String mergeMethod;

    public AuthMethod getAuth() {
        return AuthMethod.valueOf(auth.toUpperCase());
    }

    public Optional<String> getUser() {
        return user;
    }

    public Optional<String> getToken() {
        return token;
    }

    public String getRepository() {
        return repository;
    };

    public List<String> getApprovedLabels() {
        return Arrays.asList(approvedLabels);
    }

    public List<String> getChangesRequestedLabels() {
        return Arrays.asList(changesRequestedLabels);
    }

    public List<String> getCommentedLabels() {
        return Arrays.asList(commentedLabels);
    }

    public List<String> getReviewRequestedLabels() {
        return Arrays.asList(reviewRequestedLabels);
    }

    public String getWipLabel() {
        return wipLabel;
    }

    public String getReviewDismissMessage() {
        return reviewDismissMessage;
    }

    public String getMergeMessage() {
        return mergeMessage;
    }

    public GHPullRequest.MergeMethod getMergeMethod() {
        return GHPullRequest.MergeMethod.valueOf(mergeMethod.toUpperCase());
    }

    public ApprovalStrategy getApprovalStrategy() {
        return ApprovalStrategy.valueOf(approvalStrategy.toUpperCase());
    }

    /**
     * Validates the given configuration.
     */
    public void validate() {
        try {
            getApprovalStrategy();
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid value for config.approval-strategy specified: \"" + approvalStrategy + "\" (one of: " +
                Arrays.toString(ApprovalStrategy.values()) + " expected)");
        }

        try {
            getAuth();
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid value for config.auth specified: \"" + auth + "\" (one of: " +
                Arrays.toString(AuthMethod.values()) + " expected)");
        }

        try {
            getMergeMethod();
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid value for config.merge-method specified: \"" + mergeMethod + "\" (one of: " +
                Arrays.toString(GHPullRequest.MergeMethod.values()) + " expected)");
        }

        if (getAuth() == AuthMethod.TOKEN) {
            if (!getUser().isPresent()) {
                throw new ConfigurationException("Missing value for config.user when using " + AuthMethod.TOKEN + " auth method");
            }
            if (!getToken().isPresent()) {
                throw new ConfigurationException("Missing value for config.token when using " + AuthMethod.TOKEN + " auth method");
            }
        }
    }
}
