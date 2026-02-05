package org.linkki.lsp.eclipse;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LinkkiStreamConnectionProvider extends ProcessStreamConnectionProvider {

    public LinkkiStreamConnectionProvider() {
        // Find the server jar inside the OSGi Bundle
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        // The path 'server/server.jar' matches what we defined in the Tycho/Maven copy step
        URL resourceUrl = bundle.getResource("server/server.jar");

        List<String> commands = new ArrayList<>();
        commands.add("java"); // Ensure Java is in Eclipse's PATH or use JavaRuntime.getDefaultVMInstall()
        commands.add("-jar");

        try {
            if (resourceUrl != null) {
                // Convert OSGi URL to standard file path
                File file = new File(FileLocator.toFileURL(resourceUrl).getPath());
                commands.add(file.getAbsolutePath());
            } else {
                System.err.println("LSP Server JAR not found in bundle!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        setCommands(commands);
        setWorkingDirectory(System.getProperty("user.dir"));
    }
}