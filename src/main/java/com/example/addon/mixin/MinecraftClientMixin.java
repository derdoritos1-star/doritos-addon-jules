package com.example.addon.mixin;

import com.example.addon.modules.FreecamTweaks;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    private Entity cachedCameraEntity;

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void onDoAttackHead(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        FreecamTweaks tweaks = Modules.get().get(FreecamTweaks.class);
        if (tweaks != null && tweaks.isActive() && tweaks.interactFromPlayer.get()) {
            cachedCameraEntity = mc.getCameraEntity();
            mc.setCameraEntity(mc.player);
        }
    }

    @Inject(method = "doAttack", at = @At("RETURN"))
    private void onDoAttackReturn(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cachedCameraEntity != null) {
            mc.setCameraEntity(cachedCameraEntity);
            cachedCameraEntity = null;
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void onDoItemUseHead(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        FreecamTweaks tweaks = Modules.get().get(FreecamTweaks.class);
        if (tweaks != null && tweaks.isActive() && tweaks.interactFromPlayer.get()) {
            cachedCameraEntity = mc.getCameraEntity();
            mc.setCameraEntity(mc.player);
        }
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void onDoItemUseReturn(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cachedCameraEntity != null) {
            mc.setCameraEntity(cachedCameraEntity);
            cachedCameraEntity = null;
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"))
    private void onHandleBlockBreakingHead(boolean bl, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        FreecamTweaks tweaks = Modules.get().get(FreecamTweaks.class);
        if (tweaks != null && tweaks.isActive() && tweaks.interactFromPlayer.get()) {
            cachedCameraEntity = mc.getCameraEntity();
            mc.setCameraEntity(mc.player);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("RETURN"))
    private void onHandleBlockBreakingReturn(boolean bl, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cachedCameraEntity != null) {
            mc.setCameraEntity(cachedCameraEntity);
            cachedCameraEntity = null;
        }
    }
}
