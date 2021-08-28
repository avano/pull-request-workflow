package com.github.avano.pr.workflow.mock;

import static org.junit.jupiter.api.Assertions.fail;

import org.kohsuke.github.GitHubBuilder;

import com.github.avano.pr.workflow.config.RepositoryConfig;
import com.github.avano.pr.workflow.gh.GHClient;

import javax.enterprise.context.ApplicationScoped;

import java.io.IOException;

import io.quarkus.test.Mock;

@Mock
@ApplicationScoped
public class GHClientMock extends GHClient {
    @Override
    public boolean init(String repository) {
        this.rcfg = new RepositoryConfig();
        rcfg.setRepository(repository);

        rcfg.setApprovedLabels("approved");
        rcfg.setCommentedLabels("commented");
        rcfg.setChangesRequestedLabels("changes-requested");
        rcfg.setReviewRequestedLabels("review-requested");
        rcfg.setWipLabel("WIP");

        rcfg.setConflictMessage("<ID> conflict");
        rcfg.setReviewDismissMessage("Dismiss");

        rcfg.setWebhookSecret("testsecret");
        try {
            gitHub = new GitHubBuilder().withEndpoint("http://localhost:29999").build();
        } catch (IOException e) {
            fail("Unable to create GitHub client instance", e);
        }
        return true;
    }
}
