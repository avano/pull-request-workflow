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
 * <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pull_request_review">pull request review</a>
 * events.
 */
public class Review extends JsonEventHandler<GHEventPayload.PullRequestReview> {
    private static final Logger LOG = LoggerFactory.getLogger(Review.class);

    /**
     * Handles the incoming pull request review event.
     */
    @Log
    @ConsumeEvent(Constants.REVIEW_EVENT)
    public void handleReviewEvent(JsonObject jsonEvent) {
        if (!init(jsonEvent, GHEventPayload.PullRequestReview.class)) {
            return;
        }

        // Also save PR object, because we can't get to PR from the Review object
        BusMessage msg = new BusMessage(client, event.getReview()).withSender(event.getSender())
            .with(BusMessage.INFO_PR_KEY, event.getPullRequest());

        if ("submitted".equals(jsonEvent.getString("action"))) {
            eventBus.publish(Constants.PR_REVIEW_SUBMITTED, msg);
        } else {
            LOG.debug("Ignoring pull request review action: \"{}\"", jsonEvent.getString("action"));
        }
    }
}
