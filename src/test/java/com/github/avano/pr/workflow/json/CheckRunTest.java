package com.github.avano.pr.workflow.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHCheckRun;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.json.CheckRun;
import com.github.avano.pr.workflow.message.BusMessage;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class CheckRunTest extends JsonHandlerTest {
    @Inject
    CheckRun checkRun;

    @Test
    public void shouldIgnoreCheckRunCreatedTest() {
        checkRun.handleCheckRunEvent(jsonBody("checkRunCreated.json"));
        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }

    @Test
    public void shouldSendCheckRunCompletedMessageTest() {
        checkRun.handleCheckRunEvent(jsonBody("checkRunCompleted.json"));
        waitForInvocations(2);
        assertThat(busInvocations).hasSize(1);
        assertThat(lastDestination()).isEqualTo(Constants.CHECK_RUN_FINISHED);
        BusMessage msg = lastMessage();
        assertThat(msg).isNotNull();
        GHCheckRun checkRun = msg.get(GHCheckRun.class);
        assertThat(checkRun.getName()).isEqualTo("Octocoders-linter-completed");
    }
}
