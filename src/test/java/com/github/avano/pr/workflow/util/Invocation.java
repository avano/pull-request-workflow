package com.github.avano.pr.workflow.util;

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

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }
}
