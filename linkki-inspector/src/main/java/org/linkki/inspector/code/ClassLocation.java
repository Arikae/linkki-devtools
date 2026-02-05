package org.linkki.inspector.code;

public class ClassLocation extends SourceLocation {

    public ClassLocation(int lineNumber, String className, String outermostClassName) {
        super(lineNumber, className, outermostClassName);
    }
}