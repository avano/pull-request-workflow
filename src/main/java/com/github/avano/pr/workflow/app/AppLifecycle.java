package com.github.avano.pr.workflow.app;

import com.github.avano.pr.workflow.config.Configuration;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import io.quarkus.runtime.StartupEvent;

/**
 * Observes startup and shutdown events.
 */
@ApplicationScoped
public class AppLifecycle {
    @Inject
    Configuration config;

    void onStart(@Observes StartupEvent ev) {
        config.validate();
    }
}
