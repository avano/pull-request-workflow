package com.github.avano.pr.workflow.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;

import com.github.avano.pr.workflow.config.Constants;
import com.github.avano.pr.workflow.handler.json.Review;
import com.github.avano.pr.workflow.message.BusMessage;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ReviewTest extends JsonHandlerTest {
    @Inject
    Review review;

    @Test
    public void shouldSendReviewSubmittedMessageTest() {
        review.handleReviewEvent(jsonBody("reviewSubmitted.json"));
        waitForInvocations(2);
        assertThat(lastDestination()).isEqualTo(Constants.PR_REVIEW_SUBMITTED);
        BusMessage msg = lastMessage();
        assertThat(msg).isNotNull();
        GHPullRequestReview review = msg.get(GHPullRequestReview.class);
        assertThat(review.getState()).isEqualTo(GHPullRequestReviewState.COMMENTED);
        assertThat(msg.getSender().getLogin()).isEqualTo("Codertocat-submitted");
        GHPullRequest pr = msg.get(BusMessage.INFO_PR_KEY, GHPullRequest.class);
        assertThat(pr.getNumber()).isEqualTo(2);
    }

    @Test
    public void shouldNotSendAnyMessageForReviewEditedTest() {
        review.handleReviewEvent(jsonBody("reviewEdited.json"));
        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }

    @Test
    public void shouldNotSendMessageForReviewDismissedTest() {
        review.handleReviewEvent(jsonBody("reviewDismissed.json"));
        waitForInvocations(1);
        assertThat(busInvocations).isEmpty();
    }
}
