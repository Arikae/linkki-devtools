package org.linkki.lsp;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LinkkiCompletionService {

    private final Map<String, String> openDocuments;

    public LinkkiCompletionService(Map<String, String> openDocuments) {
        this.openDocuments = openDocuments;
    }

    public Either<List<CompletionItem>, CompletionList> getCompletionItems(CompletionParams params) {
        var items = new ArrayList<CompletionItem>();
        String uri = params.getTextDocument().getUri();

        if (uri.endsWith(".java")) {
            var textField = new CompletionItem();
            textField.setLabel("Linkki TextField");
            textField.setKind(CompletionItemKind.Snippet);
            textField.setInsertTextFormat(InsertTextFormat.Snippet);
            textField.setInsertText(
                    """
                            @UITextField(position = ${1:10}, label = "${2:Label}")
                            public void ${3:propertyName}() {
                                // TODO bind field
                            }"""
            );
            textField.setDetail("Generates a @UITextField method stub");
            items.add(textField);
        } else {
            try {
                Path path = Paths.get(URI.create(uri));
                String fileName = path.getFileName().toString();
                if (fileName.matches("linkki-messages(?:_[a-zA-Z]{2})?\\.properties")) {
                    items.addAll(getLinkkiPropertyCompletions(path));
                }
            } catch (Exception e) {
            }
        }

        return Either.forLeft(items);
    }

    private List<CompletionItem> getLinkkiPropertyCompletions(Path propertiesFile) {
        List<CompletionItem> items = new ArrayList<>();
        Path sourceDir = getSourcePathFromResource(propertiesFile);

        if (sourceDir != null && Files.exists(sourceDir) && Files.isDirectory(sourceDir)) {
            try (var stream = Files.list(sourceDir)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                        .forEach(javaFile -> items.addAll(extractPropertiesFromPmo(javaFile)));
            } catch (IOException e) {
            }
        }
        return items;
    }

    private Path getSourcePathFromResource(Path resourcePath) {
        String pathStr = resourcePath.toAbsolutePath().toString();
        String resourcesMarker = "src" + File.separator + "main" + File.separator + "resources";
        String javaMarker = "src" + File.separator + "main" + File.separator + "java";

        if (pathStr.contains(resourcesMarker)) {
            String javaPathStr = pathStr.replace(resourcesMarker, javaMarker);
            return Paths.get(javaPathStr).getParent();
        }
        return null;
    }

    private List<CompletionItem> extractPropertiesFromPmo(Path javaFile) {
        List<CompletionItem> items = new ArrayList<>();
        try {
            String content = null;
            for (Map.Entry<String, String> entry : openDocuments.entrySet()) {
                try {
                    URI openUriObj = URI.create(entry.getKey());
                    if ("file".equals(openUriObj.getScheme())) {
                        Path openPath = Paths.get(openUriObj);
                        if (openPath.toAbsolutePath().equals(javaFile.toAbsolutePath())) {
                            content = entry.getValue();
                            break;
                        }
                    }
                } catch (Exception e) {
                }
            }

            if (content == null) {
                content = Files.readString(javaFile);
            }

            var cu = StaticJavaParser.parse(content);

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                String className = c.getNameAsString();

                c.getMethods().forEach(method -> {
                    if (method.getAnnotations().stream().anyMatch(a -> a.getNameAsString().startsWith("UI"))) {
                        String methodName = method.getNameAsString();
                        String propertyName = null;

                        if (methodName.startsWith("get") && methodName.length() > 3) {
                            propertyName = LinkkiLspUtils.decapitalize(methodName.substring(3));
                        } else if (methodName.startsWith("is") && methodName.length() > 2) {
                            propertyName = LinkkiLspUtils.decapitalize(methodName.substring(2));
                        }

                        if (propertyName != null) {
                            CompletionItem item = new CompletionItem();
                            item.setLabel(className + "_" + propertyName);
                            item.setKind(CompletionItemKind.Property);
                            item.setDetail(className + "." + methodName);
                            items.add(item);
                        }
                    }
                });
            });

        } catch (Exception e) {
        }
        return items;
    }
}
