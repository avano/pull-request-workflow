package com.github.avano.pr.workflow.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.config.RepositoryConfig;
import com.github.avano.pr.workflow.util.Signature;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

import java.nio.charset.StandardCharsets;

/**
 * The REST endpoint which consumes the JSON GitHub events.
 */
@Path("/webhook")
public class WebhookEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(WebhookEndpoint.class);

    @Inject
    Configuration configuration;

    @Inject
    Bus eventBus;

    @Inject
    Signature signature;

    /**
     * Gets the JSON Event and forwards it to a corresponding method based on the header in the request.
     *
     * @param event JSON GitHub event
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    @POST
    public void get(@HeaderParam("x-hub-signature") String actualSignature, @HeaderParam("X-GitHub-Event") String eventType, JsonObject event) {
        if (eventType == null) {
            LOG.warn("Missing X-GitHub-Event header, ignoring request");
            return;
        }

        String repository = event.getJsonObject(Constants.JSON_REPOSITORY).getString(Constants.JSON_REPOSITORY_NAME);
        RepositoryConfig rcfg = configuration.repositoryConfig(repository);
        if (rcfg == null) {
            LOG.warn("Unconfigured repository {}, ignoring request", repository);
            return;
        }

        if (rcfg.webhookSecret() != null && !signature.isValid(rcfg.webhookSecret(), actualSignature,
            event.toString().getBytes(StandardCharsets.UTF_8))) {
            LOG.warn("Signature of the request doesn't match with expected signature, ignoring request");
            return;
        }

        LOG.debug("Received event {}", eventType.toLowerCase());
        eventBus.publish(eventType.toLowerCase(), new io.vertx.core.json.JsonObject(event.toString()));
    }
}
