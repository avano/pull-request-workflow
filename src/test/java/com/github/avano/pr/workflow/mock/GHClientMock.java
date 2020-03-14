package com.github.avano.pr.workflow.mock;

import static org.assertj.core.api.Assertions.fail;

import org.kohsuke.github.GitHubBuilder;

import com.github.avano.pr.workflow.gh.GHClient;

import javax.enterprise.context.ApplicationScoped;

import java.io.IOException;

import io.quarkus.test.Mock;

@Mock
@ApplicationScoped
public class GHClientMock extends GHClient {
    @Override
    public void init(long installationId) {
        try {
            gitHub = new GitHubBuilder().withEndpoint("http://localhost:29999").build();
        } catch (IOException e) {
            fail("Unable to create GitHub client instance", e);
        }
    }

    @Override
    public String getConfiguredRepository() {
        return "test/repo";
    }
}
