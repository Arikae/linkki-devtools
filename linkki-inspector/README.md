# Linkki Inspector

The Linkki Inspector is a powerful development tool designed to help developers understand and debug the structure of Linkki-based Vaadin applications. It provides a runtime view of the component hierarchy and its binding to Presentation Model Objects (PMOs).

## Features

*   **Hierarchy Visualization**: View the dynamic tree of UI components as they are rendered in the browser.
*   **PMO Binding Analysis**: See exactly which PMO class and property are bound to a specific UI component.
*   **Source Code Location**:
    *   Identify the file and line number where a PMO class is defined.
    *   Locate the getter/method bound to a property.
    *   Trace where a component or PMO was instantiated.
*   **Dispatcher History**: Track property updates and method invocations for debugging data flow.
*   **REST API**: Exposes inspection data via a REST endpoint (`/inspector/location`) for external tools or IDE integration.

## Installation

Add the dependency to your project's `pom.xml`:

```xml
<dependency>
    <groupId>org.linkki-framework</groupId>
    <artifactId>linkki-inspector</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Enabling the Inspector

The inspector is automatically enabled when the application is running in **development mode** (i.e., when `vaadin.productionMode` is false).

It registers a `VaadinServiceInitListener` that injects the necessary client-side scripts and styles into your UI.

### Inspecting Components

1.  Run your Linkki application.
2.  The inspector attaches metadata attributes (like `data-pmo-class`, `data-pmo-property`, `data-component-id`) to the DOM elements.
3.  You can access the inspection data via the browser console or by using a companion browser extension (if available).
4.  The backend exposes an endpoint at `/inspector/location?componentId={id}` which returns detailed JSON metadata about the component.

### Manual Registration (Advanced)

While the inspector automatically hooks into Linkki's binding mechanism, you can manually register components if needed:

```java
// Register a generic component
ComponentInspector.registerGenericComponent(myButton);

// Register a PMO-bound component
ComponentInspector.registerPmoComponent(myPmo, myComponent, "propertyName");
```

## Configuration

The inspector is designed to be zero-config for standard development environments. However, you can control its activation programmatically via `LinkkiInspectorUIInjector`.

```java
// Manually enable for a specific UI
LinkkiInspectorUIInjector.enable(UI.getCurrent());
```

## Architecture

*   **`ComponentInspector`**: Core logic for tracking component-to-PMO mappings.
*   **`HierarchyScanner`**: Traverses the component tree to build a hierarchical representation.
*   **`InspectorEndpoint`**: REST controller providing data to the frontend or external tools.
*   **`SourceCodeParser`**: Helper to extract source code location information (line numbers, file names).
