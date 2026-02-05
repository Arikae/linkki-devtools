# Linkki DevTools

This repository contains development tools designed to enhance the developer experience when working with
the [linkki framework](https://linkki-framework.org/).
It provides utilities for inspecting running applications and Language Server Protocol (LSP) implementations for better
IDE support.

## Projects

### 1. linkki Inspector (`linkki-inspector`)

The linkki Inspector is a runtime analysis tool that integrates into your linkki-based web application. It allows
developers to inspect the UI component hierarchy and trace the connection between Vaadin components and their
corresponding Presentation Model Objects (PMOs).

**Key Features:**

* **Component Inspection:** Visualize the UI hierarchy directly within the browser.
* **PMO Mapping:** Identify which PMO class and property are bound to a specific UI component.
* **Source Navigation:** Jump directly to the source code definition of the PMO or property (requires IDE integration).
* **Instantiation Tracking:** Trace where a specific PMO or component was instantiated in the code.
* **Dispatcher Chain Analysis:** View the dispatcher chain and what each dispatcher returns.

**Integration:**
The inspector is typically added as a dependency in your Linkki application and enabled via configuration or specific UI
injectors.

### 2. linkki LSP (`linkki-lsp`)

This project provides a Language Server Protocol (LSP) implementation for linkki, enabling advanced code editing
features in various IDEs.

**Modules:**

* **`lsp-server`**: The core Language Server implementation that provides language smarts (completions, diagnostics,
  etc.).
* **`intellij-lsp`**: Client plugin for IntelliJ IDEA.
* **`eclipse-lsp`**: Client plugin for Eclipse IDE.
* **`vscode-lsp`**: Client extension for Visual Studio Code.

**Features:**

* **Templates:** Ready to use code snippets.
* **Diagnostics:** Real-time validation of linkki bindings and PMO structures and quickfixes.
* **Navigation:** Go to definition support for bound properties and methods.
* **Formatting:** Lexicographical sorting of properties files
* **Autocompletion** Proposals within linkki-messages.properties files

## Getting Started

### Prerequisites

* Java 21 (for LSP) / Java 17 (for Inspector)
* Maven

### Building the Projects

To build the entire repository:

```bash
mvn clean install
```

Refer to the individual `README.md` files in `linkki-inspector` and `linkki-lsp` for detailed instructions on running
and configuring each tool.

## License

linkki-devtools is available under the MIT license.
