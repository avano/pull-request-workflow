package com.github.avano.pr.workflow.handler.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.bus.Bus;

import javax.inject.Inject;

public class BaseHandler {
    protected static final Logger LOG = LoggerFactory.getLogger(BaseHandler.class);

    @Inject
    protected Bus eventBus;
}
