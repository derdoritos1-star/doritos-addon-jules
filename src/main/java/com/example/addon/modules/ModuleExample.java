package com.example.addon.modules;

import com.example.addon.DoritosAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

public class ModuleExample extends Module {
    public ModuleExample() {
        super(DoritosAddon.CATEGORY, "module-example", "Example module.");
    }
}
