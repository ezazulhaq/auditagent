package com.cb.auditagent.aspect;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomLoggingInterceptor implements MethodInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(CustomLoggingInterceptor.class);

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String className = invocation.getMethod().getDeclaringClass().getName();
        String methodName = invocation.getMethod().getName();
        int argumentCount = invocation.getArguments().length;
        logger.info("AOP [BEFORE]: Calling method {}.{} with {} argument(s)",
                className, methodName, argumentCount);
        try {
            Object result = invocation.proceed();
            logger.info("AOP [AFTER-RETURNING]: Method {}.{} returned type {}",
                    className, methodName, result == null ? "null" : result.getClass().getSimpleName());
            return result;
        } catch (Throwable t) {
            logger.error("AOP [EXCEPTION]: Method {}.{} threw {}",
                    className, methodName, t.getClass().getSimpleName());
            throw t;
        }
    }
}
