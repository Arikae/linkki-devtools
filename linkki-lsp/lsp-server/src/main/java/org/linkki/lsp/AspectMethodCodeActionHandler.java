package org.linkki.lsp;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.linkki.tooling.apt.validator.AspectMethodValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AspectMethodCodeActionHandler {

    private static final Pattern MISSING_METHOD_NAME_PATTERN = Pattern.compile("Method \"(\\w+)\\(\\)\" is missing");

    public List<Either<Command, CodeAction>> getCodeActions(CodeActionParams params, String content) {
        List<Either<Command, CodeAction>> actions = new ArrayList<>();

        for (Diagnostic diagnostic : params.getContext().getDiagnostics()) {
            String code = getCode(diagnostic);
            if (AspectMethodValidator.MISSING_METHOD.equals(code) ||
                    AspectMethodValidator.MISSING_METHOD_ABSTRACT_TYPE.equals(code)) {

                createMissingMethodAction(diagnostic, content, params.getTextDocument().getUri())
                        .ifPresent(action -> actions.add(Either.forRight(action)));
            }
        }

        return actions;
    }

    private String getCode(Diagnostic diagnostic) {
        if (diagnostic.getCode() == null) {
            return null;
        }
        if (diagnostic.getCode().isLeft()) {
            return diagnostic.getCode().getLeft();
        }
        return diagnostic.getCode().getRight().toString();
    }

    private Optional<CodeAction> createMissingMethodAction(Diagnostic diagnostic, String content, String uri) {
        try {
            var cu = StaticJavaParser.parse(content);
            int line = diagnostic.getRange().getStart().getLine() + 1;
            int column = diagnostic.getRange().getStart().getCharacter() + 1;

            // Find the method that triggered the error
            Optional<MethodDeclaration> sourceMethodOpt = cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> m.getRange().map(r -> r.contains(new com.github.javaparser.Position(line, column))).orElse(false))
                    .findFirst();

            if (sourceMethodOpt.isPresent()) {
                MethodDeclaration sourceMethod = sourceMethodOpt.get();
                String missingMethodName = extractMissingMethodName(diagnostic.getMessage());

                if (missingMethodName != null) {
                    CodeAction action = new CodeAction("Create missing aspect method '" + missingMethodName + "'");
                    action.setKind(CodeActionKind.QuickFix);
                    action.setDiagnostics(Collections.singletonList(diagnostic));

                    Position insertPos = getInsertPosition(sourceMethod);

                    String indentation = getIndentation(sourceMethod);
                    String snippet = generateSnippet(missingMethodName, indentation);

                    Command command = new Command("Insert Snippet", "linkki.applySnippet", List.of(
                            uri,
                            new Range(insertPos, insertPos),
                            snippet
                    ));

                    action.setCommand(command);

                    action.setEdit(null);

                    return Optional.of(action);
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return Optional.empty();
    }

    private String extractMissingMethodName(String message) {
        Matcher matcher = MISSING_METHOD_NAME_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Position getInsertPosition(MethodDeclaration method) {
        if (method.getRange().isPresent()) {
            com.github.javaparser.Position end = method.getRange().get().end;
            return new Position(end.line, 0);
        }
        return new Position(0, 0);
    }

    private String getIndentation(MethodDeclaration method) {
        if (method.getRange().isPresent()) {
            int column = method.getRange().get().begin.column;
            return " ".repeat(Math.max(0, column - 1));
        }
        return "    ";
    }

    private String generateSnippet(String methodName, String indentation) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n");
        sb.append(indentation).append("public ${1:boolean} ").append(methodName).append("() {\n");
        sb.append(indentation).append("    return ${2:true};\n");
        sb.append(indentation).append("}");
        return sb.toString();
    }
}