package com.github.avano.pr.workflow.handler.json;

import org.kohsuke.github.GHEventPayload;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.gh.GHClient;

import javax.inject.Inject;
import javax.inject.Provider;

import io.vertx.core.json.JsonObject;

/**
 * Base class for all json event handlers.
 * @param <E> event payload type
 */
public class JsonEventHandler<E extends GHEventPayload> {
    protected E event;
    protected GHClient client;

    @Inject
    Bus eventBus;

    @Inject
    Provider<GHClient> clientProvider;

    /**
     * Inits the client and if a configuration for given repository exists, parses the payload into its class.
     * @param event json event
     * @param eventClass class to parse the event to
     * @return true if successful, false otherwise
     */
    protected boolean init(JsonObject event, Class<E> eventClass) {
        final GHClient client = clientProvider.get();
        final String repository = event.getJsonObject(Constants.JSON_REPOSITORY).getString(Constants.JSON_REPOSITORY_NAME);
        if (!client.init(repository)) {
            return false;
        } else {
            this.client = client;
            this.event = client.parseEvent(event, eventClass);
            if (this.event == null) {
                return false;
            }
        }
        return true;
    }
}
