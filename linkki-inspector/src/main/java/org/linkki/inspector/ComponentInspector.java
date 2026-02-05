package org.linkki.inspector;

import com.vaadin.flow.component.Component;
import org.linkki.inspector.code.ClassLocation;
import org.linkki.inspector.code.PmoMetaData;
import org.linkki.inspector.code.PropertyLocation;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced inspector integration for Linkki framework.
 */
public class ComponentInspector {

    // Maps Component -> PMO metadata
    private static final Map<Component, PmoMetaData> componentToPmoMap = new ConcurrentHashMap<>();

    // Maps PMO instance -> metadata
    private static final Map<Object, PmoMetaData> pmoInstanceMap = new ConcurrentHashMap<>();

    public static final String DATA_COMPONENT_ID = "data-component-id";
    public static final String DATA_PMO_CLASS = "data-pmo-class";
    public static final String DATA_PMO_PROPERTY = "data-pmo-property";
    public static final String DATA_PMO_INSTANCE_ID = "data-pmo-instance-id";

    /**
     * Register a generic Vaadin component that isn't necessarily bound to a PMO.
     * <p>
     * If the component is later bound to a PMO via {@link #registerPmoComponent},
     * that registration will overwrite this one, satisfying the "PMO wins" rule.
     */
    public static void registerGenericComponent(Component component) {
        if (!LinkkiInspectorUIInjector.isEnabled()) return;

        // 1. Capture Instantiation Location (Where new Button() was called)
        // We use the component class itself to find where it was instantiated
        var instantiationLocation = ComponentRegistry.captureInstantiationLocation(component.getClass());

        // 2. Capture Class Location (Only for custom classes, not standard Vaadin/JDK)
        ClassLocation classLocation = null;
        if (!isSystemClass(component.getClass())) {
            var loc = findPmoClassLocation(component.getClass());
            classLocation = (ClassLocation) SourceCodeParser.enhanceLocation(loc);
        }

        // 3. Create Metadata
        // For generic components, the "PMO Instance" is the component itself
        // We leave propertyName null to indicate it's not a bound property
        var metaData = new PmoMetaData(
                component,
                component.getClass().getName(),
                null,
                classLocation,
                null,
                instantiationLocation
        );

        componentToPmoMap.put(component, metaData);

        // 4. Set DOM attributes
        // Use a distinct prefix 'comp-' (vs 'pmo-') to differentiate, though ComponentRegistry handles both
        var componentId = "comp-" + System.identityHashCode(component);
        component.getElement().setAttribute(DATA_COMPONENT_ID, componentId);

        // CHANGED: We do NOT set data-pmo-class or properties for generic components anymore.
        // This ensures they are transparent to the HierarchyScanner.

        // 5. Register with registry
        // If we have a class location (custom component), use it. Otherwise, use instantiation (where it was added).
        ComponentRegistry.registerComponent(component, classLocation != null ? classLocation : instantiationLocation);
    }

    /**
     * Register a PMO with its generated component.
     * This overwrites any generic registration for the same component.
     */
    public static void registerPmoComponent(Object pmo, Component component, String propertyName) {
        if (!LinkkiInspectorUIInjector.isEnabled()) return;

        // Capture where the PMO was instantiated
        var instantiationLocation = ComponentRegistry.getInstantiationLocationById(pmo);

        // Capture where the PMO class is defined
        var classLocation = findPmoClassLocation(pmo.getClass());
        classLocation = (ClassLocation) SourceCodeParser.enhanceLocation(classLocation);

        // Get or create metadata for this PMO instance
        var finalClassLocation = classLocation;
        // We only track the base PMO metadata once per instance
        pmoInstanceMap.computeIfAbsent(pmo, p ->
                new PmoMetaData(pmo, pmo.getClass().getName(), null, finalClassLocation, null, instantiationLocation.orElse(null))
        );

        // Find the property location with enhanced line numbers
        var propertyLocation = findPropertyLocation(pmo, propertyName);

        // Create component-specific metadata and register it
        var componentMetadata = new PmoMetaData(
                pmo,
                pmo.getClass().getName(),
                propertyName,
                finalClassLocation,
                propertyLocation,
                instantiationLocation.orElse(null)
        );

        componentToPmoMap.put(component, componentMetadata);

        // Set identifiable attributes on component so HierarchyScanner can find them in the DOM
        var componentId = generatePmoComponentId(pmo, propertyName);
        component.getElement().setAttribute(DATA_COMPONENT_ID, componentId);
        component.getElement().setAttribute(DATA_PMO_CLASS, pmo.getClass().getCanonicalName());
        component.getElement().setAttribute(DATA_PMO_PROPERTY, propertyName != null ? propertyName : "");
        component.getElement().setAttribute(DATA_PMO_INSTANCE_ID, String.valueOf(System.identityHashCode(pmo)));

        // Register with base inspector
        ComponentRegistry.registerComponent(component, propertyLocation);
    }

    private static ClassLocation findPmoClassLocation(Class<?> pmoClass) {
        String className = pmoClass.getName();
        String outermostClassName = getOutermostClassName(className);

        return new ClassLocation(
                1,
                className,
                outermostClassName
        );
    }

    protected static String getOutermostClassName(String fullClassName) {
        return fullClassName.split("\\$")[0];
    }

    public static PropertyLocation findPropertyLocation(Object pmo, String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) {
            return null;
        }

        var pmoClass = pmo.getClass();
        var getterName = "get" + capitalize(propertyName);
        try {
            var getterMethod = pmoClass.getDeclaredMethod(getterName);
            var loc = extractMethodLocation(getterMethod, propertyName);
            return (PropertyLocation) SourceCodeParser.enhanceLocation(loc);
        } catch (NoSuchMethodException e) {
            try {
                var isMethod = pmoClass.getDeclaredMethod("is" + capitalize(propertyName));
                var loc = extractMethodLocation(isMethod, propertyName);
                return (PropertyLocation) SourceCodeParser.enhanceLocation(loc);
            } catch (NoSuchMethodException ex) {
                try {
                    var modelBindingMethod = pmoClass.getDeclaredMethod(propertyName);
                    var loc = extractMethodLocation(modelBindingMethod, propertyName);
                    return (PropertyLocation) SourceCodeParser.enhanceLocation(loc);
                } catch (NoSuchMethodException exc) {
                    return null;
                }
            }
        }
    }

    private static PropertyLocation extractMethodLocation(Method method, String propertyName) {
        return new PropertyLocation(
                1,
                method.getDeclaringClass().getName(),
                getOutermostClassName(method.getDeclaringClass().getName()),
                propertyName,
                method.getName()
        );
    }

    private static String generatePmoComponentId(Object pmo, String propertyName) {
        var propPart = propertyName != null ? propertyName : "root";
        return "pmo-" + pmo.getClass().getSimpleName() + "-" + propPart + "-" + System.identityHashCode(pmo);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static PmoMetaData getPmoMetaData(Component component) {
        return componentToPmoMap.get(component);
    }

    public static PmoMetaData getPmoInstanceMetaData(Object pmo) {
        return pmoInstanceMap.get(pmo);
    }

    /**
     * Determines if a class is a system/library class (Vaadin, JDK, Spring, etc.)
     * that we typically cannot or do not want to parse source code for.
     */
    private static boolean isSystemClass(Class<?> clazz) {
        String name = clazz.getName();
        return name.startsWith("com.vaadin") ||
                name.startsWith("java.") ||
                name.startsWith("javax.") ||
                name.startsWith("sun.") ||
                name.startsWith("jdk.") ||
                name.startsWith("org.springframework") ||
                name.startsWith("org.linkki.core") ||
                name.startsWith("org.linkki.framework");
    }
}