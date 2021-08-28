package com.github.avano.pr.workflow.json;

import com.github.avano.pr.workflow.TestParent;
import com.github.avano.pr.workflow.util.IOUtils;

import java.nio.file.Paths;

import io.vertx.core.json.JsonObject;

public class JsonHandlerTest extends TestParent {
    protected JsonObject jsonBody(String fileName) {
        return new JsonObject(IOUtils.readFile(Paths.get("src", "test", "resources", "__files", "endpoint", fileName)));
    }
}
