package org.linkki.inspector;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinSession;
import org.apache.logging.log4j.util.Strings;

import java.io.Serializable;

public class LinkkiInspectorUIInjector implements Serializable {

    private static final String INSPECTOR_ENABLED_KEY = "inspector.enabled";
    private static final int INSPECTOR_PORT = 63342;

    // 1. Define a resource loader component to trigger the Vite bundle inclusion
    @Tag("inspector-loader")
    @JsModule("./inspector/component-inspector.js") // References frontend/inspector/component-inspector.js
    public static class InspectorLoader extends Component {
        // This component doesn't need to render anything visual;
        // its presence ensures the JS is loaded.
    }

    public static void enable(UI ui) {
        VaadinSession.getCurrent().setAttribute(INSPECTOR_ENABLED_KEY, true);
        injectInspector(ui);
    }

    public static void disable() {
        VaadinSession.getCurrent().setAttribute(INSPECTOR_ENABLED_KEY, false);
        // Optional: You could reload the page or remove the UI elements via JS if needed
    }

    public static boolean isEnabled() {
        var enabled = (Boolean) VaadinSession.getCurrent().getAttribute(INSPECTOR_ENABLED_KEY);
        return enabled != null && enabled;
    }

    private static void injectInspector(UI ui) {
        var contextPath = VaadinServlet.getCurrent().getServletContext().getContextPath();
        String safeContextPath = Strings.isBlank(contextPath) ? "" : contextPath;

        // 2. Add the loader component to the UI to force the script load
        ui.add(new InspectorLoader());

        // 3. Initialize the inspector
        // We no longer need to import() the file manually; it's already in the bundle.
        // We just wait for the browser to execute the bundle and then call init.
        String script = """
                if (window.LinkkiInspector) {
                    window.LinkkiInspector.init($0, '$1');
                } else {
                    // Fallback: If bundle execution order varies, wait slightly
                    setTimeout(() => {
                        if (window.LinkkiInspector) window.LinkkiInspector.init($0, '$1');
                    }, 100);
                }
                """
                .replace("$0", String.valueOf(INSPECTOR_PORT))
                .replace("$1", safeContextPath);

        ui.getPage().executeJs(script);
    }
}