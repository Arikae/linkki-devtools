package org.linkki.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LinkkiCodeActionService {

    private final PropertiesSorter propertiesSorter = new PropertiesSorter();
    private final AspectMethodCodeActionHandler aspectMethodCodeActionHandler = new AspectMethodCodeActionHandler();

    public List<Either<Command, CodeAction>> getCodeActions(CodeActionParams params, String content) {
        var actions = new ArrayList<Either<Command, CodeAction>>();
        String uri = params.getTextDocument().getUri();

        if (uri.endsWith(".properties") && content != null) {
            boolean hasUnsortedDiagnostic = params.getContext().getDiagnostics().stream()
                    .anyMatch(this::isUnsortedDiagnostic);

            if (hasUnsortedDiagnostic) {
                CodeAction sortGroupAction = new CodeAction("Sort properties in this group");
                sortGroupAction.setKind(CodeActionKind.QuickFix);

                Range diagnosticRange = params.getContext().getDiagnostics().stream()
                        .filter(this::isUnsortedDiagnostic)
                        .findFirst()
                        .map(Diagnostic::getRange)
                        .orElse(params.getRange());

                String sortedGroupContent = propertiesSorter.sortGroup(content, diagnosticRange);

                sortGroupAction.setEdit(new WorkspaceEdit(Collections.singletonMap(uri, List.of(
                        new TextEdit(new Range(new Position(0, 0), new Position(Integer.MAX_VALUE, 0)), sortedGroupContent)
                ))));

                actions.add(Either.forRight(sortGroupAction));
            }

            CodeAction sortAllAction = new CodeAction("Sort all property groups");
            sortAllAction.setKind(CodeActionKind.SourceOrganizeImports);

            String sortedAllContent = propertiesSorter.sort(content);

            sortAllAction.setEdit(new WorkspaceEdit(Collections.singletonMap(uri, List.of(
                    new TextEdit(new Range(new Position(0, 0), new Position(Integer.MAX_VALUE, 0)), sortedAllContent)
            ))));

            actions.add(Either.forRight(sortAllAction));
        } else if (uri.endsWith(".java") && content != null) {
            actions.addAll(aspectMethodCodeActionHandler.getCodeActions(params, content));
        }

        var action = new CodeAction("Create property in messages.properties");
        action.setKind(CodeActionKind.QuickFix);
        actions.add(Either.forRight(action));
        return actions;
    }

    private boolean isUnsortedDiagnostic(Diagnostic d) {
        var code = d.getCode();
        if (code instanceof Either) {
            return ((Either<?, ?>) code).isLeft() && PropertiesSorter.LINKKI_UNSORTED_KEY.equals(((Either<?, ?>) code).getLeft());
        }
        return PropertiesSorter.LINKKI_UNSORTED_KEY.equals(code);
    }
}
