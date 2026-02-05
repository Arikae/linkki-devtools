package org.linkki.lsp;

import com.github.javaparser.StaticJavaParser;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class LinkkiTextDocumentService implements TextDocumentService {
    private final LinkkiLanguageServer server;
    private LanguageClient client;
    private final Map<String, String> openDocuments = new HashMap<>();
    
    private final LinkkiDiagnosticService diagnosticService = new LinkkiDiagnosticService();
    private final LinkkiDefinitionService definitionService = new LinkkiDefinitionService(openDocuments);
    private final LinkkiCodeActionService codeActionService = new LinkkiCodeActionService();
    private final LinkkiFormattingService formattingService = new LinkkiFormattingService();
    private final LinkkiCompletionService completionService = new LinkkiCompletionService(openDocuments);
    private final LinkkiDocumentSymbolService documentSymbolService = new LinkkiDocumentSymbolService();

    public LinkkiTextDocumentService(LinkkiLanguageServer server) {
        this.server = server;
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        openDocuments.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
        validateDocument(params.getTextDocument().getUri());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        openDocuments.put(params.getTextDocument().getUri(), params.getContentChanges().get(0).getText());
        validateDocument(params.getTextDocument().getUri());
    }

    private void validateDocument(String uri) {
        String content = openDocuments.get(uri);
        List<Diagnostic> diagnostics = diagnosticService.validateDocument(uri, content);
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        var code = openDocuments.get(params.getTextDocument().getUri());
        var data = new ArrayList<Integer>();

        if (code != null) {
            try {
                StaticJavaParser.parse(code);
            } catch (Exception e) {
            }
        }

        return CompletableFuture.completedFuture(new SemanticTokens(data));
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        String uri = params.getTextDocument().getUri();
        String content = openDocuments.get(uri);
        List<Location> locations = definitionService.findDefinition(uri, content, params.getPosition());
        return CompletableFuture.completedFuture(Either.forLeft(locations));
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        String uri = params.getTextDocument().getUri();
        String content = openDocuments.get(uri);
        List<Either<Command, CodeAction>> actions = codeActionService.getCodeActions(params, content);
        return CompletableFuture.completedFuture(actions);
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        var uri = params.getTextDocument().getUri();
        var content = openDocuments.get(uri);
        List<TextEdit> edits = formattingService.formatDocument(params, content);
        return CompletableFuture.completedFuture(edits);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        Either<List<CompletionItem>, CompletionList> items = completionService.getCompletionItems(params);
        return CompletableFuture.completedFuture(items);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        String content = openDocuments.get(uri);
        List<Either<SymbolInformation, DocumentSymbol>> symbols = documentSymbolService.getDocumentSymbols(uri, content);
        return CompletableFuture.completedFuture(symbols);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        openDocuments.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        validateDocument(params.getTextDocument().getUri());
    }
}
