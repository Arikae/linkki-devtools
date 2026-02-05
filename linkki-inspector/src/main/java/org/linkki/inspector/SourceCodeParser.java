package org.linkki.inspector;

import org.linkki.inspector.code.ClassLocation;
import org.linkki.inspector.code.PropertyLocation;
import org.linkki.inspector.code.SourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.linkki.inspector.ComponentInspector.getOutermostClassName;

/**
 * Utility to find exact line numbers for methods and inner classes in Java source files
 */
public class SourceCodeParser {

    private static String sourceRoot = "src/main/java"; // Default, can be configured

    /**
     * Find the line number where a method is defined
     */
    public static int findMethodLine(String className, String methodName) {
        try {
            var sourceCode = readSourceFile(className);
            if (sourceCode == null) return -1;

            // Pattern to find method declaration
            // Handles: public void methodName(...) or private String methodName(...)
            var pattern = "\\s+(public|private|protected|static|final|synchronized|abstract|native)*\\s*" +
                    "[\\w<>\\[\\],\\s]+\\s+" + Pattern.quote(methodName) + "\\s*\\(";

            return findLineNumber(sourceCode, pattern);
        } catch (Exception e) {
            System.err.println("Error finding method line: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Find the line number where an inner class is defined
     */
    public static int findInnerClassLine(String outerClassName, String innerClassName) {
        try {
            var sourceCode = readSourceFile(outerClassName);
            if (sourceCode == null) return -1;

            // Pattern to find inner class declaration
            // Handles: public class InnerClass, private static class InnerClass, etc.
            var pattern = "\\s+(public|private|protected|static|final)*\\s*" +
                    "(class|interface|enum)\\s+" + Pattern.quote(innerClassName) + "\\s*[\\{<]";

            return findLineNumber(sourceCode, pattern);
        } catch (Exception e) {
            System.err.println("Error finding inner class line: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Find the line number of the class declaration
     */
    public static int findClassDeclarationLine(String className) {
        try {
            var sourceCode = readSourceFile(className);
            if (sourceCode == null) return -1;

            var simpleClassName = className.substring(className.lastIndexOf('.') + 1);

            // Pattern to find class declaration
            var pattern =
                    // 1. Allow start of line and optional indentation
                    "^\\s*" +
                            // 2. Modifiers: Allow space after each modifier
                            "((public|private|protected|static|final|abstract)\\s+)*" +
                            // 3. Type declaration
                            "(class|interface|enum)\\s+" +
                            // 4. Class Name
                            Pattern.quote(simpleClassName) +
                            // 5. Allow Generics, Extends, Implements, and whitespace before the opening brace
                            //    We match anything that is NOT an opening brace, followed by the brace.
                            "[^\\{]*\\{";

            return findLineNumber(sourceCode, pattern);
        } catch (Exception e) {
            System.err.println("Error finding class line: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Enhanced location finder that determines the exact line number
     */
    public static SourceLocation enhanceLocation(SourceLocation location) {
        if (location == null) return null;

        var lineNumber = location.getLineNumber();
        var className = location.getClassName();
        String methodName = null;
        if (location instanceof PropertyLocation) {
            methodName = ((PropertyLocation) location).getMethodName();
        }

        // If we already have a valid line number from stack trace, use it
        if (lineNumber > 1) {
            return location;
        }

        // Otherwise, try to find the exact line
        if (methodName != null && !methodName.equals("<class>") && !methodName.equals("<init>")) {
            // Find method line
            var foundLine = findMethodLine(className, methodName);
            if (foundLine > 0) {
                lineNumber = foundLine;
            }
        } else if (className.contains("$")) {
            // Inner class - find the inner class definition
            var outerClass = className.substring(0, className.lastIndexOf('$'));
            var innerClass = className.substring(className.lastIndexOf('$') + 1);
            var foundLine = findInnerClassLine(outerClass, innerClass);
            if (foundLine > 0) {
                lineNumber = foundLine;
            }
        } else {
            // Find class declaration line
            var foundLine = findClassDeclarationLine(className);
            if (foundLine > 0) {
                lineNumber = foundLine;
            }
        }

        if (location instanceof ClassLocation) {
            return new ClassLocation(
                    lineNumber,
                    location.getClassName(),
                    getOutermostClassName(location.getClassName())
            );
        } else if (location instanceof PropertyLocation) {
            return new PropertyLocation(
                    lineNumber,
                    location.getClassName(),
                    getOutermostClassName(location.getClassName()),
                    ((PropertyLocation) location).getPropertyName(),
                    ((PropertyLocation) location).getMethodName()
            );
        }
        return location;
    }

    /**
     * Read source file content
     */
    private static String readSourceFile(String className) {
        try {
            // Handle inner classes - use outer class file
            var outerClassName = className.contains("$")
                    ? className.substring(0, className.indexOf('$'))
                    : className;

            var relativePath = outerClassName.replace('.', '/') + ".java";
            var sourcePath = Paths.get(sourceRoot, relativePath);

            // Try alternative source roots if not found
            if (!Files.exists(sourcePath)) {
                // Try src/test/java
                sourcePath = Paths.get("src/test/java", relativePath);
            }

            if (!Files.exists(sourcePath)) {
                System.err.println("Source file not found: " + sourcePath);
                return null;
            }

            return Files.readString(sourcePath);
        } catch (IOException e) {
            System.err.println("Error reading source file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Find line number matching a regex pattern
     */
    private static int findLineNumber(String sourceCode, String regexPattern) {
        var pattern = Pattern.compile(regexPattern, Pattern.MULTILINE);
        var matcher = pattern.matcher(sourceCode);

        if (matcher.find()) {
            // Count line breaks before the match
            var beforeMatch = sourceCode.substring(0, matcher.start());
            return beforeMatch.split("\n").length + 1;
        }

        return -1;
    }

    /**
     * Set the source root directory (e.g., "src/main/java")
     */
    public static void setSourceRoot(String root) {
        sourceRoot = root;
    }

    /**
     * Get the source root directory
     */
    public static String getSourceRoot() {
        return sourceRoot;
    }
}