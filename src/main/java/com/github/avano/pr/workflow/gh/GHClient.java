package com.github.avano.pr.workflow.gh;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GHCommitStatus;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPerson;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.config.Configuration;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Wrapper around GitHub API client + some convenient helper methods.
 */
@ApplicationScoped
public class GHClient {
    private static final Logger LOG = LoggerFactory.getLogger(GHClient.class);

    @Inject
    Configuration config;

    protected GitHub gitHub;

    /**
     * Checks if the client is initialized.
     *
     * @return true/false
     */
    public boolean isInitialized() {
        return gitHub != null;
    }

    /**
     * Inits the client based on the installation id. If the installation id is present, app auth is used, token otherwise.
     *
     * @param installationId installation id parsed from the event, -1 if not present
     */
    public void init(long installationId) {
        try {
            LOG.debug("Initializing GitHub client with installation id {}", installationId);
            if (installationId == -1) {
                gitHub = GitHub.connect(config.getUser().get(), config.getToken().get());
            } else {
                gitHub = new GitHubBuilder().withJwtToken(createJWTToken()).build();
                GHAppInstallation appInstallation = gitHub.getApp().getInstallationById(installationId);
                GHAppInstallationToken appInstallationToken = appInstallation
                    .createToken(appInstallation.getPermissions())
                    .create();
                gitHub = new GitHubBuilder().withAppInstallationToken(appInstallationToken.getToken()).build();
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to create GitHub client instance", e);
        }
    }

    /**
     * Returns the configured repository in form of <org/user>/<repository>.
     *
     * @return repository
     */
    public String getConfiguredRepository() {
        return config.getRepository();
    }

    /**
     * Parses the received GitHub event json into given class.
     *
     * @param event event json received
     * @param clazz class representation of the event
     * @return event class instance
     */
    public <T extends GHEventPayload> T parseEvent(JsonObject event, Class<T> clazz) {
        try {
            return gitHub.parseEventPayload(new StringReader(event.toString()), clazz);
        } catch (IOException e) {
            LOG.error("Unable to parse event payload: " + e);
        }
        return null;
    }

    /**
     * Gets the repository object.
     *
     * @return repository
     */
    public GHRepository getRepository() {
        try {
            return gitHub.getRepository(getConfiguredRepository());
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
        return getRepository().queryPullRequests().state(GHIssueState.OPEN).list().asList().stream().filter(
            pr -> sha.equals(pr.getHead().getSha())
        ).collect(Collectors.toList());
    }

    /**
     * Gets the requested reviewers list for given PR.
     *
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
     *
     * @param branch branch name
     * @return collection of required check names or null if the branch is not protected
     */
    public Collection<String> getRequiredChecks(String branch) {
        try {
            return getRepository().getBranch(branch).getProtection().getRequiredStatusChecks().getContexts();
        } catch (IOException e) {
            if (!e.getMessage().contains("Branch not protected")) {
                LOG.error("Unable to get repository: {}: " + e, getConfiguredRepository());
            }
        }
        return null;
    }

    /**
     * Gets the reviews for given PR. User can review multiple times, so for each user it returns the latest review.
     *
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
     *
     * @param pr pull request instance
     * @return true/false
     */
    public boolean changesRequested(GHPullRequest pr) {
        return getReviews(pr).containsValue(GHPullRequestReviewState.CHANGES_REQUESTED);
    }

    /**
     * Sets the given users as assignees of the given PR.
     *
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
     *
     * @param pr pull request
     * @param users users to assign
     */
    public void setAssignees(GHPullRequest pr, Collection<GHUser> users) {
        setAssignees(pr, users.toArray(new GHUser[0]));
    }

    /**
     * Requests the review from the given users.
     *
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
     *
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

    /**
     * Returns the map of checkname-checkstatus for given pull request's HEAD sha for both check-runs and commit statuses.
     * @param pr pull request
     * @return map of checkname-checkstatus
     */
    public Map<String, String> getChecks(GHPullRequest pr) {
        Map<String, String> checks = new HashMap<>();
        final String sha = pr.getHead().getSha();
        try {
            pr.getRepository().getCheckRuns(sha).forEach(cr -> checks.put(cr.getName(), cr.getConclusion()));
            Map<String, String> statuses = new HashMap<>();
            // Statuses are returned in newest-first order, so revert it and get last state of each status
            List<GHCommitStatus> ghCommitStatuses = pr.getRepository().listCommitStatuses(sha).toList();
            for (int i = ghCommitStatuses.size() - 1; i >= 0; i--) {
                statuses.put(ghCommitStatuses.get(i).getContext(), ghCommitStatuses.get(i).getState().name());
            }
            checks.putAll(statuses);
        } catch (IOException e) {
            LOG.error("Unable to get checkruns or statuses: " + e);
        }
        return checks;
    }

    /**
     * Loads the private key from specified file.
     *
     * @return {@link java.security.PrivateKey} instance
     */
    private PrivateKey loadKey() {
        try {
            Security.addProvider(new BouncyCastleProvider());

            PEMParser pemParser = new PEMParser(new InputStreamReader(new FileInputStream(new File(config.getKeyFile().get()))));
            PEMKeyPair keyPair = (PEMKeyPair) pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
        } catch (Exception e) {
            throw new RuntimeException("Unable to load key: " + config.getKeyFile() + ": " + e);
        }
    }

    /**
     * Creates the JWT token for given application id signed by the specified private key.
     *
     * @return JWT token
     */
    private String createJWTToken() {
        long expiration = 600000L;
        //The JWT signature algorithm we will be using to sign the token
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.RS256;

        long nowMillis = System.currentTimeMillis();

        JwtBuilder builder = Jwts.builder()
            .setIssuedAt(new Date(nowMillis))
            .setIssuer(config.getAppId().get() + "")
            .signWith(loadKey(), signatureAlgorithm);

        builder.setExpiration(new Date(nowMillis + expiration));

        //Builds the JWT and serializes it to a compact, URL-safe string
        return builder.compact();
    }
}
