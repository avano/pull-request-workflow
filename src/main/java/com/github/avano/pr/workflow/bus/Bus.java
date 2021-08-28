package com.github.avano.pr.workflow.bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.config.Constants;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.vertx.mutiny.core.eventbus.EventBus;

/**
 * Wrapper around Vertx Eventbus + constants for destinations.
 */
@ApplicationScoped
public class Bus {
    private static final Logger LOG = LoggerFactory.getLogger(Bus.class);

    @Inject
    EventBus eventBus;

    /**
     * Logs the call and calls the publish method.
     * @param destination bus destination
     * @param message bus message
     */
    public void publish(String destination, Object message) {
        LOG.trace(Constants.EVENT_PUBLISHED_MESSAGE + destination);
        eventBus.publish(destination, message);
    }
}
