package org.linkki.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LinkkiLanguageServer implements LanguageServer, LanguageClientAware {
    private LinkkiClientAPI client;
    private final LinkkiTextDocumentService textService;
    private final LinkkiWorkspaceService workspaceService;

    public LinkkiLanguageServer() {
        this.textService = new LinkkiTextDocumentService(this);
        this.workspaceService = new LinkkiWorkspaceService(this);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();

        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(
                new SemanticTokensLegend(
                        List.of("class", "method", "variable", "linkkiComponent"), // Token Types
                        List.of("declaration", "static") // Token Modifiers
                ),
                true
        ));
        capabilities.setDefinitionProvider(true);
        capabilities.setCodeActionProvider(true);
        capabilities.setDocumentFormattingProvider(true);
        capabilities.setCompletionProvider(new CompletionOptions());
        capabilities.setDocumentSymbolProvider(true);

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = (LinkkiClientAPI) client;
        this.textService.setClient(client);
    }

    public LinkkiClientAPI getClient() {
        return client;
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
    }
}