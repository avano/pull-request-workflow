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
 * <a href="https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#check_run">check run</a> events.
 */
public class CheckRun extends JsonEventHandler<GHEventPayload.CheckRun> {
    private static final Logger LOG = LoggerFactory.getLogger(CheckRun.class);

    /**
     * Handles the incoming check run event.
     */
    @Log
    @ConsumeEvent(Constants.CHECKRUN_EVENT)
    public void handleCheckRunEvent(JsonObject jsonEvent) {
        if (!init(jsonEvent, GHEventPayload.CheckRun.class)) {
            return;
        }

        if ("completed".equals(jsonEvent.getString("action"))) {
            eventBus.publish(Constants.CHECK_RUN_FINISHED, new BusMessage(client, event.getCheckRun()));
        } else {
            LOG.debug("Ignoring check run action: \"{}\"", jsonEvent.getString("action"));
        }
    }
}
