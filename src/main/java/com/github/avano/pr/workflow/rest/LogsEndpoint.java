package com.github.avano.pr.workflow.rest;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.util.IOUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import java.nio.file.Paths;

@Path("/logs")
public class LogsEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(LogsEndpoint.class);
    private static final String PATH = ConfigProvider.getConfig().getValue("quarkus.log.file.path", String.class);

    /**
     * Returns the log file content.
     */
    @Path("/")
    @GET
    public Response getLogs() {
        return Response.ok().entity(IOUtils.readFile(Paths.get(PATH))).build();
    }
}
