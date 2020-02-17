package com.github.avano.pr.workflow.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/health")
public class HealthEndpoint {
    /**
     * Returns OK for all requests - used by probes.
     */
    @Path("/")
    @GET
    public Response get() {
        return Response.ok().build();
    }
}
