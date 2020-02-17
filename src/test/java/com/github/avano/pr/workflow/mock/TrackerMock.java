package com.github.avano.pr.workflow.mock;

import com.github.avano.pr.workflow.track.CheckStateTracker;
import com.github.avano.pr.workflow.track.Tracker;

import javax.enterprise.context.ApplicationScoped;

import java.util.Map;

import io.quarkus.test.Mock;

@Mock
@ApplicationScoped
public class TrackerMock extends Tracker {
    public Map<String, Map<Integer, CheckStateTracker>> getTrackers() {
        return trackers;
    }

    public void cleanUp() {
        trackers.clear();
    }
}
