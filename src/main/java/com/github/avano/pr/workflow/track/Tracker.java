package com.github.avano.pr.workflow.track;

import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.avano.pr.workflow.util.CheckState;

import javax.enterprise.context.ApplicationScoped;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tracks information about pull requests.
 */
@ApplicationScoped
public class Tracker implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(Tracker.class);
    private static final String DATA_DIR = "data";
    private static final String SERIALIZED_FILE = "tracker.json";

    private ObjectMapper om = new ObjectMapper();

    protected Map<String, Map<Integer, CheckStateTracker>> trackers = new HashMap<>();

    /**
     * Starts tracking a new PR.
     * @param pr pull request instance
     */
    public void init(GHPullRequest pr) {
        trackers.computeIfAbsent(pr.getRepository().getFullName(), v -> {
            LOG.debug("PR #{}: Started tracking repository {}", pr.getNumber(), pr.getRepository().getFullName());
            return new HashMap<>();
        });
        trackers.get(pr.getRepository().getFullName()).computeIfAbsent(pr.getNumber(), v -> {
            LOG.debug("PR #{}: Started tracking", pr.getNumber());
            return new CheckStateTracker();
        });
    }

    /**
     * Stops tracking a PR.
     * @param pr pull request instance
     */
    public void remove(GHPullRequest pr) {
        trackers.get(pr.getRepository().getFullName()).remove(pr.getNumber());
        LOG.debug("Removed tracking of PR #{}", pr.getNumber());
    }

    /**
     * Tracks a check state for a given PR.
     * @param pr pull request instance
     * @param checkName check name
     * @param state check state
     */
    public void setCheckState(GHPullRequest pr, String checkName, CheckState state) {
        init(pr);
        trackers.get(pr.getRepository().getFullName()).get(pr.getNumber()).setCheckState(checkName, state);
        LOG.debug("PR #{}: Tracked check states: {}", pr.getNumber(),
            getChecks(pr).entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ", "[", "]")));
    }

    /**
     * Gets the tracked check states for a given PR.
     * @param pr pull request instance
     * @return map of tracked states
     */
    public Map<String, CheckState> getChecks(GHPullRequest pr) {
        init(pr);
        return Collections.unmodifiableMap(trackers.get(pr.getRepository().getFullName()).get(pr.getNumber()).getChecks());
    }

    /**
     * Clears the tracked check states for a given PR.
     * @param pr pull request instance
     */
    public void clearChecks(GHPullRequest pr) {
        init(pr);
        trackers.get(pr.getRepository().getFullName()).get(pr.getNumber()).getChecks().clear();
        LOG.debug("PR #{}: Cleared check states", pr.getNumber());
    }

    /**
     * Saves the tracker state to file.
     */
    public void save() {
        try {
            File f = new File(DATA_DIR);
            if (!f.exists()) {
                f.mkdirs();
            }
            f = Paths.get(DATA_DIR, SERIALIZED_FILE).toFile();
            om.writeValue(f, trackers);
            LOG.debug("Serialized trackers map to file: " + f.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Unable to serialize trackers map: " + e);
        }
    }

    /**
     * Loads the tracker state from file.
     */
    public void load() {
        File f = Paths.get(DATA_DIR, SERIALIZED_FILE).toFile();
        if (f.exists()) {
            try {
                trackers = om.readValue(f, new TypeReference<Map<String, Map<Integer, CheckStateTracker>>>() {});
                LOG.info("Loaded trackers map from file: " + f.getAbsolutePath());
            } catch (IOException e) {
                LOG.error("Unable to load trackers map: " + e);
            }
        } else {
            LOG.debug("Trackers map file doesn't exist, not loading");
        }
    }
}
