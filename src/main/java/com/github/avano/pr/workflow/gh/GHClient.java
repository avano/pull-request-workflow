package com.github.avano.pr.workflow.gh;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPerson;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.config.Configuration;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wrapper around GitHub API client + some convenient helper methods.
 */
@ApplicationScoped
public class GHClient {
    private static final Logger LOG = LoggerFactory.getLogger(GHClient.class);

    @Inject
    Configuration config;

    private GitHub gitHub;

    // In case of using user+token, we don't know the repository, so parse it from the first request
    private String repository;

    /**
     * Gets the client instance.
     *
     * @return client instance
     */
    public GitHub get() {
        if (gitHub == null) {
            try {
                gitHub = GitHub.connect(config.getUser().get(), config.getToken().get());
            } catch (IOException e) {
                throw new RuntimeException("Unable to create GitHub instance", e);
            }
        }
        return gitHub;
    }

    /**
     * Returns parsed repository in form of <org/user>/<repository>.
     * @return parsed repository
     */
    public String getParsedRepository() {
        return repository;
    }

    /**
     * Parses the received GitHub event json into given class.
     *
     * @param event event json received
     * @param clazz class representation of the event
     * @return event class instance
     */
    public <T extends GHEventPayload> T parseEvent(JsonObject event, Class<T> clazz) {
        if (getParsedRepository() == null) {
            repository = event.getValue("/repository/full_name").toString().replaceAll("\"", "");
            LOG.debug("Parsed repository {}", repository);
        }
        try {
            return get().parseEventPayload(new StringReader(event.toString()), clazz);
        } catch (IOException e) {
            LOG.error("Unable to parse event payload: " + e);
        }
        return null;
    }

    /**
     * Gets the repository object.
     * @return repository
     */
    public GHRepository getRepository() {
        try {
            return get().getRepository(getParsedRepository());
        } catch (IOException e) {
            LOG.error("Unable to get repository: " + e);
        }
        return null;
    }

    /**
     * Gets all opened pull requests where the given SHA is the HEAD of the PR.
     *
     * @param sha sha
     * @return list of pull requests
     */
    public List<GHPullRequest> getPullRequests(String sha) {
        if (getParsedRepository() == null) {
            // We don't have any event yet, so ignore this execution
            return null;
        }
        return getRepository().queryPullRequests().state(GHIssueState.OPEN).list().asList().stream().filter(
            pr -> sha.equals(pr.getHead().getSha())
        ).collect(Collectors.toList());
    }

    /**
     * Gets the requested reviewers list for given PR.
     * @param pr pull request instance
     * @return list of reviewers
     */
    public List<GHUser> getRequestedReviewers(GHPullRequest pr) {
        try {
            return pr.getRequestedReviewers();
        } catch (IOException e) {
            LOG.error("Unable to get reviewers: " + e);
        }
        return new ArrayList<>();
    }

    /**
     * Gets the required check names for given branch if it is protected.
     * @param branch branch name
     * @return collection of required check names or null if the branch is not protected
     */
    public Collection<String> getRequiredChecks(String branch) {
        if (getParsedRepository() == null) {
            // We don't have any event yet, so ignore this execution
            return null;
        }
        try {
            return getRepository().getBranch(branch).getProtection().getRequiredStatusChecks().getContexts();
        } catch (IOException e) {
            if (!e.getMessage().contains("Branch not protected")) {
                LOG.error("Unable to get repository: {}" + e, getParsedRepository());
            }
        }
        return null;
    }

    /**
     * Gets the reviews for given PR. User can review multiple times, so for each user it returns the latest review.
     * @param pr pull request instance
     * @return map with the user login as key and last review state as value
     */
    public Map<GHUser, GHPullRequestReviewState> getReviews(GHPullRequest pr) {
        LOG.trace("PR #{}: Listing reviews", pr.getNumber());
        Map<GHUser, GHPullRequestReviewState> response = new HashMap<>();
        pr.listReviews().asList().forEach(review -> {
            try {
                response.put(review.getUser(), review.getState());
            } catch (IOException e) {
                LOG.error("PR #{}: Unable to get user from review: " + e, pr.getNumber());
            }
        });
        return response;
    }

    /**
     * Returns if the changes were requested on given PR.
     * @param pr pull request instance
     * @return true/false
     */
    public boolean changesRequested(GHPullRequest pr) {
        return getReviews(pr).containsValue(GHPullRequestReviewState.CHANGES_REQUESTED);
    }

    /**
     * Sets the given users as assignees of the given PR.
     * @param pr pull request
     * @param users users to assign
     */
    public void setAssignees(GHPullRequest pr, GHUser... users) {
        String logins = Arrays.stream(users).map(GHPerson::getLogin).collect(Collectors.joining(", "));
        LOG.debug("PR #{}: Setting assignees to: {}", pr.getNumber(), logins);
        try {
            pr.setAssignees(users);
        } catch (IOException e) {
            LOG.error("PR #{}: Unable to add assignees: " + e, pr.getNumber());
        }
    }

    /**
     * Sets the given users as assignees of the given PR.
     * @param pr pull request
     * @param users users to assign
     */
    public void setAssignees(GHPullRequest pr, Collection<GHUser> users) {
        setAssignees(pr, users.toArray(new GHUser[0]));
    }

    /**
     * Requests the review from the given users.
     * @param pr pull request
     * @param users users to request review from
     */
    public void requestReviewers(GHPullRequest pr, List<GHUser> users) {
        String logins = users.stream().map(GHPerson::getLogin).collect(Collectors.joining(", "));
        LOG.debug("PR #{}: Requesting review from reviewers: {}", pr.getNumber(), logins);
        try {
            pr.requestReviewers(users);
        } catch (IOException e) {
            LOG.error("PR #{}: Unable to request reviews: " + e, pr.getNumber());
        }
    }

    /**
     * Gets the author of the PR.
     * @param pr pull request
     * @return GHUser representation of the author
     */
    public GHUser getAuthor(GHPullRequest pr) {
        try {
            return pr.getUser();
        } catch (IOException e) {
            LOG.error("PR #{}: Unable to get author: " + e, pr.getNumber());
        }
        return null;
    }
}
