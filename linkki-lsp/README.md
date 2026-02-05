# Linkki LSP

This project contains the Language Server Protocol (LSP) implementation for the linkki framework, along with client
implementations for IntelliJ IDEA, Eclipse, and VS Code.

## Project Structure

* `lsp-server`: The core LSP server implementation.
* `intellij-lsp`: The IntelliJ IDEA plugin client.
* `eclipse-lsp`: The Eclipse plugin client.
* `vscode-lsp`: The VS Code extension client.

## Building and Running

### Prerequisites

* Java 21 or later
* Maven
* Node.js and npm (for VS Code extension)

### 1. Build the LSP Server

First, you need to build the core LSP server, as all clients depend on it.

```bash
mvn clean install
```

This will generate a shaded JAR file in `lsp-server/target/lsp-server-1.0-SNAPSHOT.jar`.

### 2. IntelliJ IDEA

To run the IntelliJ IDEA plugin:

1. Navigate to the `intellij-lsp` directory.
2. Run the plugin using Gradle. You need to provide the path to the built LSP server JAR.

**Windows:**

```cmd
cd intellij-lsp
gradlew runIde "-PlspPath=../lsp-server/target/lsp-server-1.0-SNAPSHOT.jar"
```

**Linux/Mac:**

```bash
cd intellij-lsp
./gradlew runIde -PlspPath=../lsp-server/target/lsp-server-1.0-SNAPSHOT.jar
```

This will launch a new instance of IntelliJ IDEA with the plugin installed. Open a project with `.java` files to test
the LSP connection.

### 3. Eclipse

To run the Eclipse plugin:

1. Navigate to the `eclipse-lsp` directory.
2. Build the plugin using Maven.

```bash
cd eclipse-lsp
mvn clean verify
```

To test it in a runtime Eclipse instance:

1. Import the `eclipse-lsp` project into your Eclipse IDE (ensure you have PDE installed).
2. Right-click the project -> **Run As** -> **Eclipse Application**.

**Note:** The build process automatically copies the `lsp-server` JAR to `eclipse-lsp/server/server.jar`.

### 4. VS Code

To run the VS Code extension:

1. Navigate to the `vscode-lsp` directory.
2. Install dependencies and package the extension.

```bash
cd vscode-lsp
mvn package
```

This will create a `.vsix` file in `vscode-lsp/target/linkki-lsp-extension.vsix`.

To develop and debug:

1. Open the `vscode-lsp` folder in VS Code.
2. Run `npm install` (if not done by Maven).
3. Select `linkki-lsp-extension.vsix` and press `F5` to launch the Extension Development Host.

**Note:** The build process automatically copies the `lsp-server` JAR to `vscode-lsp/server/server.jar`.
