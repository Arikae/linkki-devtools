import * as path from 'path';
import {ExtensionContext, workspace, commands, window, SnippetString, Range, Position, Uri} from 'vscode';

import {LanguageClient, LanguageClientOptions, ServerOptions} from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: ExtensionContext) {
    // The server is implemented in Java
    const serverJar = path.join(context.extensionPath, 'server', 'lsp-server-1.0.0-SNAPSHOT.jar');

    // Server options: How to start the java process
    const serverOptions: ServerOptions = {
        run: {command: 'java', args: ['-jar', serverJar]},
        debug: {command: 'java', args: ['-jar', serverJar]}
    };

    // Options to control the language client
    const clientOptions: LanguageClientOptions = {
        documentSelector: [
            {scheme: 'file', language: 'java'},
            {scheme: 'file', language: 'properties'},
            {scheme: 'file', pattern: '**/*.properties'}
        ],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/.clientrc')
        }
    };

    // Create the language client
    client = new LanguageClient(
        'linkkiLsp',
        'Linkki LSP',
        serverOptions,
        clientOptions
    );

    // Register the custom command for snippet insertion
    context.subscriptions.push(commands.registerCommand('linkki.applySnippet', async (uriStr: string, rangeObj: any, snippet: string) => {
        const editor = window.activeTextEditor;
        if (editor && editor.document.uri.toString() === uriStr) {
            // Convert the range object from LSP to VSCode Range
            const start = new Position(rangeObj.start.line, rangeObj.start.character);
            const end = new Position(rangeObj.end.line, rangeObj.end.character);
            const range = new Range(start, end);
            
            // Insert the snippet
            await editor.insertSnippet(new SnippetString(snippet), range);
        }
    }));

    // Start the client. This will also launch the server
    client.start();
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}