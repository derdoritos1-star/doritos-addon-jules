package com.example.addon.modules.sus;

import com.example.addon.DoritosAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;

public class SusChunkFinder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    // Config
    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder().name("render-distance").description("Distance to render confirmed chunks.").defaultValue(10).sliderRange(1, 64).build());

    // Render
    private final Setting<Boolean> renderCandidates = sgRender.add(new BoolSetting.Builder().name("render-candidates").description("Render chunks that are unconfirmed but suspicious.").defaultValue(false).build());
    private final Setting<SettingColor> candidateColor = sgRender.add(new ColorSetting.Builder().name("candidate-color").description("Color for candidate chunks.").defaultValue(new SettingColor(255, 255, 0, 100)).build());
    private final Setting<Boolean> renderConfirmed = sgRender.add(new BoolSetting.Builder().name("render-confirmed").description("Render highly likely player bases.").defaultValue(true).build());
    private final Setting<SettingColor> confirmedColor = sgRender.add(new ColorSetting.Builder().name("confirmed-color").description("Color for confirmed bases.").defaultValue(new SettingColor(255, 0, 0, 150)).build());

    // Debug
    public final Setting<Boolean> debugMode = sgDebug.add(new BoolSetting.Builder().name("debug-mode").description("Prints extensive chunk scoring data to chat.").defaultValue(false).build());

    private final SusChunkEngine engine;

    public SusChunkFinder() {
        super(DoritosAddon.CATEGORY, "SusChunkFinder", "High-precision base detector. Analyzes spatial structure, ignores natural terrain.");
        this.engine = new SusChunkEngine(this);
    }

    @Override
    public void onActivate() {
        engine.reset();
    }

    @Override
    public void onDeactivate() {
        engine.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        engine.tick(mc.world, mc.player, 4); // TODO extract radius to setting
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;
        engine.markDirty(event.chunk().getPos());
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null) return;

        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            engine.markDirty(new ChunkPos(packet.getPos()));
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates((pos, state) -> engine.markDirty(new ChunkPos(pos)));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        ChunkPos playerPos = mc.player.getChunkPos();
        int maxDist = renderDistance.get();

        for (Map.Entry<ChunkPos, SusChunkEngine.ChunkData> entry : engine.getTrackedChunks().entrySet()) {
            ChunkPos pos = entry.getKey();
            if (Math.abs(pos.x - playerPos.x) > maxDist || Math.abs(pos.z - playerPos.z) > maxDist) continue;

            SusChunkEngine.ChunkData data = entry.getValue();

            if (data.state == SusChunkEngine.ConfidenceState.CONFIRMED && renderConfirmed.get()) {
                drawFlatPlatform(event, pos, confirmedColor.get());
            } else if (data.state == SusChunkEngine.ConfidenceState.CANDIDATE && renderCandidates.get()) {
                drawFlatPlatform(event, pos, candidateColor.get());
            }
        }
    }

    private void drawFlatPlatform(Render3DEvent event, ChunkPos pos, SettingColor color) {
        int cx = pos.getStartX();
        int cz = pos.getStartZ();
        double cy = mc.world.getBottomY() + 0.1;

        event.renderer.box(cx, cy, cz, cx + 16, cy + 0.1, cz + 16, color, color, ShapeMode.Sides, 0);
    }
}
