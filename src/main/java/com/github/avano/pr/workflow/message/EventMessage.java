package com.github.avano.pr.workflow.message;

import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the bus message with pull request event.
 */
public class EventMessage {
    public static final String REQUESTED_REVIEWER = "requested.reviewer";
    public static final String JSON_POINTER_REQUESTED_REVIEWER = "/requested_reviewer/login";

    public static final String LABEL = "label";
    public static final String JSON_POINTER_LABEL_NAME = "/label/name";

    public static final String INFO_PR_KEY = "pr";
    public static final String INFO_CHECK_RUN_ACTION = "check.run.action";

    private GHObject ghObject;
    private Map<String, Object> info = new HashMap<>();
    private GHUser sender;

    public EventMessage(GHObject ghObject) {
        this.ghObject = ghObject;
    }

    public <T extends GHObject> T get() {
        return (T) ghObject;
    }

    public <T> T getInfo(String key) {
        return (T) info.get(key);
    }

    /**
     * Adds additional information to the event message.
     * @param key map key
     * @param value additional info object
     * @return this
     */
    public EventMessage withAdditionalInfo(String key, Object value) {
        info.put(key, value);
        return this;
    }

    public GHUser getSender() {
        return sender;
    }

    public EventMessage withSender(GHUser sender) {
        this.sender = sender;
        return this;
    }
}
