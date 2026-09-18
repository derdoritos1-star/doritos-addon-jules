package com.example.addon.modules;

import com.example.addon.DoritosAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

public class FreecamTweaks extends Module {
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> continueWalking = sgMovement.add(new BoolSetting.Builder()
        .name("continue-walking")
        .description("Keep the player walking in the last direction when freecam starts.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> continueSneak = sgMovement.add(new BoolSetting.Builder()
        .name("continue-sneak")
        .description("Keep the player sneaking while in freecam.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> refreshChunks = sgRender.add(new BoolSetting.Builder()
        .name("refresh-chunks")
        .description("Force reload all chunks when freecam activates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> walkTicks = sgMovement.add(new IntSetting.Builder()
        .name("walk-duration")
        .description("How many ticks to keep walking (0 = infinite).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 200)
        .visible(continueWalking::get)
        .build()
    );

    private int walkTimer;
    private boolean wasFreecamActive;

    public FreecamTweaks() {
        super(DoritosAddon.CATEGORY, "FreecamTweaks", "Adds auto-walk, auto-sneak, and chunk reloading to Freecam.");
    }

    @Override
    public void onActivate() {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null && !freecam.isActive()) {
            freecam.toggle();
        }

        wasFreecamActive = true;
        walkTimer = walkTicks.get();

        if (refreshChunks.get() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }

        if (continueWalking.get() && mc.player != null) {
            float yaw = mc.player.getYaw();
            double dx = -Math.sin(Math.toRadians(yaw));
            double dz = Math.cos(Math.toRadians(yaw));
            mc.player.setVelocity(dx * 0.2, mc.player.getVelocity().y, dz * 0.2);
        }
    }

    @Override
    public void onDeactivate() {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null && freecam.isActive()) {
            freecam.toggle();
        }

        if (mc.player != null) {
            mc.player.setSneaking(false);
            mc.options.sneakKey.setPressed(false);
            mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
        }

        wasFreecamActive = false;
        walkTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!wasFreecamActive || mc.player == null) return;

        if (continueSneak.get()) {
            mc.options.sneakKey.setPressed(true);
            mc.player.setSneaking(true);
        }

        if (continueWalking.get() && (walkTimer > 0 || walkTicks.get() == 0)) {
            float yaw = mc.player.getYaw();
            double dx = -Math.sin(Math.toRadians(yaw));
            double dz = Math.cos(Math.toRadians(yaw));
            mc.player.setVelocity(dx * 0.2, mc.player.getVelocity().y, dz * 0.2);

            if (walkTicks.get() > 0) {
                walkTimer--;
            }
        }
    }
}
