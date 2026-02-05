package org.linkki.lsp;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class LinkkiDefinitionService {

    private final Map<String, String> openDocuments;

    public LinkkiDefinitionService(Map<String, String> openDocuments) {
        this.openDocuments = openDocuments;
    }

    public List<Location> findDefinition(String uri, String content, Position pos) {
        if (content == null) {
            return Collections.emptyList();
        }

        if (uri.endsWith(".properties")) {
            return handlePropertiesDefinition(uri, content, pos);
        }

        try {
            var cu = StaticJavaParser.parse(content);
            int line = pos.getLine() + 1;
            int column = pos.getCharacter() + 1;

            List<Location> locations = new ArrayList<>();

            cu.findAll(Node.class).stream()
                    .filter(node -> node.getRange().map(r -> r.contains(new com.github.javaparser.Position(line, column))).orElse(false))
                    .reduce((a, b) -> b)
                    .ifPresent(node -> {
                        MethodDeclaration method = null;

                        if (node instanceof MethodDeclaration) {
                            method = (MethodDeclaration) node;
                        } else {
                            Optional<Node> parent = node.getParentNode();
                            while (parent.isPresent()) {
                                if (parent.get() instanceof MethodDeclaration) {
                                    method = (MethodDeclaration) parent.get();
                                    break;
                                }
                                parent = parent.get().getParentNode();
                            }
                        }

                        if (method != null) {
                            boolean hasLinkkiAnnotation = method.getAnnotations().stream()
                                    .anyMatch(a -> a.getNameAsString().startsWith("UI"));

                            if (hasLinkkiAnnotation) {
                                String className = "";
                                var typeDecl = method.findAncestor(TypeDeclaration.class);
                                if (typeDecl.isPresent()) {
                                    className = typeDecl.get().getNameAsString();
                                }

                                String methodName = method.getNameAsString();
                                String propertyName = methodName;
                                if (methodName.startsWith("get") && methodName.length() > 3) {
                                    propertyName = LinkkiLspUtils.decapitalize(methodName.substring(3));
                                } else if (methodName.startsWith("is") && methodName.length() > 2) {
                                    propertyName = LinkkiLspUtils.decapitalize(methodName.substring(2));
                                }

                                locations.addAll(findPropertiesLocation(uri, className, propertyName));
                            }
                        }
                    });

            return locations;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<Location> handlePropertiesDefinition(String uri, String content, Position pos) {
        List<Location> locations = new ArrayList<>();
        int lineIdx = pos.getLine();
        String[] lines = content.split("\n");
        if (lineIdx >= lines.length) return locations;

        String line = lines[lineIdx];
        int equalsIndex = line.indexOf('=');
        if (equalsIndex == -1) equalsIndex = line.length();

        if (pos.getCharacter() > equalsIndex) return locations;

        String key = line.substring(0, equalsIndex).trim();

        locations.addAll(findSiblingPropertiesLocations(uri, key));

        int firstUnderscore = key.indexOf('_');
        if (firstUnderscore != -1) {
            String className = key.substring(0, firstUnderscore);
            String remainder = key.substring(firstUnderscore + 1);

            Location loc = findJavaMethodLocation(uri, className, remainder);
            if (loc != null) {
                locations.add(loc);
            }
        }

        return locations;
    }

    private List<Location> findSiblingPropertiesLocations(String currentUri, String key) {
        List<Location> locations = new ArrayList<>();
        try {
            URI uri = URI.create(currentUri);
            Path currentPath = Paths.get(uri);
            Path parentDir = currentPath.getParent();

            if (parentDir != null && Files.isDirectory(parentDir)) {
                try (var stream = Files.list(parentDir)) {
                    stream.filter(p -> p.toString().endsWith(".properties"))
                            .filter(p -> !p.toAbsolutePath().equals(currentPath.toAbsolutePath()))
                            .forEach(p -> {
                                try {
                                    Location loc = findKeyInFile(p, key);
                                    if (loc != null) {
                                        locations.add(loc);
                                    }
                                } catch (IOException e) {
                                }
                            });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return locations;
    }

    private Location findJavaMethodLocation(String propertiesUri, String className, String remainder) {
        try {
            URI uri = URI.create(propertiesUri);
            Path propsPath = Paths.get(uri);
            String propsPathStr = propsPath.toString();

            String srcMainResources = "src" + File.separator + "main" + File.separator + "resources";

            int idx = propsPathStr.indexOf(srcMainResources);
            if (idx != -1) {
                String projectRoot = propsPathStr.substring(0, idx);
                Path javaSourceRoot = Paths.get(projectRoot, "src", "main", "java");

                if (Files.exists(javaSourceRoot)) {
                    Path javaFile = null;

                    if (className.contains(".")) {
                        String relativePath = className.replace('.', File.separatorChar) + ".java";
                        Path candidate = javaSourceRoot.resolve(relativePath);
                        if (Files.exists(candidate)) {
                            javaFile = candidate;
                        }
                    }

                    if (javaFile == null) {
                        String simpleName = className;
                        int lastDot = className.lastIndexOf('.');
                        if (lastDot != -1) {
                            simpleName = className.substring(lastDot + 1);
                        }
                        final String targetFileName = simpleName + ".java";

                        try (var stream = Files.walk(javaSourceRoot)) {
                            Optional<Path> found = stream
                                    .filter(p -> p.getFileName().toString().equals(targetFileName))
                                    .findFirst();
                            if (found.isPresent()) {
                                javaFile = found.get();
                            }
                        }
                    }

                    if (javaFile != null) {
                        String javaContent = null;
                        String finalUri = javaFile.toUri().toString();

                        for (Map.Entry<String, String> entry : openDocuments.entrySet()) {
                            try {
                                URI openUriObj = URI.create(entry.getKey());
                                if ("file".equals(openUriObj.getScheme())) {
                                    Path openPath = Paths.get(openUriObj);
                                    if (openPath.toAbsolutePath().equals(javaFile.toAbsolutePath())) {
                                        javaContent = entry.getValue();
                                        finalUri = entry.getKey();
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }

                        if (javaContent == null) {
                            javaContent = Files.readString(javaFile);
                        }

                        try {
                            var cu = StaticJavaParser.parse(javaContent);
                            final Location[] foundLoc = new Location[1];
                            final String locUri = finalUri;

                            cu.findAll(MethodDeclaration.class).forEach(method -> {
                                if (foundLoc[0] != null) return;

                                String methodName = method.getNameAsString();
                                String propertyName = null;
                                if (methodName.startsWith("get") && methodName.length() > 3) {
                                    propertyName = LinkkiLspUtils.decapitalize(methodName.substring(3));
                                } else if (methodName.startsWith("is") && methodName.length() > 2) {
                                    propertyName = LinkkiLspUtils.decapitalize(methodName.substring(2));
                                }

                                if (propertyName != null) {
                                    boolean match = false;
                                    if (remainder.equals(propertyName) || remainder.startsWith(propertyName + "_")) {
                                        match = true;
                                    } else {
                                        String normRemainder = remainder.replace("_", "").toLowerCase();
                                        String normProp = propertyName.toLowerCase();
                                        if (normRemainder.equals(normProp) || normRemainder.startsWith(normProp)) {
                                            match = true;
                                        }
                                    }

                                    if (match) {
                                        if (method.getAnnotations().stream().anyMatch(a -> a.getNameAsString().startsWith("UI"))) {
                                            method.getName().getRange().ifPresent(r -> {
                                                foundLoc[0] = new Location(locUri, new Range(
                                                        new Position(r.begin.line - 1, r.begin.column - 1),
                                                        new Position(r.end.line - 1, r.end.column)
                                                ));
                                            });
                                        }
                                    }
                                }
                            });

                            return foundLoc[0];
                        } catch (Exception e) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<Location> findPropertiesLocation(String javaUri, String className, String propertyName) {
        List<Location> locations = new ArrayList<>();
        try {
            URI uri = URI.create(javaUri);
            Path javaPath = Paths.get(uri);
            String javaPathStr = javaPath.toString();

            String srcMainJava = "src" + File.separator + "main" + File.separator + "java";
            String srcMainResources = "src" + File.separator + "main" + File.separator + "resources";

            if (javaPathStr.contains(srcMainJava)) {
                // Strategy 1: Look in the same package structure in resources
                String resourcesPathStr = javaPathStr.replace(srcMainJava, srcMainResources);
                Path resourcesDir = Paths.get(resourcesPathStr).getParent();
                searchInDir(resourcesDir, className, propertyName, locations);

                // Strategy 2: Look in the root of src/main/resources
                int idx = javaPathStr.indexOf(srcMainJava);
                if (idx != -1) {
                    String projectRoot = javaPathStr.substring(0, idx);
                    Path rootResourcesDir = Paths.get(projectRoot, "src", "main", "resources");
                    // Avoid searching twice if the package is empty (root)
                    if (resourcesDir == null || !rootResourcesDir.toAbsolutePath().equals(resourcesDir.toAbsolutePath())) {
                        searchInDir(rootResourcesDir, className, propertyName, locations);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return locations;
    }

    private void searchInDir(Path dir, String className, String propertyName, List<Location> locations) {
        if (dir != null && Files.exists(dir) && Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".properties"))
                        .forEach(p -> {
                            try {
                                Location loc = findKeyInFile(p, className + "_" + propertyName);
                                if (loc != null) {
                                    locations.add(loc);
                                }
                            } catch (IOException e) {
                            }
                        });
            } catch (IOException e) {
            }
        }
    }

    private Location findKeyInFile(Path file, String keyPrefix) throws IOException {
        String fileUri = file.toUri().toString();
        List<String> lines;

        String content = null;
        for (Map.Entry<String, String> entry : openDocuments.entrySet()) {
            try {
                URI openUriObj = URI.create(entry.getKey());
                if ("file".equals(openUriObj.getScheme())) {
                    Path openPath = Paths.get(openUriObj);
                    if (openPath.toAbsolutePath().equals(file.toAbsolutePath())) {
                        content = entry.getValue();
                        fileUri = entry.getKey();
                        break;
                    }
                }
            } catch (Exception e) {
            }
        }

        if (content != null) {
            lines = Arrays.asList(content.split("\n"));
        } else {
            if (!Files.exists(file)) return null;
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;

            // Robust key parsing
            int equalsIndex = trimmed.indexOf('=');
            int colonIndex = trimmed.indexOf(':');
            int spaceIndex = trimmed.indexOf(' ');

            int endOfKey = trimmed.length();
            if (equalsIndex != -1) endOfKey = Math.min(endOfKey, equalsIndex);
            if (colonIndex != -1) endOfKey = Math.min(endOfKey, colonIndex);
            if (spaceIndex != -1) endOfKey = Math.min(endOfKey, spaceIndex);

            String key = trimmed.substring(0, endOfKey).trim();

            if (key.equals(keyPrefix) || key.startsWith(keyPrefix + "_")) {
                return new Location(fileUri, new Range(
                        new Position(i, 0),
                        new Position(i, line.length())
                ));
            }
        }
        return null;
    }
}
