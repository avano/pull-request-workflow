package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.config.RepositoryConfig;
import com.github.avano.pr.workflow.util.IOUtils;
import com.github.avano.pr.workflow.util.Signature;

import javax.inject.Inject;
import javax.json.Json;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

@QuarkusTest
public class WebhookEndpointTest extends TestParent {
    @TestHTTPResource("/webhook")
    private URL url;

    @Inject
    Signature signature;

    @Inject
    Configuration configuration;

    @BeforeEach
    public void setup() {
        super.setup();
        RepositoryConfig rcfg = client.getRepositoryConfiguration();
        rcfg.setRepository("test/repo");
        configuration.addRepositoryConfigFile("test", rcfg);
    }

    private void sendRequest(String header, String content) {
        sendRequest(header, content, null);
    }

    private void sendRequest(String header, String content, Map<String, String> headersOverride) {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("X-GitHub-Event", header);
            con.setRequestProperty("x-hub-signature", signature.compute("testsecret",
                Json.createReader(new StringReader(content)).readObject().toString().getBytes(StandardCharsets.UTF_8)));
            if (headersOverride != null) {
                headersOverride.forEach(con::setRequestProperty);
            }
            con.setDoOutput(true);
            con.setRequestProperty("Content-Length", Integer.toString(content.length()));
            con.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
            assertThat(con.getResponseCode()).isEqualTo(204);
        } catch (IOException e) {
            fail("Unable to send HTTP request", e);
        }
    }

    private String readFile(String fileName) {
        return IOUtils.readFile(Paths.get("src", "test", "resources", "__files", "endpoint", fileName));
    }

    @Test
    public void shouldForwardCheckRunEventTest() {
        sendRequest("check_run", readFile("checkRunCompleted.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.CHECKRUN_EVENT);
        assertThat(busInvocations.get(0).getMessageAs(JsonObject.class)).isEqualTo(new JsonObject(readFile("checkRunCompleted.json")));
    }

    @Test
    public void shouldForwardPullRequestEventTest() {
        sendRequest("pull_request", readFile("prOpened.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.PULL_REQUEST_EVENT);
        assertThat(busInvocations.get(0).getMessageAs(JsonObject.class)).isEqualTo(new JsonObject(readFile("prOpened.json")));
    }

    @Test
    public void shouldForwardPullRequestReviewEventTest() {
        sendRequest("pull_request_review", readFile("reviewSubmitted.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.REVIEW_EVENT);
        assertThat(busInvocations.get(0).getMessageAs(JsonObject.class)).isEqualTo(new JsonObject(readFile("reviewSubmitted.json")));
    }

    @Test
    public void shouldForwardStatusEventTest() {
        sendRequest("status", readFile("statusEvent.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.STATUS_EVENT);
        assertThat(busInvocations.get(0).getMessageAs(JsonObject.class)).isEqualTo(new JsonObject(readFile("statusEvent.json")));
    }

    @Test
    public void shouldIgnoreInvalidSignatureTest() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-hub-signature", "sha1=asdf");
        sendRequest("pull_request", readFile("prUnlabeled.json"), headers);
        assertThat(lastDestination()).isNull();
    }

    @Test
    public void shouldIgnoreUnconfiguredRepositoryTest() {
        sendRequest("check_run", readFile("checkRunCompleted.json").replaceAll("test/repo", "test/unconfigured"));
        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }
}
