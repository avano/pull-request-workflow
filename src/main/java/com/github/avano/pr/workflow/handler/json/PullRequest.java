package com.github.avano.pr.workflow.handler.json;

import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;

import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.json.JsonObject;

/**
 * Handles the incoming json related to the
 * <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pull_request">pull request</a> events.
 */
public class PullRequest extends JsonEventHandler<GHEventPayload.PullRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequest.class);

    /**
     * Handles the incoming pull request event.
     */
    @Log
    @ConsumeEvent(Constants.PULL_REQUEST_EVENT)
    public void handlePullRequestEvent(JsonObject jsonEvent) {
        if (!init(jsonEvent, GHEventPayload.PullRequest.class)) {
            return;
        }

        BusMessage msg = new BusMessage(client, event.getPullRequest()).withSender(event.getSender());

        switch (jsonEvent.getString("action")) {
            case "reopened":
                eventBus.publish(Constants.PR_REOPENED, msg);
                break;
            case "review_requested":
                msg.with(BusMessage.REQUESTED_REVIEWER,
                    jsonEvent.getJsonObject(Constants.JSON_REQUESTED_REVIEWER).getString(Constants.JSON_REQUESTED_REVIEWER_LOGIN));
                eventBus.publish(Constants.PR_REVIEW_REQUESTED, msg);
                break;
            case "review_request_removed":
                msg.with(BusMessage.REQUESTED_REVIEWER,
                    jsonEvent.getJsonObject(Constants.JSON_REQUESTED_REVIEWER).getString(Constants.JSON_REQUESTED_REVIEWER_LOGIN));
                eventBus.publish(Constants.PR_REVIEW_REQUEST_REMOVED, msg);
                break;
            case "ready_for_review":
                eventBus.publish(Constants.PR_READY_FOR_REVIEW, msg);
                break;
            case "synchronize":
                eventBus.publish(Constants.PR_UPDATED, msg);
                break;
            case "unlabeled":
                msg.with(BusMessage.LABEL, jsonEvent.getJsonObject(Constants.JSON_LABEL).getString(Constants.JSON_LABEL_NAME));
                eventBus.publish(Constants.PR_UNLABELED, msg);
                break;
            default:
                LOG.debug("Ignoring pull request action \"{}\"", jsonEvent.getString("action"));
                break;
        }
    }
}
