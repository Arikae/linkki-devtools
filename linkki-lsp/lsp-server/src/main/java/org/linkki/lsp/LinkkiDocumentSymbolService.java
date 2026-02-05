package org.linkki.lsp;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LinkkiDocumentSymbolService {

    public List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(String uri, String content) {
        if (content == null) {
            return Collections.emptyList();
        }

        List<DocumentSymbol> result = new ArrayList<>();
        try {
            var cu = StaticJavaParser.parse(content);
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                if (isPmo(c)) {
                    DocumentSymbol pmoSymbol = createSymbol(c, c.getNameAsString(), SymbolKind.Class);
                    List<DocumentSymbol> children = new ArrayList<>();

                    List<Node> components = new ArrayList<>();
                    c.getFields().stream().filter(this::isLinkkiComponent).forEach(components::add);
                    c.getMethods().stream().filter(this::isLinkkiComponent).forEach(components::add);

                    for (Node comp : components) {
                        String compName = getComponentName(comp);
                        DocumentSymbol compSymbol = createSymbol(comp, compName, SymbolKind.Property);

                        List<DocumentSymbol> aspects = new ArrayList<>();
                        c.getMethods().forEach(m -> {
                            if (isAspectMethod(m, compName, comp)) {
                                aspects.add(createSymbol(m, m.getNameAsString(), SymbolKind.Method));
                            }
                        });

                        compSymbol.setChildren(aspects);
                        children.add(compSymbol);
                    }

                    pmoSymbol.setChildren(children);
                    result.add(pmoSymbol);
                }
            });
        } catch (Exception e) {
        }

        return result.stream().map(Either::<SymbolInformation, DocumentSymbol>forRight).collect(Collectors.toList());
    }

    private boolean isPmo(ClassOrInterfaceDeclaration c) {
        return c.getAnnotations().stream().anyMatch(a -> a.getNameAsString().startsWith("UI"));
    }

    private boolean isLinkkiComponent(Node node) {
        if (node instanceof FieldDeclaration) {
            return ((FieldDeclaration) node).getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().startsWith("UI"));
        }
        if (node instanceof MethodDeclaration) {
            return ((MethodDeclaration) node).getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().startsWith("UI"));
        }
        return false;
    }

    private String getComponentName(Node node) {
        if (node instanceof FieldDeclaration) {
            var variables = ((FieldDeclaration) node).getVariables();
            if (!variables.isEmpty()) {
                return variables.get(0).getNameAsString();
            }
        } else if (node instanceof MethodDeclaration) {
            String name = ((MethodDeclaration) node).getNameAsString();
            if (name.startsWith("get") && name.length() > 3) return LinkkiLspUtils.decapitalize(name.substring(3));
            if (name.startsWith("is") && name.length() > 2) return LinkkiLspUtils.decapitalize(name.substring(2));
            return name;
        }
        return "";
    }

    private boolean isAspectMethod(MethodDeclaration m, String componentName, Node componentNode) {
        if (m.equals(componentNode)) return false;

        String methodName = m.getNameAsString();
        String prefix = "is" + LinkkiLspUtils.capitalize(componentName);
        return methodName.startsWith(prefix) && methodName.length() > prefix.length();
    }

    private DocumentSymbol createSymbol(Node node, String name, SymbolKind kind) {
        Range range = getRange(node);
        Range selectionRange = getSelectionRange(node);
        return new DocumentSymbol(name, kind, range, selectionRange);
    }

    private Range getRange(Node node) {
        return node.getRange().map(r -> new Range(
                new Position(r.begin.line - 1, r.begin.column - 1),
                new Position(r.end.line - 1, r.end.column)
        )).orElse(new Range(new Position(0, 0), new Position(0, 0)));
    }

    private Range getSelectionRange(Node node) {
        if (node instanceof ClassOrInterfaceDeclaration) {
            return ((ClassOrInterfaceDeclaration) node).getName().getRange()
                    .map(this::toLspRange).orElse(getRange(node));
        } else if (node instanceof MethodDeclaration) {
            return ((MethodDeclaration) node).getName().getRange()
                    .map(this::toLspRange).orElse(getRange(node));
        } else if (node instanceof FieldDeclaration) {
            var variables = ((FieldDeclaration) node).getVariables();
            if (!variables.isEmpty()) {
                return variables.get(0).getName().getRange().map(this::toLspRange).orElse(getRange(node));
            }
        }
        return getRange(node);
    }

    private Range toLspRange(com.github.javaparser.Range r) {
        return new Range(
                new Position(r.begin.line - 1, r.begin.column - 1),
                new Position(r.end.line - 1, r.end.column)
        );
    }
}
