package com.github.avano.pr.workflow.handler.json;

import org.kohsuke.github.GHEventPayload;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.interceptor.Log;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.CommitStatusMessage;

import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.json.JsonObject;

/**
 * Handles the incoming json related to the
 * <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#status">status</a> events.
 */
public class Status extends JsonEventHandler<GHEventPayload.Status> {
    /**
     * Handles the incoming status event.
     */
    @Log
    @ConsumeEvent(Constants.STATUS_EVENT)
    public void handleStatusEvent(JsonObject jsonEvent) {
        if (!init(jsonEvent, GHEventPayload.Status.class)) {
            return;
        }

        eventBus.publish(Constants.STATUS_CHANGED,
            new BusMessage(client, new CommitStatusMessage(event.getCommit(), event.getState(), event.getContext())));
    }
}
