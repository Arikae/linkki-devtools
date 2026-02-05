package org.linkki.inspector;

import org.linkki.inspector.code.PropertyLocation;
import org.linkki.inspector.code.SourceLocation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint for Component Inspector
 */
@RestController
@RequestMapping("/inspector")
public class InspectorEndpoint {

    @GetMapping("/location")
    public Map<String, Object> getComponentLocation(@RequestParam("componentId") String componentId) {
        Map<String, Object> response = new HashMap<>();

        try {
            var componentOpt = ComponentRegistry.getComponentById(componentId);

            if (componentOpt.isEmpty()) {
                response.put("error", "Component instance not found");
                return response;
            }

            var component = componentOpt.get();
            var metadata = ComponentInspector.getPmoMetaData(component);

            if (metadata == null) {
                response.put("error", "Metadata not found");
                return response;
            }

            response.put("componentId", componentId);
            response.put("pmoClassName", metadata.getClassLocation().getClassName());
            response.put("propertyName", metadata.getPropertyName());
            response.put("pmoInstanceId", String.valueOf(System.identityHashCode(metadata.getPmoInstance())));

            // Add Location Data
            if (metadata.getClassLocation() != null) {
                response.put("classLocation", locationToMap(metadata.getClassLocation()));
            }
            if (metadata.getPropertyLocation() != null) {
                response.put("propertyLocation", locationToMap(metadata.getPropertyLocation()));
            }
            if (metadata.getInstantiationLocation() != null) {
                response.put("instantiationLocation", locationToMap(metadata.getInstantiationLocation()));
                response.put("hasInstantiationLocation", true);
            } else {
                response.put("hasInstantiationLocation", false);
            }

            // DYNAMIC HIERARCHY GENERATION
            // 1. Calculate path from current element up to root
            response.put("hierarchyPath", HierarchyScanner.calculateHierarchyPath(component.getElement()));

            // 2. Build full tree starting from the UI root
            var hierarchyRoot = HierarchyScanner.buildDynamicHierarchy(component.getElement());
            response.put("hierarchy", hierarchyRoot);

            // 3. Dispatcher History
            if (metadata.getPropertyName() != null) {
                var history = DispatcherChainInspector.getHistory(metadata.getPmoInstance(), metadata.getPropertyName());
                response.put("dispatcherHistory", history);
            }

        } catch (Exception e) {
            System.err.println("Inspector endpoint error: " + e.getMessage());
            e.printStackTrace();
            response.put("error", "Failed to get component location: " + e.getMessage());
        }

        return response;
    }

    private Map<String, Object> locationToMap(SourceLocation location) {
        var map = new HashMap<String, Object>();
        map.put("fileName", location.getFileName());
        map.put("lineNumber", location.getLineNumber());
        map.put("className", location.getOutermostClassName());
        if (location instanceof PropertyLocation) {
            map.put("methodName", ((PropertyLocation) location).getMethodName());
        }
        return map;
    }
}