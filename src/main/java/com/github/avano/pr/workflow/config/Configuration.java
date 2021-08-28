package com.github.avano.pr.workflow.config;

import java.util.HashMap;
import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration class containing all the configuration that can be changed via usual means.
 */
@ConfigMapping(prefix = "prw")
public interface Configuration {
    Map<String, String> repositoryConfigFiles = new HashMap<>();
    Map<String, RepositoryConfig> repositoryConfigs = new HashMap<>();

    @WithDefault(".")
    String repositoryConfigDir();

    @WithDefault("repoconfig")
    String repositoryConfigFileExtension();

    default void addRepositoryConfigFile(String file, RepositoryConfig repositoryConfig) {
        repositoryConfigFiles.put(file, repositoryConfig.repository());
        repositoryConfigs.put(repositoryConfig.repository(), repositoryConfig);
    }

    default void deleteRepositoryConfigFile(String file) {
        repositoryConfigs.remove(repositoryConfigFiles.get(file));
        repositoryConfigFiles.remove(file);
    }

    default RepositoryConfig repositoryConfig(String repository) {
        return repositoryConfigs.get(repository);
    }
}
