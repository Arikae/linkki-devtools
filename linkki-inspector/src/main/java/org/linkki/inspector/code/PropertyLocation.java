package org.linkki.inspector.code;

public class PropertyLocation extends SourceLocation {
    private final String propertyName;
    private final String methodName;

    public PropertyLocation(int lineNumber, String className, String outermostClassName, String propertyName, String methodName) {
        super(lineNumber, className, outermostClassName);
        this.propertyName = propertyName;
        this.methodName = methodName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getMethodName() {
        return methodName;
    }
}