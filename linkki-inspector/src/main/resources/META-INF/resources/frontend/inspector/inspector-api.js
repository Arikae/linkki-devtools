export class InspectorAPI {
    constructor(config) {
        this.contextPath = config.contextPath;
        this.inspectorPort = config.inspectorPort;
    }

    log(msg, ...args) {
        // Simple internal logger
        console.log(`%c[Inspector API] ${msg}`, 'color: #2196F3; font-weight: bold;', ...args);
    }

    async fetchLocationData(componentId) {
        this.log("Fetching location for ID:", componentId);
        try {
            // Normalize context path: ensures /myApp format or empty string
            let ctx = this.contextPath || "";
            if (ctx && !ctx.startsWith("/")) ctx = "/" + ctx;
            if (ctx === "/") ctx = "";

            const response = await fetch(`${ctx}/inspector/location?componentId=${componentId}`);
            const json = await response.json();
            this.log("Received data:", json);
            return json;
        } catch (err) {
            console.error('[Inspector] Failed to fetch location:', err);
            return null;
        }
    }

    openInIDE(location) {
        if (!location || !location.className) return;

        let classForPath = location.outermostClassName || location.className;
        if (classForPath.includes('$')) {
            classForPath = classForPath.split('$')[0];
        }

        const filePath = classForPath.replace(/\./g, '/') + '.java';
        const lineNum = location.lineNumber || 1;

        const ideUrl = `http://localhost:${this.inspectorPort}/api/file/src/main/java/${filePath}?line=${lineNum}`;
        this.log('Opening in IDE via URL:', ideUrl);

        fetch(ideUrl, {mode: 'no-cors'}).catch(err => {
            console.error('[Inspector] Failed to open in IDE:', err);
            alert(`Failed to open in IDE. Ensure IntelliJ is running on port ${this.inspectorPort}.`);
        });
    }
}