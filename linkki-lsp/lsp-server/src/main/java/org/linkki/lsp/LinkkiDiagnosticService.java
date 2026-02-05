package org.linkki.lsp;

import org.eclipse.lsp4j.*;
import org.linkki.tooling.apt.processor.LinkkiAnnotationProcessor;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LinkkiDiagnosticService {

    private final PropertiesSorter propertiesSorter = new PropertiesSorter();

    public List<Diagnostic> validateDocument(String uri, String content) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (uri.endsWith(".properties") && content != null) {
            diagnostics.addAll(propertiesSorter.validate(content));
        } else if (uri.endsWith(".java") && content != null) {
            var compiler = ToolProvider.getSystemJavaCompiler();
            var diagnosticCollector = new DiagnosticCollector<>();
            var fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);

            JavaFileObject source = new SimpleJavaFileObject(URI.create(uri), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return content;
                }
            };

            var task = compiler.getTask(null, fileManager, diagnosticCollector, null, null, List.of(source));
            task.setProcessors(List.of(new LinkkiAnnotationProcessor()));
            task.call();

            for (var d : diagnosticCollector.getDiagnostics()) {
                String code = d.getCode();
                if (code != null && (
                        code.startsWith("compiler.err.cant.resolve") ||
                                code.startsWith("compiler.err.doesnt.exist") ||
                                code.startsWith("compiler.err.not.def.access.class.intf.cant.access")
                )) {
                    continue;
                }

                var lspDiagnostic = new Diagnostic();
                lspDiagnostic.setSeverity(DiagnosticSeverity.Error);
                String message = d.getMessage(Locale.getDefault());
                lspDiagnostic.setMessage(message);
                lspDiagnostic.setSource("Linkki Processor");

                if (message != null) {
                    String trimmedMsg = message.trim();
                    if (trimmedMsg.endsWith("]")) {
                        int lastBracket = trimmedMsg.lastIndexOf('[');
                        if (lastBracket != -1) {
                            String extractedCode = trimmedMsg.substring(lastBracket + 1, trimmedMsg.length() - 1);
                            lspDiagnostic.setCode(extractedCode);
                        }
                    }
                }

                if (lspDiagnostic.getCode() == null && code != null) {
                    lspDiagnostic.setCode(code);
                }

                var startOffset = d.getStartPosition();
                var endOffset = d.getEndPosition();

                if (startOffset != javax.tools.Diagnostic.NOPOS && endOffset != javax.tools.Diagnostic.NOPOS) {
                    lspDiagnostic.setRange(new Range(
                            LinkkiLspUtils.offsetToPosition(content, startOffset),
                            LinkkiLspUtils.offsetToPosition(content, endOffset)
                    ));
                } else {
                    int line = (int) d.getLineNumber() - 1;
                    int col = (int) d.getColumnNumber() - 1;
                    if (line < 0) line = 0;
                    if (col < 0) col = 0;

                    lspDiagnostic.setRange(new Range(
                            new Position(line, col),
                            new Position(line, col + 1)
                    ));
                }

                diagnostics.add(lspDiagnostic);
            }
        }
        return diagnostics;
    }
}
