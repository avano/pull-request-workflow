package com.github.avano.pr.workflow.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.config.RepositoryConfig;
import com.github.avano.pr.workflow.util.IOUtils;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

/**
 * Observes startup and shutdown events.
 */
@ApplicationScoped
public class AppLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(AppLifecycle.class);
    private static final ExecutorService es = Executors.newFixedThreadPool(1);
    private ObjectMapper mapper;

    @Inject
    Configuration configuration;

    void onStart(@Observes StartupEvent ev) {
        mapper = new JavaPropsMapper();
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

        scanConfigDir();
        watchConfigDir();
    }

    void onStop(@Observes ShutdownEvent ev) {
        es.shutdown();
    }

    /**
     * Lists config files (files ending with given repositoryConfigFileExtension) in the given directory.
     *
     * @param directory directory where the config files should be present
     * @return list of paths
     */
    private List<Path> configFiles(File directory) {
        return Arrays.stream(Objects.requireNonNull(directory.list((dir, name) -> name.endsWith(configuration.repositoryConfigFileExtension()))))
            .map(p -> Paths.get(directory.getAbsolutePath(), p)).collect(Collectors.toList());
    }

    /**
     * Creates a repository config object from given file.
     *
     * @param file file
     */
    private void createRepositoryConfig(Path file) {
        try {
            LOG.debug("Processing repository config file {}", file);
            RepositoryConfig rcfg = mapper.readValue(IOUtils.readFile(file), RepositoryConfig.class);
            if (rcfg.validate()) {
                String action = configuration.repositoryConfig(rcfg.repository()) == null ? "Created" : "Updated";
                configuration.addRepositoryConfigFile(file.toAbsolutePath().toString(), rcfg);
                LOG.info("{} repository config for repository {}", action, rcfg.repository());
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Unable to parse {} - {}", file.toAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Scans the given config dir for repository configuration files.
     */
    private void scanConfigDir() {
        File configDir = new File(configuration.repositoryConfigDir());
        if (!configDir.exists()) {
            LOG.debug("Configuration directory {} doesn't exist, creating", configuration.repositoryConfigDir());
            configDir.mkdir();
        }
        LOG.debug("Scanning folder {} for .{} files", configuration.repositoryConfigDir(), configuration.repositoryConfigFileExtension());
        for (Path configFilePath : configFiles(configDir)) {
            createRepositoryConfig(configFilePath);
        }
    }

    /**
     * Creates a file watcher for the repository configuration dir to dynamically add/remove/change repository configuration.
     */
    private void watchConfigDir() {
        LOG.debug("Watching folder {} for file changes", configuration.repositoryConfigDir());
        es.submit(() -> {
            WatchService watchService;
            try {
                watchService = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                LOG.error("Unable to create new watch service", e);
                return;
            }

            try {
                Paths.get(configuration.repositoryConfigDir())
                    .register(watchService, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            } catch (IOException e) {
                LOG.error("Unable to watch " + configuration.repositoryConfigDir(), e);
                return;
            }

            WatchKey key;
            try {
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        LOG.debug("Event: {}, file: {}", event.kind(), event.context());
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            if (event.context().toString().endsWith("." + configuration.repositoryConfigFileExtension())) {
                                createRepositoryConfig(Paths.get(configuration.repositoryConfigDir(), event.context().toString()));
                            }
                        }
                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            if (event.context().toString().endsWith("." + configuration.repositoryConfigFileExtension())) {
                                configuration.deleteRepositoryConfigFile(event.context().toString());
                                LOG.info("Deleted repository config for file {}", event.context());
                            }
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                LOG.warn("Watch thread interrupted", e);
            }
        });
    }
}
