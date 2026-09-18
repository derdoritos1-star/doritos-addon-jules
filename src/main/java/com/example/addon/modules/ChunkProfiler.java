package com.example.addon.modules;

import com.example.addon.DoritosAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkProfiler extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> maxEntities = sgGeneral.add(new IntSetting.Builder().name("max-entities").description("Max entities before a chunk is flagged.").defaultValue(50).sliderRange(10, 300).build());
    private final Setting<Integer> maxBlockEntities = sgGeneral.add(new IntSetting.Builder().name("max-block-entities").description("Max block entity updates before a chunk is flagged.").defaultValue(100).sliderRange(10, 500).build());
    private final Setting<Integer> clearDelay = sgGeneral.add(new IntSetting.Builder().name("clear-delay").description("Ticks to wait before clearing the block entity cache.").defaultValue(200).sliderRange(20, 1200).build());

    private final Setting<Boolean> renderGrid = sgRender.add(new BoolSetting.Builder().name("render-grid").description("Renders a 3D grid around flagged chunks.").defaultValue(true).build());
    private final Setting<SettingColor> gridColor = sgRender.add(new ColorSetting.Builder().name("grid-color").description("Color of the chunk grid.").defaultValue(new SettingColor(255, 0, 0, 100)).build());
    private final Setting<SettingColor> gridOutline = sgRender.add(new ColorSetting.Builder().name("grid-outline").description("Outline color of the chunk grid.").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Map<ChunkPos, Integer> blockEntityCounts = new ConcurrentHashMap<>();
    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();

    private int ticksPassed = 0;

    public ChunkProfiler() {
        super(DoritosAddon.CATEGORY, "ChunkProfiler", "Profiles chunks to detect lag machines or dense entity clusters.");
    }

    @Override
    public void onActivate() {
        blockEntityCounts.clear();
        flaggedChunks.clear();
        ticksPassed = 0;
    }

    @Override
    public void onDeactivate() {
        blockEntityCounts.clear();
        flaggedChunks.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof BlockEntityUpdateS2CPacket packet) {
            BlockPos pos = packet.getPos();
            ChunkPos cPos = new ChunkPos(pos);

            int count = blockEntityCounts.getOrDefault(cPos, 0) + 1;
            blockEntityCounts.put(cPos, count);

            if (count >= maxBlockEntities.get() && flaggedChunks.add(cPos)) {
                ChatUtils.info("Flagged chunk for excessive BlockEntities: " + cPos.getStartX() + ", " + cPos.getStartZ());
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        ticksPassed++;
        if (ticksPassed >= clearDelay.get()) {
            blockEntityCounts.clear();
            ticksPassed = 0;
        }

        // Periodically check loaded entities
        if (mc.player.age % 20 == 0) {
            Map<ChunkPos, Integer> entityCounts = new java.util.HashMap<>();
            for (Entity entity : mc.world.getEntities()) {
                ChunkPos cPos = entity.getChunkPos();
                entityCounts.put(cPos, entityCounts.getOrDefault(cPos, 0) + 1);
            }

            for (Map.Entry<ChunkPos, Integer> entry : entityCounts.entrySet()) {
                if (entry.getValue() >= maxEntities.get() && flaggedChunks.add(entry.getKey())) {
                    ChatUtils.info("Flagged chunk for excessive Entities: " + entry.getKey().getStartX() + ", " + entry.getKey().getStartZ() + " (" + entry.getValue() + " entities)");
                }
            }

            // Auto-clear chunks that are far away to prevent memory leaks
            ChunkPos playerChunk = mc.player.getChunkPos();
            flaggedChunks.removeIf(cPos -> Math.abs(cPos.x - playerChunk.x) > 16 || Math.abs(cPos.z - playerChunk.z) > 16);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || !renderGrid.get()) return;

        for (ChunkPos cPos : flaggedChunks) {
            double minX = cPos.getStartX();
            double minZ = cPos.getStartZ();
            double maxX = minX + 16;
            double maxZ = minZ + 16;

            // Render full chunk column outline
            event.renderer.box(minX, -64, minZ, maxX, 320, maxZ, gridColor.get(), gridOutline.get(), ShapeMode.Both, 0);
        }
    }
}
