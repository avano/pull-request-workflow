package com.github.avano.pr.workflow.bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.vertx.mutiny.core.eventbus.EventBus;

/**
 * Wrapper around Vertx Eventbus + constants for destinations.
 */
@ApplicationScoped
public class Bus {
    private static final Logger LOG = LoggerFactory.getLogger(Bus.class);

    public static final String PR_UPDATED = "pr.updated";

    public static final String PR_REVIEW_REQUESTED = "pr.review.requested";
    public static final String PR_REVIEW_REQUEST_REMOVED = "pr.review.request.removed";

    public static final String PR_READY_FOR_REVIEW = "pr.ready.for.review";

    public static final String PR_REVIEW_SUBMITTED = "pr.review.submitted";

    public static final String PR_MERGE = "pr.merge";

    public static final String PR_UNLABELED = "pr.unlabeled";
    public static final String EDIT_LABELS = "pr.labels";

    public static final String PR_OPENED = "pr.opened";
    public static final String PR_CLOSED = "pr.closed";
    public static final String PR_REOPENED = "pr.reopened";

    public static final String STATUS_CHANGED = "status.changed";

    public static final String CHECK_RUN_CREATED = "run.created";
    public static final String CHECK_RUN_FINISHED = "run.finished";

    public static final String EVENT_PUBLISHED_MESSAGE = "Event published to destination: ";
    public static final String EVENT_RECEIVED_MESSAGE = "Event received from destination: ";

    @Inject
    EventBus eventBus;

    /**
     * Logs the call and calls the publish method.
     * @param destination bus destination
     * @param message bus message
     */
    public void publish(String destination, Object message) {
        LOG.trace(EVENT_PUBLISHED_MESSAGE + destination);
        eventBus.publish(destination, message);
    }
}
