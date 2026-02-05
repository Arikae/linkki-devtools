package org.linkki.inspector;


import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

public class InspectorServiceInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        System.out.println("InspectorServiceInitListener initialized");
        event.getSource().addUIInitListener(uiEvent -> {
            var ui = uiEvent.getUI();

            // Auto-enable in development mode
            if (isDevelopmentMode()) {
                System.out.println("Inspector enabled in development mode");
                LinkkiInspectorUIInjector.enable(ui);
            }
        });
    }

    private boolean isDevelopmentMode() {
        // Check system property or environment variable
        return !Boolean.getBoolean("vaadin.productionMode");
    }
}