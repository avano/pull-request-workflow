package com.github.avano.pr.workflow.app;

import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.track.Tracker;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

/**
 * Observes startup and shutdown events.
 */
@ApplicationScoped
public class AppLifecycle {
    @Inject
    Tracker tracker;

    @Inject
    Configuration config;

    void onStart(@Observes StartupEvent ev) {
        config.validate();
        tracker.load();
    }

    void onStop(@Observes ShutdownEvent ev) {
        tracker.save();
    }
}
