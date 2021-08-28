package com.github.avano.pr.workflow.handler.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.avano.pr.workflow.config.Constants;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.quarkus.vertx.ConsumeEvent;

@Log
@Priority(0)
@Interceptor
public class LoggingInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingInterceptor.class);

    @AroundInvoke
    Object logInvocation(InvocationContext context) {
        LOG.trace("{}{}", Constants.EVENT_RECEIVED_MESSAGE, context.getMethod().getAnnotation(ConsumeEvent.class).value());
        try {
            return context.proceed();
        } catch (Exception e) {
            LOG.error("Unable to proceed with method invocation", e);
        }
        return null;
    }
}
