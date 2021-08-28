package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import org.junit.jupiter.api.Test;

import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.github.GHPullRequest;

import com.github.avano.pr.workflow.handler.ConflictHandler;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.ConflictMessage;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ConflictHandlerTest extends TestParent {
    @Inject
    ConflictHandler conflictHandler;

    @Test
    public void shouldPostCommentWhenCausedConflictTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls?state=open"))
            .willReturn(ok().withBodyFile("merge/conflict/twoMergeable.json")));
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/20"))
            .willReturn(ok().withBodyFile("merge/conflict/20_conflict.json")));
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/21"))
            .willReturn(ok().withBodyFile("merge/conflict/21_ok.json")));
        stubFor(WireMock.post(urlEqualTo("/repos/" + TEST_REPO + "/issues/20/comments"))
            .willReturn(aResponse().withStatus(201).withBody("{}")));

        List<GHPullRequest> pullRequestList = new ArrayList<>();
        pullRequestList.add(loadPullRequest(20));
        pullRequestList.add(loadPullRequest(21));
        ConflictMessage msg = new ConflictMessage(123, pullRequestList);

        conflictHandler.checkForConflict(new BusMessage(client, msg));

        List<LoggedRequest> requests = getRequests(WireMock.postRequestedFor(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+/comments")));
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getAbsoluteUrl()).endsWith("issues/20/comments");
        assertThat(new JSONObject(requests.get(0).getBodyAsString()).getString("body"))
            .isEqualTo(client.getRepositoryConfiguration().conflictMessage().replace("<ID>", 123 + ""));
    }

    @Test
    public void shouldAssignOtherPrToAuthorWhenConflictWasCausedTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls?state=open"))
            .willReturn(ok().withBodyFile("merge/conflict/twoMergeable.json")));
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/20"))
            .willReturn(ok().withBodyFile("merge/conflict/20_conflict.json")));
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/21"))
            .willReturn(ok().withBodyFile("merge/conflict/21_ok.json")));
        stubFor(WireMock.post(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+/comments"))
            .willReturn(aResponse().withStatus(201).withBody("{}")));

        List<GHPullRequest> pullRequestList = new ArrayList<>();
        pullRequestList.add(loadPullRequest(20));
        pullRequestList.add(loadPullRequest(21));
        ConflictMessage msg = new ConflictMessage(123, pullRequestList);

        conflictHandler.checkForConflict(new BusMessage(client, msg));

        List<LoggedRequest> requests = getRequests(WireMock.patchRequestedFor(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+")));
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getAbsoluteUrl()).endsWith("issues/20");
        JSONArray assignees = new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("assignees");
        assertThat(assignees).containsExactly("creator");
    }

    @Test
    public void shouldNotPostCommentWhenEverythingWasOkTest() {
        stubFor(WireMock.get(urlEqualTo("/repos/" + TEST_REPO + "/pulls?state=open"))
            .willReturn(ok().withBodyFile("merge/conflict/twoMergeable.json")));
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/20"))
            .willReturn(ok().withBodyFile("merge/conflict/20_ok.json")));
        stubFor(WireMock.get(urlPathMatching("/repos/" + TEST_REPO + "/pulls/21"))
            .willReturn(ok().withBodyFile("merge/conflict/21_ok.json")));
        stubFor(WireMock.post(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+/comments"))
            .willReturn(aResponse().withStatus(201).withBody("{}")));

        List<GHPullRequest> pullRequestList = new ArrayList<>();
        pullRequestList.add(loadPullRequest(20));
        pullRequestList.add(loadPullRequest(21));
        ConflictMessage msg = new ConflictMessage(123, pullRequestList);

        conflictHandler.checkForConflict(new BusMessage(client, msg));

        List<LoggedRequest> requests = getRequests(WireMock.postRequestedFor(urlPathMatching("/repos/" + TEST_REPO + "/issues/\\d+/comments")));
        assertThat(requests).isEmpty();
    }
}
