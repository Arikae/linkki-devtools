package org.linkki.inspector.code;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced metadata for PMO components including hierarchy and instantiation tracking
 */
public class PmoMetaData implements Serializable {
    private final Object pmoInstance;
    private final String pmoClassName;
    private final String propertyName;
    private final ClassLocation classLocation;
    private final PropertyLocation propertyLocation;
    private final SourceLocation instantiationLocation;
    private final List<PmoMetaData> children;
    private PmoMetaData parent;

    public PmoMetaData(Object pmoInstance,
                       String pmoClassName,
                       String propertyName,
                       ClassLocation classLocation,
                       PropertyLocation propertyLocation,
                       SourceLocation instantiationLocation) {
        this.pmoInstance = pmoInstance;
        this.pmoClassName = pmoClassName;
        this.propertyName = propertyName;
        this.classLocation = classLocation;
        this.propertyLocation = propertyLocation;
        this.instantiationLocation = instantiationLocation;
        this.children = new ArrayList<>();
    }

    // Getters
    public Object getPmoInstance() {
        return pmoInstance;
    }

    public String getPmoClassName() {
        return pmoClassName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public ClassLocation getClassLocation() {
        return classLocation;
    }

    public PropertyLocation getPropertyLocation() {
        return propertyLocation;
    }

    public SourceLocation getInstantiationLocation() {
        return instantiationLocation;
    }

    public List<PmoMetaData> getChildren() {
        return children;
    }

    public PmoMetaData getParent() {
        return parent;
    }

    // Hierarchy management
    public void addChild(PmoMetaData child) {
        if (!children.contains(child)) {
            children.add(child);
            child.parent = this;
        }
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean hasParent() {
        return parent != null;
    }

    // Get full hierarchy path
    public String getHierarchyPath() {
        var path = new ArrayList<String>();
        var current = this;
        while (current != null) {
            path.add(0, current.getSimpleClassName() +
                    (current.propertyName != null ? "." + current.propertyName : ""));
            current = current.parent;
        }
        return String.join(" → ", path);
    }

    public String getSimpleClassName() {
        var parts = pmoClassName.split("\\.");
        return parts[parts.length - 1];
    }

    // Convert to JSON-serializable format
    public HierarchyNode toHierarchyNode() {
        var node = new HierarchyNode();
        node.className = getSimpleClassName();
        node.fullClassName = pmoClassName;
        node.propertyName = propertyName;
        node.hasInstantiationLocation = instantiationLocation != null;

        if (classLocation != null) {
            node.classLocation = new LocationInfo(
                    classLocation.getFileName(),
                    classLocation.getLineNumber(),
                    classLocation.getClassName(),
                    null
            );
        }

        if (propertyLocation != null) {
            node.propertyLocation = new LocationInfo(
                    propertyLocation.getFileName(),
                    propertyLocation.getLineNumber(),
                    propertyLocation.getClassName(),
                    propertyLocation.getMethodName()
            );
        }

        if (instantiationLocation != null) {
            node.instantiationLocation = new LocationInfo(
                    instantiationLocation.getFileName(),
                    instantiationLocation.getLineNumber(),
                    instantiationLocation.getClassName(),
                    (instantiationLocation instanceof PropertyLocation) ? ((PropertyLocation) instantiationLocation).getMethodName() : null
            );
        }

        node.children = new ArrayList<>();
        for (var child : children) {
            node.children.add(child.toHierarchyNode());
        }

        return node;
    }

    // Inner classes for JSON serialization
    public static class HierarchyNode implements Serializable {
        public String className;
        public String fullClassName;
        public String propertyName;
        public LocationInfo classLocation;
        public LocationInfo propertyLocation;
        public LocationInfo instantiationLocation;
        public boolean hasInstantiationLocation;
        public List<HierarchyNode> children;
        public String componentId;
    }

    public static class LocationInfo implements Serializable {
        public String fileName;
        public int lineNumber;
        public String className;
        public String methodName;
        public String outermostClassName;

        public LocationInfo(String fileName, int lineNumber, String className, String methodName) {
            this.fileName = fileName;
            this.lineNumber = lineNumber;
            this.className = className;
            this.methodName = methodName;
        }
    }
}