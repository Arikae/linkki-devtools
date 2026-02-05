package org.linkki.inspector;

import org.linkki.core.binding.descriptor.aspect.Aspect;
import org.linkki.core.binding.dispatcher.AbstractPropertyDispatcherDecorator;
import org.linkki.core.binding.dispatcher.PropertyDispatcher;
import org.linkki.core.binding.validation.message.MessageList;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DispatcherChainInspector {

    // Key: System.identityHashCode(pmo) + "#" + property
    // Value: Map<AspectName, InspectionRecord>
    private static final Map<String, Map<String, InspectionRecord>> history = new ConcurrentHashMap<>();
    private static final int MAX_VALUE_LENGTH = 200;

    public static void record(Object pmo, String property, Aspect<?> aspect, Object result, Map<String, Object> breakdown) {
        String key = getKey(pmo, property);

        // Convert breakdown values to strings to be safe for serialization/display
        Map<String, String> safeBreakdown = new LinkedHashMap<>();
        breakdown.forEach((k, v) -> safeBreakdown.put(k, truncate(v)));

        String aspectName = aspect.getName();
        if (aspectName == null || aspectName.isEmpty()) {
            aspectName = "value";
        }

        history.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(aspectName, new InspectionRecord(aspectName, truncate(result), safeBreakdown));
    }

    public static List<InspectionRecord> getHistory(Object pmo, String property) {
        Map<String, InspectionRecord> records = history.get(getKey(pmo, property));
        if (records == null) {
            return Collections.emptyList();
        }
        List<InspectionRecord> list = new ArrayList<>(records.values());
        list.sort(Comparator.comparingLong(r -> r.timestamp));
        return list;
    }

    private static String getKey(Object pmo, String property) {
        return System.identityHashCode(pmo) + "#" + property;
    }

    private static String truncate(Object value) {
        String s = String.valueOf(value);
        if (s.length() > MAX_VALUE_LENGTH) {
            return s.substring(0, MAX_VALUE_LENGTH) + "...";
        }
        return s;
    }

    public static class InspectionRecord {
        public final String aspectName;
        public final String result;
        public final Map<String, String> breakdown;
        public final long timestamp = System.currentTimeMillis();

        public InspectionRecord(String aspectName, String result, Map<String, String> breakdown) {
            this.aspectName = aspectName;
            this.result = result;
            this.breakdown = breakdown;
        }
    }

    /**
     * Traverses the chain, isolates each dispatcher, and records what it would return
     * if it had no fallback.
     */
    public static Map<String, Object> inspectChain(PropertyDispatcher head, Aspect<?> aspect) {
        Map<String, Object> debugResults = new LinkedHashMap<>();

        PropertyDispatcher current = head;

        while (current != null) {
            String name = current.getClass().getSimpleName();

            // 1. Isolate: Swap the next link with TERMINAL
            PropertyDispatcher originalWrapped = getWrapped(current);
            if (originalWrapped != null) {
                setWrapped(current, DUMMY_DISPATCHER);
            }

            // 2. Probe: Call pull() on this isolated node
            try {
                Object result = current.pull(aspect);
                debugResults.put(name, result);
            } catch (Exception e) {
                // in case of exceptions, we just ignore the result
                // debugResults.put(name, "[Error: " + e.getMessage() + "]");
            }

            // 3. Restore: Put the original link back
            if (originalWrapped != null) {
                setWrapped(current, originalWrapped);
            }

            // Move to next
            current = originalWrapped;
        }

        return debugResults;
    }

    // --- Reflection Helpers ---

    private static PropertyDispatcher getWrapped(PropertyDispatcher dispatcher) {
        if (dispatcher instanceof AbstractPropertyDispatcherDecorator) {
            try {
                Field field = AbstractPropertyDispatcherDecorator.class.getDeclaredField("wrappedDispatcher");
                field.setAccessible(true);
                return (PropertyDispatcher) field.get(dispatcher);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static void setWrapped(PropertyDispatcher dispatcher, PropertyDispatcher newValue) {
        if (dispatcher instanceof AbstractPropertyDispatcherDecorator) {
            try {
                Field field = AbstractPropertyDispatcherDecorator.class.getDeclaredField("wrappedDispatcher");
                field.setAccessible(true);
                field.set(dispatcher, newValue);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // A dispatcher that does nothing and replaces the next dispatcher in the chain for debugging purposes
    private static final PropertyDispatcher DUMMY_DISPATCHER = new PropertyDispatcher() {
        @Override
        public <T> T pull(Aspect<T> aspect) {
            // Return null (or aspect default) to signal "I have no value"
            return null;
        }

        // Implement other methods as no-ops or throw unsupported
        @Override
        public String getProperty() {
            return null;
        }

        @Override
        public Object getBoundObject() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return null;
        }

        @Override
        public MessageList getMessages(MessageList ml) {
            return ml;
        }

        @Override
        public <T> void push(Aspect<T> aspect) {
        }

        @Override
        public <T> boolean isPushable(Aspect<T> aspect) {
            return false;
        }
    };
}
