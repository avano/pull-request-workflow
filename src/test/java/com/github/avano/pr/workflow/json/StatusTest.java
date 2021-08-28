package com.github.avano.pr.workflow.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHCommitState;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.json.Status;
import com.github.avano.pr.workflow.message.BusMessage;
import com.github.avano.pr.workflow.message.CommitStatusMessage;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class StatusTest extends JsonHandlerTest {
    @Inject
    Status status;

    @Test
    public void shouldSendStatusMessageTest() {
        status.handleStatusEvent(jsonBody("statusEvent.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.STATUS_CHANGED);
        BusMessage msg = lastMessage();
        assertThat(msg).isNotNull();
        CommitStatusMessage status = msg.get(CommitStatusMessage.class);
        assertThat(status.getName()).isEqualTo("status-check");
        assertThat(status.getStatus()).isEqualTo(GHCommitState.SUCCESS);
        assertThat(status.getCommit().getSHA1()).isEqualTo("6113728f27ae82c7b1a177c8d03f9e96e0adf246");
    }
}
