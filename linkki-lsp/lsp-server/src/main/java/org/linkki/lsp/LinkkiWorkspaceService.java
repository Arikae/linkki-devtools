package org.linkki.lsp;

import com.google.gson.Gson;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LinkkiWorkspaceService implements WorkspaceService {
    private final LinkkiLanguageServer server;
    private final Gson gson = new Gson();

    public LinkkiWorkspaceService(LinkkiLanguageServer server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        if ("linkki.applySnippet".equals(params.getCommand())) {
            List<Object> args = params.getArguments();
            if (args != null && args.size() >= 3) {
                String uri = gson.toJsonTree(args.get(0)).getAsString();
                Range range = gson.fromJson(gson.toJsonTree(args.get(1)), Range.class);
                String snippet = gson.toJsonTree(args.get(2)).getAsString();

                if (server != null && server.getClient() != null) {
                    server.getClient().applySnippet(uri, range, snippet);
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    }
}