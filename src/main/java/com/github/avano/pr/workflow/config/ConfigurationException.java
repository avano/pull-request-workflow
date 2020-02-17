package com.github.avano.pr.workflow.config;

/**
 * Thrown when incorrect values are provided in the configuration.
 */
public class ConfigurationException extends RuntimeException {
    public ConfigurationException(String err) {
        super(err);
    }
}
