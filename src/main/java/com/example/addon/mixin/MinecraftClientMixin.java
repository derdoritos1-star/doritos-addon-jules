package com.example.addon.mixin;

import com.example.addon.modules.DoritosFreecamera;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    private Entity cachedCameraEntity;
    private HitResult cachedCrosshairTarget;

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void onDoAttackHead(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        DoritosFreecamera tweaks = Modules.get().get(DoritosFreecamera.class);
        if (tweaks != null && tweaks.isActive() && tweaks.interactFromPlayer.get() && mc.player != null) {
            cachedCameraEntity = mc.getCameraEntity();
            mc.setCameraEntity(mc.player);

            cachedCrosshairTarget = mc.crosshairTarget;
            mc.crosshairTarget = mc.player.raycast(5.0, 1.0F, false);
        }
    }

    @Inject(method = "doAttack", at = @At("RETURN"))
    private void onDoAttackReturn(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cachedCameraEntity != null) {
            mc.setCameraEntity(cachedCameraEntity);
            cachedCameraEntity = null;
        }
        if (cachedCrosshairTarget != null) {
            mc.crosshairTarget = cachedCrosshairTarget;
            cachedCrosshairTarget = null;
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void onDoItemUseHead(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        DoritosFreecamera tweaks = Modules.get().get(DoritosFreecamera.class);
        if (tweaks != null && tweaks.isActive() && tweaks.interactFromPlayer.get() && mc.player != null) {
            cachedCameraEntity = mc.getCameraEntity();
            mc.setCameraEntity(mc.player);

            cachedCrosshairTarget = mc.crosshairTarget;
            mc.crosshairTarget = mc.player.raycast(5.0, 1.0F, false);
        }
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void onDoItemUseReturn(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cachedCameraEntity != null) {
            mc.setCameraEntity(cachedCameraEntity);
            cachedCameraEntity = null;
        }
        if (cachedCrosshairTarget != null) {
            mc.crosshairTarget = cachedCrosshairTarget;
            cachedCrosshairTarget = null;
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"))
    private void onHandleBlockBreakingHead(boolean bl, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        DoritosFreecamera tweaks = Modules.get().get(DoritosFreecamera.class);
        if (tweaks != null && tweaks.isActive() && tweaks.interactFromPlayer.get() && mc.player != null) {
            cachedCameraEntity = mc.getCameraEntity();
            mc.setCameraEntity(mc.player);

            cachedCrosshairTarget = mc.crosshairTarget;
            mc.crosshairTarget = mc.player.raycast(5.0, 1.0F, false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("RETURN"))
    private void onHandleBlockBreakingReturn(boolean bl, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cachedCameraEntity != null) {
            mc.setCameraEntity(cachedCameraEntity);
            cachedCameraEntity = null;
        }
        if (cachedCrosshairTarget != null) {
            mc.crosshairTarget = cachedCrosshairTarget;
            cachedCrosshairTarget = null;
        }
    }
}
