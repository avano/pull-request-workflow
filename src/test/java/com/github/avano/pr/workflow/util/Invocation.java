package com.github.avano.pr.workflow.util;

import com.github.avano.pr.workflow.message.BusMessage;

public class Invocation {
    private String destination;
    private Object message;

    public Invocation(String destination, Object message) {
        this.destination = destination;
        this.message = message;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public BusMessage getMessage() {
        return (BusMessage) message;
    }

    public <T> T getMessageAs(Class<T> tClass) {
        return tClass.cast(message);
    }

    public void setMessage(Object message) {
        this.message = message;
    }
}
