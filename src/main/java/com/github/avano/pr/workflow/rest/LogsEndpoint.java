package com.github.avano.pr.workflow.rest;

import org.apache.commons.io.FileUtils;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;

@Path("/logs")
public class LogsEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(LogsEndpoint.class);
    private static final String PATH = ConfigProvider.getConfig().getValue("quarkus.log.file.path", String.class);

    // todo remove
//    private static final String PATH = "data/pull-request-workflow.log";
    /**
     * Returns the log file content.
     */
    @Path("/")
    @GET
    public Response getLogs() {
        String logs = "";
        try {
            logs = FileUtils.readFileToString(new File(PATH), "UTF-8");
        } catch (IOException e) {
            LOG.error("Unable to read log file: " + e);
        }
        return Response.ok().entity(logs).build();
    }
}
