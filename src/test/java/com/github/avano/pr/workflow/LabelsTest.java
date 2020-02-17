package com.github.avano.pr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.avano.pr.workflow.bus.Bus;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.handler.Label;
import com.github.avano.pr.workflow.message.EventMessage;
import com.github.avano.pr.workflow.message.LabelsMessage;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class LabelsTest extends TestParent {
    @Inject
    Label label;

    @Inject
    Configuration config;

    @Test
    public void shouldAddLabelTest() {
        List<String> labels = new ArrayList<>();
        labels.add("testLabel");
        label.modifyLabels(new LabelsMessage(loadPullRequest(0), labels, null));
        waitFor(() -> !getRequests(PR_PATCH).isEmpty(), 5);
        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        JSONArray labelsJsonArray = new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("labels");
        assertThat(labelsJsonArray).hasSize(2);
        assertThat(labelsJsonArray).containsExactly("bug", "testLabel");
    }

    @Test
    public void shouldRemoveLabelTest() {
        List<String> labels = new ArrayList<>();
        labels.add("bug");
        label.modifyLabels(new LabelsMessage(loadPullRequest(0), null, labels));
        waitFor(() -> !getRequests(PR_PATCH).isEmpty(), 5);
        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        assertThat(new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("labels")).hasSize(0);
    }

    @Test
    public void shouldRemoveAndAddLabelsTest() {
        List<String> labelsToAdd = new ArrayList<>();
        labelsToAdd.add("testLabel");
        List<String> labelsToRemove = new ArrayList<>();
        labelsToRemove.add("bug");
        label.modifyLabels(new LabelsMessage(loadPullRequest(0), labelsToAdd, labelsToRemove));
        waitFor(() -> !getRequests(PR_PATCH).isEmpty(), 5);
        List<LoggedRequest> requests = getRequests(PR_PATCH);
        assertThat(requests).hasSize(1);
        JSONArray labelsJsonArray = new JSONObject(requests.get(0).getBodyAsString()).getJSONArray("labels");
        assertThat(labelsJsonArray).hasSize(1);
        assertThat(labelsJsonArray).containsExactly("testLabel");
    }

    @Test
    public void shouldTryToMergeWhenWipLabelWasRemovedTest() {
        EventMessage message = new EventMessage(loadPullRequest(PULL_REQUEST_ID)).withAdditionalInfo(EventMessage.LABEL, config.getWipLabel());
        label.handlePrUnlabeled(message);

        waitForInvocationsAndAssert(1);
        assertThat(busInvocations.get(0).getDestination()).isEqualTo(Bus.PR_MERGE);
    }

    @Test
    public void shouldntTryToMergeWhenOtherLabelWasRemovedTest() {
        EventMessage message = new EventMessage(loadPullRequest(PULL_REQUEST_ID)).withAdditionalInfo(EventMessage.LABEL, "test");
        label.handlePrUnlabeled(message);

        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }
}
