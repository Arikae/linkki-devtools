package org.linkki.inspector.code;

import java.io.Serializable;

/**
 * Abstract source location data class
 */
public abstract class SourceLocation implements Serializable {
    private final int lineNumber;
    private final String className;
    private final String outermostClassName;

    public SourceLocation(int lineNumber, String className, String outermostClassName) {
        this.lineNumber = lineNumber;
        this.className = className;
        this.outermostClassName = outermostClassName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getClassName() {
        return className;
    }

    public String getOutermostClassName() {
        return outermostClassName;
    }

    public String getFileName() {
        return outermostClassName + ".java";
    }
}