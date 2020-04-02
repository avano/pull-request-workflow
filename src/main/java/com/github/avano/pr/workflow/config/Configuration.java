package com.github.avano.pr.workflow.config;

import org.kohsuke.github.GHPullRequest;

import java.io.File;
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
    public Optional<Long> appId;
    public Optional<String> keyFile;

    public String secret;

    public String repository;

    public String[] approvedLabels;
    public String[] changesRequestedLabels;
    public String[] commentedLabels;
    public String[] reviewRequestedLabels;
    public String wipLabel;

    public String reviewDismissMessage;
    public String mergeMessage;
    public String conflictMessage;

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

    public Optional<Long> getAppId() {
        return appId;
    }

    public Optional<String> getKeyFile() {
        return keyFile;
    }

    public String getSecret() {
        return secret;
    }

    public String getRepository() {
        return repository;
    }

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

    public String getConflictMessage(int id) {
        return conflictMessage.replace("<ID>", id + "");
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
        } else {
            if (!getAppId().isPresent()) {
                throw new ConfigurationException("Missing value for config.app-id when using " + AuthMethod.APP + " auth method");
            }
            if (!getKeyFile().isPresent()) {
                throw new ConfigurationException("Missing value for config.key-file when using " + AuthMethod.APP + " auth method");
            }
            final File keyFile = new File(getKeyFile().get());
            if (!keyFile.exists()) {
                throw new ConfigurationException("Key file specified in config.key-file doesn't exist");
            }
            if (!keyFile.canRead()) {
                throw new ConfigurationException("Can't read key file specified in config.key-file");
            }
        }
    }
}
