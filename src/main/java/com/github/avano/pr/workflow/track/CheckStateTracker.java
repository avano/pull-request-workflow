package com.github.avano.pr.workflow.track;

import com.github.avano.pr.workflow.util.CheckState;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class that holds information about check runs and commit statuses for given PR.
 *
 * It is not completely necessary to hold these values, but we can avoid querying each HEAD commit in the PR
 * for its status checks and check runs. This has one drawback that if we track checkrun/status progress,
 * but we miss (for some reason) the checkrun/status successful finish, it will block the PR merge, but that
 * can be easily worked around by re-triggering the check
 */
public class CheckStateTracker {
    // Map of <check name> : <check status>
    // Because the status checks doesn't have any information about the PR, it just contains the commit id,
    // we need to save the status somewhere
    private Map<String, CheckState> checks = new HashMap<>();

    public Map<String, CheckState> getChecks() {
        return checks;
    }

    public void setCheckState(String name, CheckState state) {
        checks.put(name, state);
    }
}
