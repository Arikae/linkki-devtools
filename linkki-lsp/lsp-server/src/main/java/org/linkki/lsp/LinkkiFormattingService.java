package org.linkki.lsp;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;

public class LinkkiFormattingService {

    private final PropertiesSorter propertiesSorter = new PropertiesSorter();

    public List<TextEdit> formatDocument(DocumentFormattingParams params, String content) {
        var uri = params.getTextDocument().getUri();
        var edits = new ArrayList<TextEdit>();

        if (uri.endsWith(".properties")) {
            String sortedContent = propertiesSorter.sort(content);
            edits.add(new TextEdit(
                    new Range(new Position(0, 0), new Position(Integer.MAX_VALUE, 0)),
                    sortedContent
            ));

        } else if (uri.endsWith(".java")) {
            try {
                var cu = StaticJavaParser.parse(content);

                cu.getTypes().forEach(type -> {
                    List<MethodDeclaration> methods = new ArrayList<>(type.getMethods());
                    methods.forEach(MethodDeclaration::remove);

                    methods.sort((m1, m2) -> {
                        boolean m1Annotated = m1.getAnnotations().stream().anyMatch(a -> a.getNameAsString().startsWith("UI"));
                        boolean m2Annotated = m2.getAnnotations().stream().anyMatch(a -> a.getNameAsString().startsWith("UI"));

                        if (m1Annotated && !m2Annotated) return -1;
                        if (!m1Annotated && m2Annotated) return 1;
                        return m1.getNameAsString().compareTo(m2.getNameAsString());
                    });

                    methods.forEach(type::addMember);
                });

                edits.add(new TextEdit(
                        new Range(new Position(0, 0), new Position(Integer.MAX_VALUE, 0)),
                        cu.toString()
                ));
            } catch (Exception e) {
            }
        }

        return edits;
    }
}
