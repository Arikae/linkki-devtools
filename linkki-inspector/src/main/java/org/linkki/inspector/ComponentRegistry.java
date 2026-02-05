package org.linkki.inspector;

import com.vaadin.flow.component.Component;
import org.linkki.inspector.code.PropertyLocation;
import org.linkki.inspector.code.SourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.linkki.inspector.ComponentInspector.DATA_COMPONENT_ID;
import static org.linkki.inspector.ComponentInspector.getOutermostClassName;

public class ComponentRegistry {
    private static final Map<Object, SourceLocation> objectInstanceLocationRegistry = new ConcurrentHashMap<>();
    private static final Map<String, ComponentSourceLocation> componentRegistry = new ConcurrentHashMap<>();

    private ComponentRegistry() {
        // Utility class
    }

    public static void registerComponent(Component component, SourceLocation location) {
        componentRegistry.put(component.getElement().getAttribute(DATA_COMPONENT_ID), new ComponentSourceLocation(component, location));
    }

    public static void registerInstantiationLocation(Object obj, SourceLocation location) {
        objectInstanceLocationRegistry.put(obj, location);
    }

    public static SourceLocation captureLocation() {
        var stack = Thread.currentThread().getStackTrace();

        for (int i = 2; i < stack.length; i++) {
            var element = stack[i];
            var className = element.getClassName();

            if (!className.startsWith("com.vaadin") &&
                    !className.startsWith("java.") &&
                    !className.startsWith("org.linkki.inspector")) {

                return new PropertyLocation(
                        element.getLineNumber(),
                        element.getClassName(),
                        getOutermostClassName(element.getClassName()),
                        null,
                        element.getMethodName()
                );
            }
        }
        return null;
    }

    public static SourceLocation captureInstantiationLocation(Class<?> clazz) {
        var stack = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            var element = stack[i];
            var className = element.getClassName();
            if (!className.equals(clazz.getName())) {
                continue;
            } else {
                element = stack[++i];
            }

            return new PropertyLocation(
                    element.getLineNumber(),
                    element.getClassName(),
                    getOutermostClassName(element.getClassName()),
                    null,
                    element.getMethodName()
            );
        }
        return null;
    }

    public static Optional<Component> getComponentById(String componentId) {
        if (componentRegistry.containsKey(componentId)) {
            return Optional.of(componentRegistry.get(componentId).component);
        } else {
            return Optional.empty();
        }
    }

    public static Optional<SourceLocation> getInstantiationLocationById(Object object) {
        if (objectInstanceLocationRegistry.containsKey(object)) {
            return Optional.of(objectInstanceLocationRegistry.get(object));
        } else {
            return Optional.empty();
        }
    }

    record ComponentSourceLocation(Component component, SourceLocation location) {
    }
}