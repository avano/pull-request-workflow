package com.github.avano.pr.workflow.message;

import org.kohsuke.github.GHUser;

import com.github.avano.pr.workflow.gh.GHClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the bus message with pull request event.
 * <p>
 * Ideally it could be a generic class, but that doesn't work for the consumer methods in vertx, as that calls ClassLoader.loadClass with the
 * generic name, causing ClassNotFoundException
 */
public class BusMessage {
    public static final String REQUESTED_REVIEWER = "requested_reviewer";

    public static final String LABEL = "label";

    public static final String INFO_PR_KEY = "pr";

    private final GHClient client;
    private final Object ghObject;
    private final Map<String, Object> info = new HashMap<>();
    private GHUser sender;

    public BusMessage(GHClient client, Object ghObject) {
        this.client = client;
        this.ghObject = ghObject;
    }

    public GHClient client() {
        return client;
    }

    public <T> T get(Class<T> tClass) {
        return tClass.cast(ghObject);
    }

    public <U> U get(String key, Class<U> uClass) {
        return uClass.cast(info.get(key));
    }

    /**
     * Adds additional information to the event message.
     *
     * @param key map key
     * @param value additional info object
     * @return this
     */
    public BusMessage with(String key, Object value) {
        info.put(key, value);
        return this;
    }

    public GHUser getSender() {
        return sender;
    }

    public BusMessage withSender(GHUser sender) {
        this.sender = sender;
        return this;
    }
}
