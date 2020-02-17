package com.github.avano.pr.workflow.mock;

import static org.assertj.core.api.Assertions.fail;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import com.github.avano.pr.workflow.gh.GHClient;

import javax.enterprise.context.ApplicationScoped;

import java.io.IOException;

import io.quarkus.test.Mock;

@Mock
@ApplicationScoped
public class GHclientMock extends GHClient {
    @Override
    public GitHub get() {
        try {
            return new GitHubBuilder().withEndpoint("http://localhost:29999").build();
        } catch (IOException e) {
            fail("Unable to create GitHub instance", e);
        }
        return null;
    }

    @Override
    public String getParsedRepository() {
        return "test/repo";
    }
}
