package org.linkki.inspector;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.StateNode;
import com.vaadin.flow.internal.nodefeature.ElementData;
import org.linkki.inspector.code.PmoMetaData;
import org.linkki.inspector.code.PmoMetaData.HierarchyNode;
import org.linkki.inspector.code.PmoMetaData.LocationInfo;
import org.linkki.inspector.code.PropertyLocation;
import org.linkki.inspector.code.SourceLocation;

import java.util.*;
import java.util.stream.Collectors;

import static org.linkki.inspector.ComponentInspector.*;

public class HierarchyScanner {

    public static HierarchyNode buildDynamicHierarchy(Element element) {
        Element root = findAbsoluteRoot(element);

        HierarchyNode syntheticRoot = new HierarchyNode();
        syntheticRoot.className = "UI Root";
        syntheticRoot.fullClassName = "root";
        syntheticRoot.children = findDirectDataChildren(root).stream()
                .map(HierarchyScanner::createNode)
                .filter(HierarchyScanner::isValidNode) // Filter at root level
                .collect(Collectors.toList());
        return syntheticRoot;
    }

    // ... [calculateHierarchyPath, findAbsoluteRoot remain unchanged] ...
    public static String calculateHierarchyPath(Element element) {
        List<String> pathParts = new ArrayList<>();
        Element current = element;

        while (current != null) {
            // Include components that have valid PMO metadata identifiers
            if (current.getAttribute(DATA_COMPONENT_ID) != null && current.getAttribute(DATA_PMO_CLASS) != null) {
                String className = current.getAttribute(DATA_PMO_CLASS);
                if (className != null) {
                    className = className.substring(className.lastIndexOf('.') + 1);
                }
                String property = current.getAttribute(DATA_PMO_PROPERTY);

                String part = className + (property != null && !property.isEmpty() ? "." + property : "");
                pathParts.add(part);
            }
            current = current.getParent();
        }

        Collections.reverse(pathParts);
        return String.join(" → ", pathParts);
    }

    private static Element findAbsoluteRoot(Element start) {
        Element current = start;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    private static boolean isValidNode(HierarchyNode node) {
        return node != null && !"Unknown".equals(node.className);
    }

    private static HierarchyNode createNode(Element element) {
        HierarchyNode node = new HierarchyNode();

        var fullClassName = element.getAttribute(DATA_PMO_CLASS);
        var propertyName = element.getAttribute(DATA_PMO_PROPERTY);
        var componentId = element.getAttribute(DATA_COMPONENT_ID);

        node.fullClassName = fullClassName;
        node.className = fullClassName != null ? fullClassName.substring(fullClassName.lastIndexOf('.') + 1) : "Unknown";
        node.propertyName = propertyName;
        node.componentId = componentId;

        enrichNodeWithLocation(node, componentId);

        var childrenElements = findDirectDataChildren(element);

        if (childrenElements.size() == 1 && isGrid(childrenElements.getFirst())) {
            return createGroupedNode(childrenElements.getFirst());
        }

        node.children = childrenElements.stream()
                .map(HierarchyScanner::createNode)
                .filter(HierarchyScanner::isValidNode) // Filter: remove Unknowns
                .collect(Collectors.toList());

        return node;
    }

    private static HierarchyNode createPmoNode(Element element) {
        HierarchyNode node = new HierarchyNode();

        var component = element.getComponent().get();
        
        var componentId = element.getAttribute(DATA_COMPONENT_ID);
        node.componentId = componentId;

        var metaData = ComponentInspector.getPmoMetaData(component);
        var pmoInstanceInfo = ComponentInspector.getPmoInstanceMetaData(metaData.getPmoInstance());

        enrichNodeWithLocation(node, pmoInstanceInfo);

        List<Element> childrenElements = findDirectDataChildren(element);

        if (childrenElements.size() == 1 && isGrid(childrenElements.getFirst())) {
            return createGroupedNode(childrenElements.getFirst());
        }

        node.children = childrenElements.stream()
                .map(HierarchyScanner::createNode)
                .filter(HierarchyScanner::isValidNode) // Filter: remove Unknowns
                .collect(Collectors.toList());

        return node;
    }

    private static boolean isGrid(Element element) {
        return element.getComponent().orElse(null) instanceof Grid;
    }

    private static HierarchyNode createGroupedNode(Element element) {
        HierarchyNode node = new HierarchyNode();

        var fullClassName = element.getAttribute(DATA_PMO_CLASS);
        var propertyName = element.getAttribute(DATA_PMO_PROPERTY);
        var componentId = element.getAttribute(DATA_COMPONENT_ID);

        node.fullClassName = fullClassName;
        node.className = fullClassName != null ? fullClassName.substring(fullClassName.lastIndexOf('.') + 1) : "Unknown";
        node.propertyName = propertyName;
        node.componentId = componentId;

        enrichNodeWithLocation(node, componentId);

        List<Element> childrenElements = findDirectDataChildren(element);
        var distinctRows = childrenElements.stream().collect(Collectors.groupingBy(HierarchyScanner::extractPmoClassName));
        var groupedByProperties = distinctRows.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().collect(Collectors.groupingBy(HierarchyScanner::extractPmoClassNameAndPropertyName))));

        node.children = distinctRows.keySet().stream()
                .map(row -> {
                    var pmoNode = createPmoNode(distinctRows.get(row).getFirst());
                    pmoNode.children = groupedByProperties.get(row).values().stream()
                            .map(List::getFirst)
                            .map(HierarchyScanner::createNode)
                            .filter(HierarchyScanner::isValidNode) // Filter: remove Unknowns
                            .toList();
                    return pmoNode;
                }).toList();
        return node;
    }

    // ... [extractPmoClassName, extractPmoClassNameAndPropertyName, enrichNodeWithLocation(node, componentId) remain unchanged] ...
    private static String extractPmoClassName(Element element) {
        return element.getAttribute(DATA_PMO_CLASS);
    }

    private static String extractPmoClassNameAndPropertyName(Element element) {
        return element.getAttribute(DATA_PMO_CLASS) + "." + element.getAttribute(DATA_PMO_PROPERTY);
    }

    private static void enrichNodeWithLocation(HierarchyNode node, String componentId) {
        if (componentId == null) return;

        Optional<com.vaadin.flow.component.Component> componentOpt = ComponentRegistry.getComponentById(componentId);
        if (componentOpt.isPresent()) {
            PmoMetaData metadata = ComponentInspector.getPmoMetaData(componentOpt.get());
            enrichNodeWithLocation(node, metadata);
        }
    }

    private static void enrichNodeWithLocation(HierarchyNode node, PmoMetaData metadata) {
        if (metadata != null) {
            // FIXED: If className is still "Unknown" (e.g. Generic Vaadin Component),
            // fallback to the class name in metadata even if SourceLocation is missing.
            if ("Unknown".equals(node.className) && metadata.getPmoClassName() != null) {
                node.fullClassName = metadata.getPmoClassName();
                node.className = node.fullClassName.substring(node.fullClassName.lastIndexOf('.') + 1);
            }

            if (metadata.getClassLocation() != null) {
                node.classLocation = new LocationInfo(
                        metadata.getClassLocation().getFileName(),
                        metadata.getClassLocation().getLineNumber(),
                        metadata.getClassLocation().getClassName(),
                        null
                );
                node.classLocation.outermostClassName = metadata.getClassLocation().getOutermostClassName();
                node.className = metadata.getClassLocation().getClassName();
                node.fullClassName = metadata.getClassLocation().getOutermostClassName();
            }

            if (metadata.getPropertyLocation() != null) {
                node.propertyLocation = new LocationInfo(
                        metadata.getPropertyLocation().getFileName(),
                        metadata.getPropertyLocation().getLineNumber(),
                        metadata.getPropertyLocation().getClassName(),
                        metadata.getPropertyLocation().getMethodName()
                );
            }

            // CHECK: Only add instantiation location if it is useful (not system/spring reflection)
            if (metadata.getInstantiationLocation() != null && isUsefulInstantiation(metadata.getInstantiationLocation())) {
                node.hasInstantiationLocation = true;
                node.instantiationLocation = new LocationInfo(
                        metadata.getInstantiationLocation().getFileName(),
                        metadata.getInstantiationLocation().getLineNumber(),
                        metadata.getInstantiationLocation().getClassName(),
                        (metadata.getInstantiationLocation() instanceof PropertyLocation) ? ((PropertyLocation) metadata.getInstantiationLocation()).getMethodName() : null
                );
            }

        }
    }

    private static boolean isUsefulInstantiation(SourceLocation loc) {
        String cls = loc.getClassName();
        if (cls == null) return false;
        // Filter out low-level reflection and framework internals that don't help the user
        return !cls.startsWith("jdk.internal.") &&
                !cls.startsWith("java.lang.reflect.") &&
                !cls.startsWith("sun.reflect.") &&
                !cls.startsWith("org.springframework.");
    }

    private static List<Element> findDirectDataChildren(Element parent) {
        var results = new LinkedList<Element>();
        var queue = new LinkedList<StateNode>();

        boolean parentIsPmo = parent.getAttribute(DATA_PMO_CLASS) != null;

        parent.getNode().forEachChild(queue::add);

        while (!queue.isEmpty()) {
            var node = queue.poll();
            var foundMatch = false;

            if (node.hasFeature(ElementData.class)) {
                Element current = Element.get(node);

                if (current.getAttribute(DATA_COMPONENT_ID) != null) {
                    boolean nodeIsPmo = current.getAttribute(DATA_PMO_CLASS) != null;

                    if (parentIsPmo) {
                        if (nodeIsPmo) {
                            results.add(current);
                            foundMatch = true;
                        }
                    } else {
                        results.add(current);
                        foundMatch = true;
                    }
                }
            }

            if (!foundMatch) {
                node.forEachChild(queue::add);
            }
        }

        return results;
    }
}