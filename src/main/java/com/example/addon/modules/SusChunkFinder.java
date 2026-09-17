package com.example.addon.modules;

import com.example.addon.DoritosAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.block.Block;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    public enum LoggingMode { Chat, ActionBar, Toast, None }

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder().name("scan-radius").description("Radius in chunks to scan around player.").defaultValue(4).sliderRange(1, 32).build());
    private final Setting<Integer> anomalyThreshold = sgGeneral.add(new IntSetting.Builder().name("anomaly-threshold").description("Score threshold for palette inconsistency.").defaultValue(50).sliderRange(1, 200).build());
    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder().name("min-y").defaultValue(-64).sliderRange(-64, 320).build());
    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder().name("max-y").defaultValue(320).sliderRange(-64, 320).build());

    private final Setting<SettingColor> chunkGridColor = sgRender.add(new ColorSetting.Builder().name("chunk-grid-color").description("Color of the highlighted chunk borders.").defaultValue(new SettingColor(0, 255, 0, 255)).build());

    private final Setting<LoggingMode> loggingMode = sgNotifications.add(new EnumSetting.Builder<LoggingMode>().name("logging-mode").description("How to notify when a sus chunk is found.").defaultValue(LoggingMode.Chat).build());

    private final Set<ChunkPos> alertedChunks = ConcurrentHashMap.newKeySet();

    public SusChunkFinder() {
        super(DoritosAddon.CATEGORY, "SusChunkFinder", "Finds suspicious chunks on DonutSMP. NOTE: Below Y=0 (Deepslate), servers heavily obfuscate blocks. You must manually dig down to load them!");
    }

    @Override
    public void onActivate() {
        alertedChunks.clear();
    }

    @Override
    public void onDeactivate() {
        alertedChunks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        ChunkPos playerChunk = mc.player.getChunkPos();
        int radius = scanRadius.get();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int cx = playerChunk.x + x;
                int cz = playerChunk.z + z;

                if (mc.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    net.minecraft.world.chunk.WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk != null) {
                        processChunk(chunk);
                    }
                }
            }
        }
    }

    private void processChunk(net.minecraft.world.chunk.WorldChunk chunk) {
        ChunkPos cPos = chunk.getPos();
        if (alertedChunks.contains(cPos)) return;

        int score = 0;

        for (int i = 0; i < chunk.getSectionArray().length; i++) {
            ChunkSection section = chunk.getSectionArray()[i];
            if (section == null || section.isEmpty()) continue;

            int sectionY = chunk.getBottomSectionCoord() + i;
            int worldYStart = sectionY * 16;

            if (worldYStart > maxY.get() || (worldYStart + 15) < minY.get()) continue;

            // False Positive Mitigation: Ignore natural anomalies
            if (section.hasAny(state -> state.isOf(Blocks.AMETHYST_BLOCK) ||
                                        state.isOf(Blocks.BONE_BLOCK) ||
                                        state.isOf(Blocks.SCULK) ||
                                        state.isOf(Blocks.SPAWNER))) {
                return; // Entire chunk is skipped if natural anomalies are found
            }

            // Palette inconsistency detection
            score += section.getBlockStateContainer().getPacketSize();

            // Unnatural block generation checks below Y=0 (Deepslate layer)
            if (worldYStart < 0) {
                if (section.hasAny(state -> state.isOf(Blocks.AIR))) {
                    score += 20; // Finding standard air underground is suspicious (should be CAVE_AIR)
                }
            }
        }

        if (score >= anomalyThreshold.get()) {
            if (alertedChunks.add(cPos)) {
                sendNotification(cPos, score);
            }
        }
    }

    private void sendNotification(ChunkPos cPos, int score) {
        String msg = String.format("Sus chunk found at %d, %d (score: %d)", cPos.getStartX(), cPos.getStartZ(), score);
        switch (loggingMode.get()) {
            case Chat:
                ChatUtils.info(msg);
                break;
            case ActionBar:
                if (mc.player != null) mc.player.sendMessage(Text.literal(msg), true);
                break;
            case Toast:
                if (mc.getToastManager() != null) {
                    mc.getToastManager().add(SystemToast.create(mc, SystemToast.Type.NARRATOR_TOGGLE, Text.literal("Sus Chunk Finder"), Text.literal(msg)));
                }
                break;
            case None:
            default:
                break;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (ChunkPos cPos : alertedChunks) {
            double minX = cPos.getStartX();
            double minZ = cPos.getStartZ();
            double maxX = cPos.getEndX() + 1.0;
            double maxZ = cPos.getEndZ() + 1.0;
            double minY = -64.0;
            double maxY = 320.0;

            event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, chunkGridColor.get(), chunkGridColor.get(), ShapeMode.Lines, 0);
        }
    }
}
