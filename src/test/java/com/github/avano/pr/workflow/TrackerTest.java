package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.mock.TrackerMock;
import com.github.avano.pr.workflow.util.CheckState;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TrackerTest extends TestParent {
    @Inject
    TrackerMock tracker;

    @Test
    public void shouldLoadSavedFile() {
        GHPullRequest pr = getPullRequestWithReviewers("reviewer1", "reviewer2");
        tracker.setCheckState(pr, "testCheck", CheckState.FAILED);
        tracker.save();

        tracker.cleanUp();

        tracker.load();
        assertThat(tracker.getTrackers()).hasSize(1);
        assertThat(tracker.getChecks(pr)).hasSize(1);
        assertThat(tracker.getChecks(pr)).containsKey("testCheck");
        assertThat(tracker.getChecks(pr).get("testCheck")).isEqualTo(CheckState.FAILED);
    }
}
