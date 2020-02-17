package com.github.avano.pr.workflow.rest;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.gh.GHClient;
import com.github.avano.pr.workflow.message.CommitStatusMessage;
import com.github.avano.pr.workflow.message.EventMessage;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

/**
 * The REST endpoint which consumes the JSON GitHub events.
 */
@Path("/webhook")
public class WebhookEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(WebhookEndpoint.class);

    @Inject
    GHClient client;

    @Inject
    Bus eventBus;

    /**
     * Gets the JSON Event and forwards it to a corresponding method based on the header in the request.
     *
     * @param event JSON GitHub event
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    @POST
    public void get(@HeaderParam("X-GitHub-Event") String eventType, JsonObject event) {
        if (eventType == null) {
            LOG.warn("Missing X-GitHub-Event header, ignoring request");
            return;
        }
        switch (GHEvent.valueOf(eventType.toUpperCase())) {
            case CHECK_RUN:
                handleCheckRun(event);
                break;
            case STATUS:
                handleStatus(event);
                break;
            case PULL_REQUEST_REVIEW:
                handlePullRequestReview(event);
                break;
            case PULL_REQUEST:
                handlePullRequest(event);
                break;
            default:
                LOG.warn("Unsupported event type: {}", eventType);
                break;
        }
    }

    /**
     * Handles the pull request JSON event.
     *
     * @param event JSON GitHub event
     */
    private void handlePullRequest(JsonObject event) {
        final GHEventPayload.PullRequest prGhEvent = client.parseEvent(event, GHEventPayload.PullRequest.class);
        if (prGhEvent == null) {
            return;
        }
        EventMessage prEvent = new EventMessage(prGhEvent.getPullRequest()).withSender(prGhEvent.getSender());

        switch (event.getString("action")) {
            case "opened":
                eventBus.publish(Bus.PR_OPENED, prEvent);
                break;
            case "closed":
                eventBus.publish(Bus.PR_CLOSED, prEvent);
                break;
            case "reopened":
                eventBus.publish(Bus.PR_REOPENED, prEvent);
                break;
            case "review_requested":
                prEvent.withAdditionalInfo(EventMessage.REQUESTED_REVIEWER,
                    event.getValue(EventMessage.JSON_POINTER_REQUESTED_REVIEWER).toString().replaceAll("\"", ""));
                eventBus.publish(Bus.PR_REVIEW_REQUESTED, prEvent);
                break;
            case "review_request_removed":
                prEvent.withAdditionalInfo(EventMessage.REQUESTED_REVIEWER,
                    event.getValue(EventMessage.JSON_POINTER_REQUESTED_REVIEWER).toString().replaceAll("\"", ""));
                eventBus.publish(Bus.PR_REVIEW_REQUEST_REMOVED, prEvent);
                break;
            case "ready_for_review":
                eventBus.publish(Bus.PR_READY_FOR_REVIEW, prEvent);
                break;
            case "synchronize":
                eventBus.publish(Bus.PR_UPDATED, prEvent);
                break;
            case "unlabeled":
                prEvent.withAdditionalInfo(EventMessage.LABEL, event.getValue(EventMessage.JSON_POINTER_LABEL_NAME).toString().replaceAll("\"", ""));
                eventBus.publish(Bus.PR_UNLABELED, prEvent);
                break;
            default:
                LOG.debug("Ignoring pull request action \"{}\"", event.getString("action"));
        }
    }

    /**
     * Handles the pull request review JSON event.
     *
     * @param event JSON GitHub event
     */
    private void handlePullRequestReview(JsonObject event) {
        final GHEventPayload.PullRequestReview reviewGhEvent = client.parseEvent(event, GHEventPayload.PullRequestReview.class);
        if (reviewGhEvent == null) {
            return;
        }
        // Also save PR object, because we can't get to PR from the Review object
        EventMessage reviewEvent = new EventMessage(reviewGhEvent.getReview()).withSender(reviewGhEvent.getSender())
            .withAdditionalInfo(EventMessage.INFO_PR_KEY, reviewGhEvent.getPullRequest());

        if ("submitted".equals(event.getString("action"))) {
            eventBus.publish(Bus.PR_REVIEW_SUBMITTED, reviewEvent);
        } else {
            LOG.debug("Ignoring pull request review action: \"{}\"", event.getString("action"));
        }
    }

    /**
     * Handles the check run JSON event.
     *
     * @param event JSON GitHub event
     */
    private void handleCheckRun(JsonObject event) {
        final GHEventPayload.CheckRun checkRunGhEvent = client.parseEvent(event, GHEventPayload.CheckRun.class);
        if (checkRunGhEvent == null) {
            return;
        }

        EventMessage checkRunEvent = new EventMessage(checkRunGhEvent.getCheckRun());
        switch (event.getString("action")) {
            case "completed":
                eventBus.publish(Bus.CHECK_RUN_FINISHED, checkRunEvent);
                break;
            case "created":
                eventBus.publish(Bus.CHECK_RUN_CREATED, checkRunEvent);
                break;
            case "rerequested":
                // Rerequested also only puts the check run in the in_progress state, so send it to check_run_created destination with additional info
                eventBus.publish(Bus.CHECK_RUN_CREATED, checkRunEvent.withAdditionalInfo(EventMessage.INFO_CHECK_RUN_ACTION, "rerequested"));
                break;
            default:
                LOG.warn("Unknown check run action: \"{}\"", event.getString("action"));
        }
    }

    /**
     * Handles the status JSON event.
     *
     * @param event JSON GitHub event
     */
    private void handleStatus(JsonObject event) {
        final GHEventPayload.Status statusGhEvent = client.parseEvent(event, GHEventPayload.Status.class);
        if (statusGhEvent == null) {
            return;
        }

        // Status doesn't have GHObject representation, so use custom class for it
        eventBus.publish(Bus.STATUS_CHANGED,
            new CommitStatusMessage(statusGhEvent.getCommit(), statusGhEvent.getState(), statusGhEvent.getContext()));
    }
}
