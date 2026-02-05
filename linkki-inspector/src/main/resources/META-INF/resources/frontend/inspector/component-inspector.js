import "./inspector.css"

import {InspectorAPI} from './inspector-api.js';
import {InspectorUI} from './inspector-ui.js';

class ComponentInspector {
    constructor() {
        this.active = false;
        this.highlightMode = false;
        this.lastLogId = null;
        this.highlightOverlays = [];
        this.api = null;
        this.ui = null;
        this.hoveredElements = [];
        this.selectedElementIndex = 0;
        this.mouseX = 0;
        this.mouseY = 0;
        this.hierarchyHoverOverlay = null;
    }

    init(port, contextPath) {
        if (window.componentInspectorInitialized) return;

        console.log("[Inspector] Initializing Main Controller...");

        this.api = new InspectorAPI({inspectorPort: port, contextPath: contextPath});

        this.ui = new InspectorUI({
            onToggle: () => this.toggle(),
            onHighlight: () => this.toggleHighlightMode(),
            onHierarchy: () => this.toggleHierarchyFromRoot(),
            onResetAll: () => this.resetAll(),
            onNodeClick: (loc) => this.api.openInIDE(loc),
            onInstantiationClick: (loc) => this.api.openInIDE(loc),
            onMenuAction: (action, data) => this.handleMenuAction(action, data),
            onNodeHover: (componentId) => this.highlightComponent(componentId),
            onNodeHoverOut: () => this.removeComponentHighlight()
        });

        // Init DOM elements
        this.ui.createOverlay();
        this.ui.createTooltip();
        this.ui.createContextMenu();
        this.ui.createHierarchyPanel();
        this.ui.createDispatcherPanel();
        this.ui.createStatusIndicator();

        this.attachGlobalListeners();
        window.componentInspectorInitialized = true;
    }

    attachGlobalListeners() {
        // Keybinds
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey && e.shiftKey) {
                if (e.key === 'E') {
                    e.preventDefault();
                    this.toggle();
                }
                if (e.key === 'H') {
                    e.preventDefault();
                    this.toggleHighlightMode();
                }
            }
            if (e.key === 'Escape') this.resetAll();
        });

        // Hover
        document.addEventListener('mousemove', (e) => {
            this.mouseX = e.clientX;
            this.mouseY = e.clientY;
            if (!this.active || this.highlightMode) return;
            this.handleHover(e);
        });

        // Scroll to select behind
        document.addEventListener('wheel', (e) => {
            if (!this.active || this.highlightMode || this.hoveredElements.length <= 1) return;
            e.preventDefault();

            if (e.deltaY > 0) {
                this.selectedElementIndex = (this.selectedElementIndex + 1) % this.hoveredElements.length;
            } else {
                this.selectedElementIndex = (this.selectedElementIndex - 1 + this.hoveredElements.length) % this.hoveredElements.length;
            }
            this.updateSelection();
        }, {passive: false});

        // Context Menu Trigger
        document.addEventListener('click', (e) => {
            if (!this.active || this.highlightMode) return;
            const menu = this.ui.elements.contextMenu;
            const panel = this.ui.elements.hierarchyPanel;
            const dispatcherPanel = this.ui.elements.dispatcherPanel;

            // If click is NOT inside menu or hierarchy panel or dispatcher panel
            if (!menu.contains(e.target) && !panel.contains(e.target) && (!dispatcherPanel || !dispatcherPanel.contains(e.target))) {
                const componentId = this.getSelectedComponentId();
                if (componentId) {
                    e.preventDefault();
                    e.stopPropagation();
                    this.openContextMenu(e.clientX, e.clientY, componentId);
                }
            }
        }, true);
    }

    deactivateInspector() {
        if (!this.active) return;
        this.active = false;
        console.log("[Inspector] Active:", this.active);

        this.ui.elements.overlay.style.display = 'none';
        this.ui.elements.tooltip.style.display = 'none';
        this.ui.elements.contextMenu.style.display = 'none';

        this.hoveredElements = [];
        this.selectedElementIndex = 0;
        this.removeComponentHighlight();

        document.body.style.cursor = '';
        this.ui.elements.statusBtn.style.background = 'linear-gradient(135deg, #757575 0%, #616161 100%)';
    }

    deactivateHighlight() {
        if (!this.highlightMode) return;
        this.highlightMode = false;
        this.clearHighlights();
        const btn = this.ui.elements.highlightBtn;
        if (btn) btn.style.background = 'linear-gradient(135deg, #757575 0%, #616161 100%)';
    }

    deactivateHierarchy() {
        if (this.ui.elements.hierarchyPanel.style.display === 'flex') {
            this.ui.setHierarchyPanelVisible(false);
        }
    }

    toggle() {
        if (this.active) {
            this.deactivateInspector();
            this.ui.openMenu();
        } else {
            this.deactivateHighlight();
            this.deactivateHierarchy();

            this.active = true;
            console.log("[Inspector] Active:", this.active);

            document.body.style.cursor = 'crosshair';
            this.ui.elements.statusBtn.style.background = 'linear-gradient(135deg, #4CAF50 0%, #45A049 100%)';
        }
    }

    resetAll() {
        this.deactivateInspector();
        this.deactivateHighlight();
        this.deactivateHierarchy();

        const dispatcherPanel = this.ui.elements.dispatcherPanel;
        if (dispatcherPanel) dispatcherPanel.style.display = 'none';
    }

    // --- Hover Logic ---

    handleHover(e) {
        // Find all elements under the cursor
        const elements = document.elementsFromPoint(e.clientX, e.clientY);

        // Filter: Only keep elements that explicitly have PMO metadata.
        // This eliminates "ghost" components that trigger the '|| Component' fallback.
        this.hoveredElements = elements.filter(el => {
            const hasId = this.getComponentId(el) !== null;
            // STRICT CHECK: The element itself must have the PMO class attribute.
            // If the attribute is on a parent, that parent is already in the 'elements' array
            // and will be selected in its own iteration.
            const hasPmoData = el.hasAttribute('data-pmo-class');
            return hasId && hasPmoData;
        });

        // Reset selection index if we moved to a new set of elements
        if (this.hoveredElements.length > 0) {
            if (this.selectedElementIndex >= this.hoveredElements.length) {
                this.selectedElementIndex = 0;
            }
        } else {
            this.selectedElementIndex = 0;
            this.ui.elements.overlay.style.display = 'none';
            this.ui.elements.tooltip.style.display = 'none';
            return;
        }

        this.updateSelection();
    }

    updateSelection() {
        if (this.hoveredElements.length === 0) return;

        const element = this.hoveredElements[this.selectedElementIndex];
        const componentId = this.getComponentId(element);

        if (!componentId) return;

        // 1. RE-ADDED: Visual Target Logic (Handle Vaadin specific wrappers)
        let target = element;
        const formItem = element.closest('vaadin-form-item');
        const insideGrid = element.closest('vaadin-grid') || element.closest('table');

        // If we are inside a form item but NOT inside a grid/table (where structure is stricter),
        // highlight the whole form item for better UX.
        if (formItem && (!insideGrid || insideGrid === element)) {
            target = formItem;
        }

        // 2. RE-ADDED: Position Overlay
        const rect = target.getBoundingClientRect();
        const overlay = this.ui.elements.overlay;

        overlay.style.display = 'block'; // Make sure it's visible
        overlay.style.left = `${rect.left}px`;
        overlay.style.top = `${rect.top}px`;
        overlay.style.width = `${rect.width}px`;
        overlay.style.height = `${rect.height}px`;

        // --- Tooltip Logic (Existing code) ---
        // Remove the "|| 'Component'" fallback to ensure only valid data is shown
        const pmoClass = element.getAttribute('data-pmo-class');
        const pmoProperty = element.getAttribute('data-pmo-property') || '';

        // If pmoClass is somehow missing despite filter, we don't want to show the tooltip at all
        if (!pmoClass) {
            this.ui.elements.tooltip.style.display = 'none';
            return;
        }

        // Use cached coordinates
        const x = this.mouseX;
        const y = this.mouseY;

        this.ui.showTooltip(x, y, pmoClass, pmoProperty, this.selectedElementIndex, this.hoveredElements.length);
    }

    getSelectedComponentId() {
        if (this.hoveredElements.length > 0 && this.selectedElementIndex < this.hoveredElements.length) {
            return this.getComponentId(this.hoveredElements[this.selectedElementIndex]);
        }
        return null;
    }

    // --- Highlight All Logic ---

    toggleHighlightMode() {
        if (this.highlightMode) {
            this.deactivateHighlight();
        } else {
            this.deactivateInspector();
            this.deactivateHierarchy();

            this.highlightMode = true;
            const btn = this.ui.elements.highlightBtn;

            this.ui.elements.overlay.style.display = 'none';
            this.ui.elements.tooltip.style.display = 'none';
            this.highlightAllPmos();
            if (btn) btn.style.background = 'linear-gradient(135deg, #FF5722 0%, #E64A19 100%)';
        }
    }

    highlightAllPmos() {
        this.clearHighlights();
        const elements = document.querySelectorAll('[data-pmo-class]');
        console.log(`[Inspector] Highlighting ${elements.length} elements.`);

        elements.forEach(el => {
            const pmoClass = el.getAttribute('data-pmo-class') || '';
            const pmoProperty = el.getAttribute('data-pmo-property') || '';
            const className = pmoClass.split('.').pop();
            const isSection = !pmoProperty;

            let target = el;
            const formItem = el.closest('vaadin-form-item');
            const insideGrid = el.closest('vaadin-grid') || el.closest('table');
            if (formItem && (!insideGrid || insideGrid === el)) {
                target = formItem;
            }

            const rect = target.getBoundingClientRect();

            // Create box
            const overlay = document.createElement('div');
            overlay.style.cssText = `
                position: fixed; left: ${rect.left}px; top: ${rect.top}px; width: ${rect.width}px; height: ${rect.height}px;
                border: 2px solid ${isSection ? '#FF5722' : '#2196F3'};
                background: ${isSection ? 'rgba(255, 87, 34, 0.08)' : 'rgba(33, 150, 243, 0.08)'};
                pointer-events: none; z-index: 999998;
                box-shadow: 0 0 0 1px ${isSection ? 'rgba(255, 87, 34, 0.3)' : 'rgba(33, 150, 243, 0.3)'};
            `;

            // Create Label
            const label = document.createElement('div');
            label.style.cssText = `
                position: fixed; left: ${rect.left}px; top: ${rect.top - 24}px;
                background: ${isSection ? 'linear-gradient(135deg, #FF5722 0%, #E64A19 100%)' : 'linear-gradient(135deg, #1976D2 0%, #1565C0 100%)'};
                color: white; padding: 4px 8px; border-radius: 4px;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                font-size: 11px; font-weight: 600; pointer-events: none; z-index: 999999;
                box-shadow: 0 2px 8px rgba(0,0,0,0.2); white-space: nowrap;
            `;
            label.textContent = isSection ? `📦 ${className}` : `🔹 ${className}.${pmoProperty}`;

            document.body.appendChild(overlay);
            document.body.appendChild(label);
            this.highlightOverlays.push(overlay);
            this.highlightOverlays.push(label);
        });
    }

    clearHighlights() {
        this.highlightOverlays.forEach(o => {
            if (o.parentNode) o.parentNode.removeChild(o);
        });
        this.highlightOverlays = [];
    }

    // --- Context Menu & Hierarchy Logic ---

    async openContextMenu(x, y, componentId) {
        const data = await this.api.fetchLocationData(componentId);
        if (data) {
            this.ui.renderContextMenu(x, y, data);
        }
    }

    async handleMenuAction(action, data) {
        if (action === 'open-property') {
            this.api.openInIDE(data.propertyLocation);
        } else if (action === 'open-class') {
            const clsLoc = {
                className: data.classLocation.className,
                outermostClassName: data.classLocation.outermostClassName,
                fileName: data.classLocation.fileName,
                lineNumber: data.classLocation.lineNumber || 1
            };
            this.api.openInIDE(clsLoc);
        } else if (action === 'open-instantiation') {
            this.api.openInIDE(data.instantiationLocation);
        } else if (action === 'show-dispatcher-history') {
            // If we already have history in data, use it. Otherwise fetch it.
            // The context menu data usually comes from fetchLocationData which includes history.
            // But if called from hierarchy button, we might need to fetch if data is incomplete.
            
            let history = data.dispatcherHistory;
            if (!history && data.componentId) {
                // Fetch fresh data if history is missing but we have ID
                const freshData = await this.api.fetchLocationData(data.componentId);
                if (freshData) {
                    history = freshData.dispatcherHistory;
                }
            }
            
            this.ui.renderDispatcherHistory(history);
        }
    }

    async toggleHierarchyFromRoot() {
        if (this.ui.elements.hierarchyPanel.style.display === 'flex') {
            this.deactivateHierarchy();
            return;
        }

        this.deactivateInspector();
        this.deactivateHighlight();

        const anyPmo = document.querySelector('[data-component-id]');
        if (!anyPmo) {
            alert("No PMO components found.");
            return;
        }

        const componentId = anyPmo.getAttribute('data-component-id');
        const data = await this.api.fetchLocationData(componentId);

        if (data && data.hierarchy) {
            this.ui.renderHierarchy(data.hierarchy);
        } else {
            alert("Could not load hierarchy.");
        }
    }

    getComponentId(element) {
        if (element.tagName && element.tagName.toLowerCase() === 'vaadin-form-item') {
            const childWithId = element.querySelector('[data-component-id]');
            if (childWithId) return childWithId.getAttribute('data-component-id');
        }
        let current = element;
        while (current && current !== document.body) {
            const cid = current.getAttribute('data-component-id');
            if (cid) return cid;
            if (current.id && current.id.startsWith('pmo-')) return current.id;
            current = current.parentElement;
        }
        return null;
    }

    highlightComponent(componentId) {
        this.removeComponentHighlight();
        
        const element = document.querySelector(`[data-component-id="${componentId}"]`);
        if (!element) return;

        let target = element;
        const formItem = element.closest('vaadin-form-item');
        const insideGrid = element.closest('vaadin-grid') || element.closest('table');
        if (formItem && (!insideGrid || insideGrid === element)) {
            target = formItem;
        }

        const rect = target.getBoundingClientRect();
        
        const overlay = document.createElement('div');
        overlay.style.cssText = `
            position: fixed; left: ${rect.left}px; top: ${rect.top}px; width: ${rect.width}px; height: ${rect.height}px;
            border: 2px solid #FF9800;
            background: rgba(255, 152, 0, 0.2);
            pointer-events: none; z-index: 999999;
            box-shadow: 0 0 0 1px rgba(255, 152, 0, 0.5);
            transition: all 0.2s ease;
        `;
        
        document.body.appendChild(overlay);
        this.hierarchyHoverOverlay = overlay;
        
        // Scroll into view if needed
        target.scrollIntoView({behavior: "smooth", block: "center", inline: "nearest"});
    }

    removeComponentHighlight() {
        if (this.hierarchyHoverOverlay) {
            if (this.hierarchyHoverOverlay.parentNode) {
                this.hierarchyHoverOverlay.parentNode.removeChild(this.hierarchyHoverOverlay);
            }
            this.hierarchyHoverOverlay = null;
        }
    }
}

// Singleton Export attached to window
window.LinkkiInspector = new ComponentInspector();
