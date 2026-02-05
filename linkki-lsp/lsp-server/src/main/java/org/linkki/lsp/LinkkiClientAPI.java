package org.linkki.lsp;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

public interface LinkkiClientAPI extends LanguageClient {

    @JsonNotification("linkki/applySnippet")
    void applySnippet(String uri, Range range, String snippet);
}