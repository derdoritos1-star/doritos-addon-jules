package com.example.addon;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class DoritosTheme extends MeteorGuiTheme {
    public DoritosTheme() {
        super();
        try {
            java.lang.reflect.Field nameField = meteordevelopment.meteorclient.gui.GuiTheme.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(this, "Doritos");
        } catch (Exception e) {}



        // Custom "Doritos" theme colors (Orange/Red palette)
        this.accentColor.set(new SettingColor(255, 140, 0, 255));      // Orange
        this.checkboxColor.set(new SettingColor(255, 69, 0, 255));     // Red-Orange
        this.sliderLeft.set(new SettingColor(255, 140, 0, 255));
        this.sliderRight.set(new SettingColor(60, 60, 60, 255));

        this.backgroundColor.get().set(new SettingColor(30, 25, 20, 245));   // Dark brownish background
        this.moduleBackground.set(new SettingColor(40, 35, 30, 255));

        this.titleTextColor.set(new SettingColor(255, 200, 0, 255));   // Yellow-ish text for title
        this.textColor.set(new SettingColor(250, 240, 230, 255));
        this.textHighlightColor.set(new SettingColor(255, 140, 0, 255));

        this.outlineColor.get().set(new SettingColor(200, 100, 0, 255));     // Orange outline
        this.separatorCenter.set(new SettingColor(255, 140, 0, 255));
        this.separatorEdges.set(new SettingColor(30, 25, 20, 0));
    }
}
