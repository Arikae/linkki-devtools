package org.linkki.inspector; // Adjust package to your project

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.linkki.core.binding.descriptor.aspect.Aspect;
import org.linkki.core.binding.dispatcher.PropertyDispatcher;
import org.springframework.stereotype.Component;

import java.util.Map;

@org.aspectj.lang.annotation.Aspect
@Component
public class DispatcherChainInterceptor {

    // Guard to prevent the aspect from intercepting the calls we make during inspection
    private static final ThreadLocal<Boolean> IS_ANALYZING = ThreadLocal.withInitial(() -> false);

    @Around("execution(* org.linkki.core.binding.dispatcher.PropertyDispatcher.pull(..)) && args(aspect)")
    public Object debugDispatcherChain(ProceedingJoinPoint joinPoint, Aspect<?> aspect) throws Throwable {

        // 1. If we are already analyzing (recursion guard), just execute normally.
        if (IS_ANALYZING.get()) {
            return joinPoint.proceed();
        }

        try {
            IS_ANALYZING.set(true);

            // 2. Execute the REAL chain first to get the actual result
            Object actualResult = joinPoint.proceed();

            // 3. Perform the analysis
            PropertyDispatcher head = (PropertyDispatcher) joinPoint.getThis();

            // Only debug if this is a "real" call we care about (optional filters can go here)
            if (shouldDebug(head, aspect)) {
                Map<String, Object> chainAnalysis = DispatcherChainInspector.inspectChain(head, aspect);

                // Store the result for UI retrieval
                DispatcherChainInspector.record(
                        head.getBoundObject(),
                        head.getProperty(),
                        aspect,
                        actualResult,
                        chainAnalysis
                );

                // Optional: Keep console logging for now
                // printDebugReport(head, aspect, actualResult, chainAnalysis);
            }

            return actualResult;

        } finally {
            IS_ANALYZING.set(false);
        }
    }

    private boolean shouldDebug(PropertyDispatcher head, Aspect aspect) {
        // Optional: Filter to reduce noise.
        // E.g., only debug dispatchers for specific properties or specific aspects
        return true;
    }

    private void printDebugReport(PropertyDispatcher head, Aspect aspect, Object finalResult, Map<String, Object> analysis) {
        System.out.println("=== Dispatcher Chain Analysis: " + head.getProperty() + " (" + aspect.getName() + ") ===");
        System.out.println("Final Result: " + finalResult);
        System.out.println("Chain Breakdown (Isolated):");

        analysis.forEach((className, value) -> {
            boolean isWinner = (value != null && value.equals(finalResult))
                    || (value == null && finalResult == null);

            String marker = isWinner ? " [*] " : "     ";
            System.out.println(marker + className + " -> " + value);
        });
        System.out.println("=================================================");
    }
}