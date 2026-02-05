package org.linkki.lsp;

import org.eclipse.lsp4j.Range;
import java.util.List;

/**
 * Represents a single property entry in a .properties file, including its comments.
 */
public class PropertyEntry {
    private final String key;
    private final String value;
    private final List<String> lines; // All lines including comments and the property itself
    private final Range keyRange;

    public PropertyEntry(String key, String value, List<String> lines, Range keyRange) {
        this.key = key;
        this.value = value;
        this.lines = lines;
        this.keyRange = keyRange;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public List<String> getLines() {
        return lines;
    }

    public Range getKeyRange() {
        return keyRange;
    }

    @Override
    public String toString() {
        return String.join("\n", lines);
    }
}
