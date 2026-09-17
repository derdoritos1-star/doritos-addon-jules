
package com.example.addon;

import com.example.addon.modules.SusChunkFinder;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.ModuleExample;
import com.example.addon.modules.NetheriteFinder;
import com.example.addon.modules.DonutSpawnerFinder;

import com.mojang.logging.LogUtils;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.gui.GuiThemes;


import org.slf4j.Logger;

public class DoritosAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category CATEGORY = new Category("Doritos");
    public static final HudGroup HUD_GROUP = new HudGroup("Doritos");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Doritos Addon");

        Modules.get().add(new ModuleExample());
        Modules.get().add(new NetheriteFinder());
        Modules.get().add(new DonutSpawnerFinder());
        Modules.get().add(new SusChunkFinder());

        Hud.get().register(HudExample.INFO);

        GuiThemes.add(new DoritosTheme());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
