package org.linkki.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;

import java.io.PrintStream;

public class LinkkiLspLauncher {
    public static void main(String[] args) {
        PrintStream standardOut = System.out;

        // Redirect System.out to System.err so that random print statements from libraries don't break the LSP JSON-RPC protocol
        System.setOut(System.err);

        LinkkiLanguageServer server = new LinkkiLanguageServer();
        Launcher<LinkkiClientAPI> launcher = Launcher.createLauncher(
                server,
                LinkkiClientAPI.class,
                System.in,
                standardOut
        );
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}