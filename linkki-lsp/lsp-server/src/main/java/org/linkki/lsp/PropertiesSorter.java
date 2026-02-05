package org.linkki.lsp;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PropertiesSorter {

    public static final String LINKKI_UNSORTED_KEY = "LINKKI_UNSORTED_KEY";

    /**
     * Validates the content of a properties file and returns diagnostics for unsorted keys.
     */
    public List<Diagnostic> validate(String content) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<List<PropertyEntry>> groups = parseGroups(content);

        for (List<PropertyEntry> group : groups) {
            if (group.size() < 2) continue;

            for (int i = 1; i < group.size(); i++) {
                PropertyEntry current = group.get(i);
                PropertyEntry previous = group.get(i - 1);

                if (current.getKey().compareTo(previous.getKey()) < 0) {
                    Diagnostic diagnostic = new Diagnostic();
                    diagnostic.setSeverity(DiagnosticSeverity.Warning);
                    diagnostic.setCode(LINKKI_UNSORTED_KEY);
                    diagnostic.setMessage("Key is not sorted lexicographically within its group.");
                    diagnostic.setRange(current.getKeyRange());
                    diagnostic.setSource("Linkki Properties Sorter");
                    diagnostics.add(diagnostic);
                }
            }
        }
        return diagnostics;
    }

    /**
     * Sorts all property groups in the given content.
     */
    public String sort(String content) {
        List<List<PropertyEntry>> groups = parseGroups(content);
        StringBuilder sortedContent = new StringBuilder();

        for (int i = 0; i < groups.size(); i++) {
            List<PropertyEntry> group = groups.get(i);

            List<PropertyEntry> sortedGroup = new ArrayList<>(group);
            sortedGroup.sort(Comparator.comparing(PropertyEntry::getKey));

            for (PropertyEntry entry : sortedGroup) {
                sortedContent.append(entry.toString()).append("\n");
            }

            if (i < groups.size() - 1) {
                sortedContent.append("\n");
            }
        }

        return sortedContent.toString();
    }

    /**
     * Sorts only the group containing the given range.
     */
    public String sortGroup(String content, Range range) {
        List<List<PropertyEntry>> groups = parseGroups(content);
        StringBuilder sortedContent = new StringBuilder();

        for (int i = 0; i < groups.size(); i++) {
            List<PropertyEntry> group = groups.get(i);
            boolean isTargetGroup = false;

            for (PropertyEntry entry : group) {
                if (isOverlapping(entry.getKeyRange(), range)) {
                    isTargetGroup = true;
                    break;
                }
            }

            if (isTargetGroup) {
                List<PropertyEntry> sortedGroup = new ArrayList<>(group);
                sortedGroup.sort(Comparator.comparing(PropertyEntry::getKey));
                for (PropertyEntry entry : sortedGroup) {
                    sortedContent.append(entry.toString()).append("\n");
                }
            } else {
                for (PropertyEntry entry : group) {
                    sortedContent.append(entry.toString()).append("\n");
                }
            }

            if (i < groups.size() - 1) {
                sortedContent.append("\n");
            }
        }
        return sortedContent.toString();
    }

    private boolean isOverlapping(Range r1, Range r2) {
        int r1Start = r1.getStart().getLine();
        int r1End = r1.getEnd().getLine();
        int r2Start = r2.getStart().getLine();
        int r2End = r2.getEnd().getLine();

        // Check if the lines overlap
        return r1Start <= r2End && r2Start <= r1End;
    }

    private List<List<PropertyEntry>> parseGroups(String content) {
        List<List<PropertyEntry>> groups = new ArrayList<>();
        List<PropertyEntry> currentGroup = new ArrayList<>();
        List<String> currentComments = new ArrayList<>();

        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (!currentGroup.isEmpty()) {
                    groups.add(new ArrayList<>(currentGroup));
                    currentGroup.clear();
                }
                currentComments.clear();
                continue;
            }

            if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                currentComments.add(line);
            } else {
                int keyStartLine = i;
                List<String> entryLines = new ArrayList<>(currentComments);
                entryLines.add(line);

                while (isContinued(lines[i])) {
                    i++;
                    if (i < lines.length) {
                        entryLines.add(lines[i]);
                    } else {
                        break;
                    }
                }

                String firstLine = lines[keyStartLine];
                int sepIdx = findSeparatorIndex(firstLine);
                String key;
                if (sepIdx != -1) {
                    key = firstLine.substring(0, sepIdx).trim();
                } else {
                    key = firstLine.trim();
                }

                int startChar = firstLine.indexOf(key);
                if (startChar == -1) startChar = 0;

                Range keyRange = new Range(
                        new Position(keyStartLine, startChar),
                        new Position(keyStartLine, startChar + key.length())
                );

                currentGroup.add(new PropertyEntry(key, "", entryLines, keyRange));
                currentComments.clear();
            }
        }

        if (!currentGroup.isEmpty()) {
            groups.add(new ArrayList<>(currentGroup));
        }

        return groups;
    }

    private boolean isContinued(String line) {
        if (line == null) return false;
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0; i--) {
            if (line.charAt(i) == '\\') {
                backslashes++;
            } else {
                break;
            }
        }
        return backslashes % 2 != 0;
    }

    private int findSeparatorIndex(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '=' || c == ':') {
                if (i > 0 && line.charAt(i - 1) == '\\') continue;
                return i;
            }
        }
        return -1;
    }
}
