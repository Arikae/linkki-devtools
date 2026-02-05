export class InspectorUI {
    constructor(handlers) {
        this.handlers = handlers; // { onToggle, onHighlight, onHierarchy, onResetAll, onNodeClick, onInstantiationClick, onMenuAction, onNodeHover, onNodeHoverOut }
        this.elements = {};
        this.menuOpen = false;
    }

    createOverlay() {
        const el = document.createElement('div');
        el.id = 'inspector-overlay';
        document.body.appendChild(el);
        this.elements.overlay = el;
    }

    createTooltip() {
        const el = document.createElement('div');
        el.id = 'inspector-tooltip';
        document.body.appendChild(el);
        this.elements.tooltip = el;
    }

    createContextMenu() {
        const menu = document.createElement('div');
        menu.id = 'inspector-context-menu';
        document.body.appendChild(menu);
        this.elements.contextMenu = menu;

        // Close menu if clicked outside
        document.addEventListener('click', (e) => {
            if (!menu.contains(e.target)) {
                menu.style.display = 'none';
            }
        });
    }

    createHierarchyPanel() {
        const panel = document.createElement('div');
        panel.id = 'inspector-hierarchy-panel';

        const header = document.createElement('div');
        header.className = 'hierarchy-header';
        header.innerHTML = `
            <span>PMO Hierarchy</span>
            <button id="inspector-close-hierarchy">Close</button>
        `;

        const content = document.createElement('div');
        content.className = 'hierarchy-scroll-area';

        panel.appendChild(header);
        panel.appendChild(content);
        document.body.appendChild(panel);

        header.querySelector('#inspector-close-hierarchy').onclick = () => {
            this.setHierarchyPanelVisible(false);
        };

        this.elements.hierarchyPanel = panel;
        this.elements.hierarchyContent = content;
    }

    createDispatcherPanel() {
        const panel = document.createElement('div');
        panel.id = 'inspector-dispatcher-panel';
        panel.style.display = 'none';
        
        document.body.appendChild(panel);
        this.elements.dispatcherPanel = panel;

        // Close on click outside
        document.addEventListener('click', (e) => {
            if (panel.style.display !== 'none' && !panel.contains(e.target)) {
                // Check if the click target is inside a menu item that might have triggered this panel
                // This prevents the panel from closing immediately if it was just opened by a menu click
                // However, since the menu closes itself, we mainly care about clicks completely outside
                panel.style.display = 'none';
            }
        });
    }

    createStatusIndicator() {
        const container = document.createElement('div');
        container.id = 'inspector-controls';

        const createBtn = (id, icon, title, bg, handler) => {
            const btn = document.createElement('div');
            btn.id = id;
            btn.className = 'inspector-control-btn';
            btn.title = title;
            btn.style.background = bg;
            btn.innerHTML = icon;
            btn.onclick = handler;
            return btn;
        };

        // Main Menu Button (Center) - Plus icon that rotates to X
        const mainBtn = createBtn('inspector-main-btn',
            `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>`,
            'Inspector Menu', 'linear-gradient(135deg, #2196F3 0%, #1976D2 100%)',
            () => this.toggleMenu());

        // 1. Toggle Inspector
        const toggleBtn = createBtn('inspector-toggle-btn',
            `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="22" y1="12" x2="18" y2="12"/><line x1="6" y1="12" x2="2" y2="12"/><line x1="12" y1="6" x2="12" y2="2"/><line x1="12" y1="22" x2="12" y2="18"/></svg>`,
            'Toggle Inspector (Ctrl+Shift+E)', 'linear-gradient(135deg, #757575 0%, #616161 100%)',
            this.handlers.onToggle);
        toggleBtn.classList.add('sub-btn');

        // 2. Highlight
        const highlightBtn = createBtn('inspector-highlight-btn',
            `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="9" y1="9" x2="15" y2="9"/><line x1="9" y1="15" x2="15" y2="15"/></svg>`,
            'Toggle Highlight (Ctrl+Shift+H)', 'linear-gradient(135deg, #757575 0%, #616161 100%)',
            this.handlers.onHighlight);
        highlightBtn.classList.add('sub-btn');

        // 3. Hierarchy
        const hierarchyBtn = createBtn('inspector-hierarchy-btn',
            `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><polyline points="3 6 3 18"/></svg>`,
            'Show Full Hierarchy', 'linear-gradient(135deg, #757575 0%, #616161 100%)',
            this.handlers.onHierarchy);
        hierarchyBtn.classList.add('sub-btn');

        container.append(toggleBtn, highlightBtn, hierarchyBtn, mainBtn);
        document.body.appendChild(container);

        this.elements.mainBtn = mainBtn;
        this.elements.statusBtn = toggleBtn; // Used by ComponentInspector to update state
        this.elements.highlightBtn = highlightBtn;
        this.elements.hierarchyBtn = hierarchyBtn;
        this.elements.controlsContainer = container;
    }

    toggleMenu() {
        if (this.menuOpen) {
            this.closeMenu(true);
        } else {
            this.openMenu();
        }
    }

    openMenu() {
        this.menuOpen = true;
        this.elements.controlsContainer.classList.add('expanded');
    }

    closeMenu(reset = true) {
        this.menuOpen = false;
        this.elements.controlsContainer.classList.remove('expanded');
        if (reset) {
            this.handlers.onResetAll();
        }
    }

    // --- Dynamic Content Generators ---

    showTooltip(x, y, pmoClass, pmoProperty, selectedIndex, totalElements) {
        const t = this.elements.tooltip;
        t.style.display = 'block';
        t.style.left = (x + 15) + 'px';
        t.style.top = (y + 15) + 'px';
        const propertyText = pmoProperty ? `.${pmoProperty}` : '';

        let selectionInfo = '';
        if (totalElements > 1) {
            selectionInfo = `<div style="margin-bottom: 4px; font-size: 10px; color: #FF9800;">Element ${selectedIndex + 1} of ${totalElements} (Scroll to cycle)</div>`;
        }

        t.innerHTML = `
            ${selectionInfo}
            <div style="margin-bottom: 4px; opacity: 0.9; font-size: 11px;">Right-click for options</div>
            <div style="font-size: 13px;"><strong>${pmoClass.split('.').pop()}</strong>${propertyText}</div>
            <div style="margin-top: 4px; opacity: 0.8; font-size: 10px;">Ctrl+Shift+H to highlight all</div>
        `;
    }

    renderContextMenu(x, y, data) {
        const menu = this.elements.contextMenu;
        if (!data || !data.classLocation) {
            menu.innerHTML = '<div style="padding: 16px; color: #666;">No location data available</div>';
        } else {
            const pmoClass = data.classLocation.className.split('.').pop();
            const property = data.propertyName || null;
            const hasProperty = property && property !== 'null' && property !== '';

            let html = `
                <div style="padding: 16px; border-bottom: 1px solid #e0e0e0;">
                    <div style="font-weight: 600; font-size: 14px; color: #1976D2; margin-bottom: 4px;">${pmoClass}</div>
                    ${hasProperty ? `<div style="font-size: 12px; color: #666;">${property}</div>` : '<div style="font-size: 12px; color: #666;">Section</div>'}
                </div>
                <div style="padding: 8px 0;">
            `;

            const createItem = (title, subtitle, action, icon) => `
                <div class="inspector-menu-item" data-action="${action}">
                    <div style="color: #1976D2; display: flex; align-items: center;">${icon}</div>
                    <div style="flex: 1;">
                        <div style="font-weight: 500; font-size: 13px; color: #212121;">${title}</div>
                        <div style="font-size: 11px; color: #757575; margin-top: 2px;">${subtitle}</div>
                    </div>
                </div>`;

            if (hasProperty) {
                html += createItem('Open Property', `Jump to ${property}`, 'open-property',
                    `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M12 1v6m0 6v6M5.6 5.6l4.2 4.2m4.2 4.2l4.2 4.2M1 12h6m6 0h6M5.6 18.4l4.2-4.2m4.2-4.2l4.2-4.2"/></svg>`);
            }
            html += createItem('Open Class', `Jump to class ${pmoClass}`, 'open-class',
                `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`);

            if (data.instantiationLocation) {
                html += createItem('Open Instantiation', `Where object was created`, 'open-instantiation',
                    `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>`);
            }

            if (data.dispatcherHistory && data.dispatcherHistory.length > 0) {
                html += createItem('Aspect Overview', `View aspect execution details`, 'show-dispatcher-history',
                    `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>`);
            }

            html += '</div>';
            menu.innerHTML = html;
        }

        // Positioning logic
        const menuX = Math.min(x, window.innerWidth - 340);
        const menuY = Math.min(y, window.innerHeight - 500);
        menu.style.left = menuX + 'px';
        menu.style.top = menuY + 'px';
        menu.style.display = 'block';

        // Attach events
        menu.querySelectorAll('.inspector-menu-item').forEach(item => {
            item.onclick = (e) => {
                e.stopPropagation(); // Prevent immediate closing of opened panels
                const action = item.getAttribute('data-action');
                this.handlers.onMenuAction(action, data);
                menu.style.display = 'none';
            };
        });
    }

    renderDispatcherHistory(history) {
        let panel = this.elements.dispatcherPanel;
        // If for some reason it wasn't created (legacy support?), create it
        if (!panel) {
            this.createDispatcherPanel();
            panel = this.elements.dispatcherPanel;
        }

        const header = `
            <div class="dispatcher-header">
                <span class="dispatcher-header-title">Aspect Overview</span>
                <button id="inspector-close-dispatcher">&times;</button>
            </div>
        `;

        let content = '<div class="dispatcher-content">';

        if (!history || history.length === 0) {
            content += '<div class="dispatcher-empty">No aspect history recorded.</div>';
        } else {
            history.slice().reverse().forEach(record => {
                content += `
                    <details class="aspect-details dispatcher-record">
                        <summary class="dispatcher-summary">
                            <div class="dispatcher-aspect-name">${record.aspectName}</div>
                            
                            <div class="dispatcher-result-container">
                                <span class="dispatcher-result">${record.result}</span>
                            </div>

                            <div class="aspect-arrow">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <polyline points="6 9 12 15 18 9"></polyline>
                                </svg>
                            </div>
                        </summary>
                        
                        <div class="dispatcher-details-body">
                            <div class="dispatcher-chain-container">
                                ${Object.entries(record.breakdown).map(([cls, val]) => `
                                    <div class="dispatcher-chain-item">
                                        <span class="dispatcher-chain-class">${cls}</span>
                                        <span class="dispatcher-chain-value">${val}</span>
                                    </div>
                                `).join('')}
                            </div>
                        </div>
                    </details>
                `;
            });
        }
        content += '</div>';

        panel.innerHTML = '';
        panel.insertAdjacentHTML('beforeend', header + content);
        
        panel.style.display = 'flex';

        // Close handler
        panel.querySelector('#inspector-close-dispatcher').onclick = () => {
            panel.style.display = 'none';
        };
    }

    setHierarchyPanelVisible(visible) {
        const panel = this.elements.hierarchyPanel;
        const btn = this.elements.hierarchyBtn;
        
        if (visible) {
            panel.style.display = 'flex';
            if (btn) btn.style.background = 'linear-gradient(135deg, #009688 0%, #00796B 100%)';
        } else {
            panel.style.display = 'none';
            if (btn) btn.style.background = 'linear-gradient(135deg, #757575 0%, #616161 100%)';
        }
    }

    renderHierarchy(rootNode) {
        if (!rootNode) return;
        this.elements.hierarchyContent.innerHTML = this.buildHierarchyHTML(rootNode);
        this.setHierarchyPanelVisible(true);
        this.attachHierarchyHandlers();
    }

    buildHierarchyHTML(node) {
        const children = node.children || [];
        const hasChildren = children.length > 0;
        const displayClassName = node.className;
        const displayPropName = node.propertyName;
        const locationData = node.propertyLocation || node.classLocation;
        const isNavigable = !!locationData;

        // Data attributes for click handling
        let dataAttrs = '';
        if (isNavigable) {
            dataAttrs = `data-classname="${node.fullClassName}" 
                         data-linenumber="${locationData.lineNumber}" 
                         data-filename="${locationData.fileName}" 
                         data-outermost-class="${locationData.outermostClassName || node.fullClassName}"`;
        }

        const classColor = isNavigable ? '#1565C0' : '#757575';
        const labelHtml = displayPropName
            ? `<span style="font-weight: 600; color: #212121; font-family: monospace; font-size: 13px;">${displayPropName}</span><span style="margin: 0 4px; color: #999;">:</span><span style="color: ${classColor}; font-size: 12px; font-family: monospace;">${displayClassName}</span>`
            : `<span style="font-weight: 600; color: ${classColor}; font-size: 13px;">${displayClassName}</span>`;

        const icon = !hasChildren
            ? `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="${isNavigable ? (displayPropName ? '#FF9800' : '#1565C0') : '#9E9E9E'}" stroke-width="3"><circle cx="12" cy="12" r="4"/></svg>`
            : '';

        let instantiationHtml = '';
        if (node.hasInstantiationLocation && node.instantiationLocation) {
            const il = node.instantiationLocation;
            instantiationHtml = `
            <div class="hierarchy-action-btn" title="Jump to Instantiation"
                 data-action="instantiation"
                 data-filename="${il.fileName}"
                 data-linenumber="${il.lineNumber}"
                 data-classname="${il.className}"
                 data-outermost="${il.outermostClassName || il.className}">
                 <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"></path>
                 </svg>
            </div>`;
        }

        // Dispatcher History Button in Hierarchy
        let historyHtml = '';
        if (displayPropName) {
            historyHtml = `
            <div class="hierarchy-action-btn" title="Show Aspect Overview"
                 data-action="history"
                 data-component-id="${node.componentId || ''}"> 
                 <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
                 </svg>
            </div>`;
        }

        const expanderHtml = hasChildren
            ? `<div class="hierarchy-expander open"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#546E7A" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg></div>`
            : `<div class="hierarchy-expander empty"></div>`;

        let html = `<div class="hierarchy-node" data-component-id="${node.componentId || ''}">
            <div class="hierarchy-row">${expanderHtml}
                <div class="${isNavigable ? 'hierarchy-content navigable' : 'hierarchy-content system'}" ${dataAttrs} title="${displayClassName}">
                    ${icon}<div style="display: flex; align-items: center; white-space: nowrap;">${labelHtml}${instantiationHtml}${historyHtml}</div>
                </div>
            </div>`;

        if (hasChildren) {
            html += `<div class="hierarchy-children">${children.map(c => this.buildHierarchyHTML(c)).join('')}</div>`;
        }
        html += `</div>`;
        return html;
    }

    attachHierarchyHandlers() {
        // Expander logic
        this.elements.hierarchyContent.querySelectorAll('.hierarchy-expander:not(.empty)').forEach(exp => {
            exp.onclick = (e) => {
                e.stopPropagation();
                const container = exp.closest('.hierarchy-node').querySelector('.hierarchy-children');
                if (container) {
                    if (container.classList.contains('hidden')) {
                        container.classList.remove('hidden');
                        exp.classList.add('open');
                    } else {
                        container.classList.add('hidden');
                        exp.classList.remove('open');
                    }
                }
            };
        });

        // Click on class name logic
        this.elements.hierarchyContent.querySelectorAll('.hierarchy-content.navigable').forEach(item => {
            item.onclick = (e) => {
                if (e.target.closest('.hierarchy-action-btn')) return;

                const loc = {
                    className: item.getAttribute('data-classname'),
                    outermostClassName: item.getAttribute('data-outermost-class'),
                    fileName: item.getAttribute('data-filename'),
                    lineNumber: parseInt(item.getAttribute('data-linenumber')) || 1
                };
                this.handlers.onNodeClick(loc);
            };
        });

        // Click on Instantiation button logic
        this.elements.hierarchyContent.querySelectorAll('.hierarchy-action-btn[data-action="instantiation"]').forEach(btn => {
            btn.onclick = (e) => {
                e.stopPropagation();
                const loc = {
                    className: btn.getAttribute('data-classname'),
                    outermostClassName: btn.getAttribute('data-outermost'),
                    fileName: btn.getAttribute('data-filename'),
                    lineNumber: parseInt(btn.getAttribute('data-linenumber')) || 1
                };
                this.handlers.onInstantiationClick(loc);
            };
        });

        // Click on History button logic
        this.elements.hierarchyContent.querySelectorAll('.hierarchy-action-btn[data-action="history"]').forEach(btn => {
            btn.onclick = (e) => {
                e.stopPropagation();
                const componentId = btn.closest('.hierarchy-node').getAttribute('data-component-id');
                if (componentId) {
                    this.handlers.onMenuAction('show-dispatcher-history', {componentId: componentId});
                }
            };
        });

        // Hover logic for hierarchy rows
        this.elements.hierarchyContent.querySelectorAll('.hierarchy-row').forEach(row => {
            row.onmouseenter = () => {
                const node = row.closest('.hierarchy-node');
                const componentId = node.getAttribute('data-component-id');
                if (componentId) this.handlers.onNodeHover(componentId);
            };
            row.onmouseleave = () => {
                this.handlers.onNodeHoverOut();
            };
        });
    }
}