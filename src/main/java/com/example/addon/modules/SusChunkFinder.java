package com.example.addon.modules;

import com.example.addon.DoritosAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    public enum LoggingMode { Chat, ActionBar, Toast, None }
    public enum TriggerMode { All, Redstone, StorageBase }

    private final Setting<TriggerMode> triggerMode = sgGeneral.add(new EnumSetting.Builder<TriggerMode>().name("trigger-mode").description("What to search for.").defaultValue(TriggerMode.All).build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder().name("scan-radius").description("Radius in chunks to scan around player.").defaultValue(4).sliderRange(1, 32).build());
    private final Setting<Integer> anomalyThreshold = sgGeneral.add(new IntSetting.Builder().name("anomaly-threshold").description("Score threshold for alerting.").defaultValue(50).sliderRange(1, 1000).build());

    private final Setting<Integer> minFarmEntities = sgGeneral.add(new IntSetting.Builder().name("min-farm-entities").description("Minimum passive mobs to flag a farm.").defaultValue(15).sliderRange(5, 100).build());
    private final Setting<Integer> surfaceTraceMaxY = sgGeneral.add(new IntSetting.Builder().name("surface-trace-max-y").description("Maximum Y level to scan for unobfuscated trace blocks (Glass, Beds).").defaultValue(50).sliderRange(0, 320).build());

    private final Setting<SettingColor> chunkGridColor = sgRender.add(new ColorSetting.Builder().name("chunk-grid-color").description("Color of the highlighted chunk borders.").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Setting<Boolean> drawTracers = sgRender.add(new BoolSetting.Builder().name("draw-tracers").description("Draw lines from player to flagged chunks.").defaultValue(true).build());
    private final Setting<Boolean> drawBeacon = sgRender.add(new BoolSetting.Builder().name("draw-beacon").description("Draw highlight on the flagged chunk.").defaultValue(true).build());

    private final Setting<LoggingMode> loggingMode = sgNotifications.add(new EnumSetting.Builder<LoggingMode>().name("logging-mode").description("How to notify when a sus chunk is found.").defaultValue(LoggingMode.Chat).build());

    private final Map<ChunkPos, Integer> chunkScores = new ConcurrentHashMap<>();
    private final Set<ChunkPos> alertedChunks = ConcurrentHashMap.newKeySet();

    // Ultimate Tracking State
    private final List<ChunkPos> alertHistory = new ArrayList<>();
    private final List<BlockPos> soundHeatmap = new ArrayList<>();
    private ChunkPos predictedVector = null;

    public SusChunkFinder() {
        super(DoritosAddon.CATEGORY, "SusChunkFinder", "Sub-Zero Target Detector. Exploits anti-xray flaws below Y=0.");
    }

    @Override
    public void onActivate() {
        chunkScores.clear();
        alertedChunks.clear();
        alertHistory.clear();
        soundHeatmap.clear();
        predictedVector = null;

        if (mc.world != null && mc.player != null) {
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
    }
    @Override
    public void onDeactivate() {
        chunkScores.clear();
        alertedChunks.clear();
        alertHistory.clear();
        soundHeatmap.clear();
        predictedVector = null;
    }

    private void addScore(ChunkPos pos, int score) {
        if (alertedChunks.contains(pos)) return;

        int newScore = chunkScores.merge(pos, score, Integer::sum);
        if (newScore >= anomalyThreshold.get() && alertedChunks.add(pos)) {
            sendNotification(pos, newScore);

            // Track history for Vector Prediction
            if (alertHistory.size() >= 5) alertHistory.remove(0);
            alertHistory.add(pos);

            // Calculate Tunnel Vector if we have at least 3 points
            if (alertHistory.size() >= 3) {
                ChunkPos p1 = alertHistory.get(alertHistory.size() - 3);
                ChunkPos p2 = alertHistory.get(alertHistory.size() - 2);
                ChunkPos p3 = alertHistory.get(alertHistory.size() - 1);

                int dx1 = p2.x - p1.x;
                int dz1 = p2.z - p1.z;
                int dx2 = p3.x - p2.x;
                int dz2 = p3.z - p2.z;

                // Vector collinearity check (Cross product == 0 indicates identical line trajectory)
                // Dot product check ensures the direction hasn't reversed (180 degree turn)
                if ((dx1 * dz2 - dz1 * dx2) == 0 && (dx1 * dx2 + dz1 * dz2) > 0 && (dx1 != 0 || dz1 != 0)) {
                    predictedVector = new ChunkPos(p3.x + (dx2 * 10), p3.z + (dz2 * 10)); // Project 10 chunks forward
                } else {
                    predictedVector = null; // Trajectory broken, clear prediction
                }
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;
        processChunk(event.chunk());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (mc.player.age % 20 != 0) return; // OPTIMIZATION: Only run 1 time per second

        ChunkPos playerChunk = mc.player.getChunkPos();

        // Clear ghost chunks after /rtp
        alertedChunks.removeIf(cPos -> Math.abs(cPos.x - playerChunk.x) > 32 || Math.abs(cPos.z - playerChunk.z) > 32);
        chunkScores.keySet().removeIf(cPos -> Math.abs(cPos.x - playerChunk.x) > 32 || Math.abs(cPos.z - playerChunk.z) > 32);
        if (alertedChunks.isEmpty()) { alertHistory.clear(); soundHeatmap.clear(); predictedVector = null; }

        // Entity Sniffing (Lead Detection)
        TriggerMode mode = triggerMode.get();
        if (mode == TriggerMode.All || mode == TriggerMode.StorageBase) {
            Map<ChunkPos, Integer> entityCounts = new java.util.HashMap<>();
            Map<ChunkPos, Integer> minecartCounts = new java.util.HashMap<>();

            for (Entity entity : mc.world.getEntities()) {
                ChunkPos cPos = entity.getChunkPos();
                if (alertedChunks.contains(cPos)) continue;

                if (entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity) {
                    addScore(cPos, anomalyThreshold.get()); // Instant alert for frames/armor stands
                } else if (entity.hasCustomName()) {
                    addScore(cPos, anomalyThreshold.get()); // Named entities = guaranteed player
                } else if (entity instanceof TameableEntity tameable && tameable.isTamed()) {
                    addScore(cPos, anomalyThreshold.get()); // Pets = guaranteed base
                } else if (entity instanceof ChestMinecartEntity || entity instanceof HopperMinecartEntity) {
                    minecartCounts.put(cPos, minecartCounts.getOrDefault(cPos, 0) + 1);
                } else if (entity instanceof PassiveEntity || entity instanceof VillagerEntity) {
                    entityCounts.put(cPos, entityCounts.getOrDefault(cPos, 0) + 1);
                }
            }

            for (Map.Entry<ChunkPos, Integer> entry : entityCounts.entrySet()) {
                if (entry.getValue() >= minFarmEntities.get()) { // Confirmed farm, avoids natural herds
                    addScore(entry.getKey(), anomalyThreshold.get());
                }
            }

            for (Map.Entry<ChunkPos, Integer> entry : minecartCounts.entrySet()) {
                if (entry.getValue() >= 3) { // 3+ storage carts indicates a stash/farm, bypassing standard mineshafts
                    addScore(entry.getKey(), anomalyThreshold.get());
                }
            }
        }
    }

    private void processChunk(net.minecraft.world.chunk.WorldChunk chunk) {
        ChunkPos cPos = chunk.getPos();
        if (alertedChunks.contains(cPos)) return;

        int localScore = 0;
        TriggerMode mode = triggerMode.get();

        // 1. Scan Block Entities as a fallback (if anti-xray happens to leak them)
        if (mode == TriggerMode.All || mode == TriggerMode.StorageBase) {
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                net.minecraft.block.entity.BlockEntity be = chunk.getBlockEntity(pos);
                if (be != null) {
                    net.minecraft.block.entity.BlockEntityType<?> type = be.getType();
                    if (type == net.minecraft.block.entity.BlockEntityType.CHEST || type == net.minecraft.block.entity.BlockEntityType.TRAPPED_CHEST ||
                        type == net.minecraft.block.entity.BlockEntityType.BARREL || type == net.minecraft.block.entity.BlockEntityType.SHULKER_BOX ||
                        type == net.minecraft.block.entity.BlockEntityType.HOPPER) {
                        localScore += 20;
                    }
                }
            }
        }

        // 2. Comprehensive Scan (Extremely optimized: O(1) palette checks)
        for (int i = 0; i < chunk.getSectionArray().length; i++) {
            ChunkSection section = chunk.getSectionArray()[i];
            if (section == null || section.isEmpty()) continue;

            // Ancient City False Positive Filter
            if (section.hasAny(state -> state.isOf(Blocks.SCULK) || state.isOf(Blocks.SCULK_SENSOR) || state.isOf(Blocks.SCULK_VEIN))) {
                continue; // Ignore this section to prevent flagging redstone in Ancient Cities
            }

            int sectionY = chunk.getBottomSectionCoord() + i;
            int worldYStart = sectionY * 16;

            // Redstone Leads
            if (mode == TriggerMode.All || mode == TriggerMode.Redstone) {
                if (section.hasAny(state -> {
                    Block b = state.getBlock();
                    return b == Blocks.REDSTONE_WIRE || b == Blocks.REPEATER || b == Blocks.COMPARATOR ||
                           b == Blocks.OBSERVER || b == Blocks.PISTON || b == Blocks.STICKY_PISTON;
                })) {
                    localScore += anomalyThreshold.get();
                }
            }

            // Storage / Base Trace Leads (Only check below configured Y to avoid Surface Villages)
            if (worldYStart < surfaceTraceMaxY.get() && (mode == TriggerMode.All || mode == TriggerMode.StorageBase)) {
                if (section.hasAny(state -> {
                    Block b = state.getBlock();

                    // 1. Guaranteed Player Trace (Sub-Zero strict check for things that spawn in villages on the surface)
                    if (worldYStart < 0) {
                        if (b instanceof DoorBlock || b instanceof TrapdoorBlock || b instanceof net.minecraft.block.BedBlock ||
                            b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.LANTERN || b == Blocks.CAMPFIRE ||
                            b == Blocks.LADDER) {
                            return true;
                        }
                    }

                    // 2. Unobfuscatable Block Checks (Valid below surfaceTraceMaxY)
                    return b == Blocks.CRAFTING_TABLE || b == Blocks.GLASS ||
                           b == Blocks.END_ROD || b == Blocks.ANVIL ||
                           b == Blocks.BREWING_STAND || b == Blocks.CAULDRON ||
                           b == Blocks.BOOKSHELF || b == Blocks.JUKEBOX ||
                           b == Blocks.NOTE_BLOCK || b == Blocks.FURNACE ||
                           b == Blocks.SMOKER || b == Blocks.BLAST_FURNACE ||
                           b == Blocks.ENCHANTING_TABLE || b == Blocks.NETHER_PORTAL;
                })) {
                    localScore += anomalyThreshold.get();
                }
            }

            if (localScore >= anomalyThreshold.get()) break;
        }

        if (localScore > 0) {
            addScore(cPos, localScore);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof BlockEntityUpdateS2CPacket packet) {
            BlockPos pos = packet.getPos();
            if (pos.getY() < 0) {
                ChunkPos cPos = new ChunkPos(pos);
                BlockEntityType<?> type = packet.getBlockEntityType();
                if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST ||
                    type == BlockEntityType.BARREL || type == BlockEntityType.SHULKER_BOX ||
                    type == BlockEntityType.HOPPER) {
                    addScore(cPos, 20);
                }
            }
        }
        else if (event.packet instanceof PlaySoundS2CPacket packet) {
            if (packet.getY() < 0) {
                String soundName = packet.getSound().value().id().getPath().toLowerCase();

                // Smart Sound Filtering (Player interactions only)
                if (soundName.contains("chest.open") || soundName.contains("chest.close") ||
                    soundName.contains("barrel.open") || soundName.contains("anvil.use") ||
                    soundName.contains("door.open")) {

                    ChunkPos pos = new ChunkPos((int) packet.getX() >> 4, (int) packet.getZ() >> 4);
                    addScore(pos, anomalyThreshold.get()); // Massive score
                    BlockPos exactPos = new BlockPos((int)packet.getX(), (int)packet.getY(), (int)packet.getZ());

                    if (soundHeatmap.size() >= 500) soundHeatmap.remove(0);
                    soundHeatmap.add(exactPos);
                }
            }
        } else if (event.packet instanceof PlaySoundFromEntityS2CPacket packet) {
            Entity entity = mc.world.getEntityById(packet.getEntityId());
            if (entity != null && entity.getY() < 0) {
                String soundName = packet.getSound().value().id().getPath().toLowerCase();

                if (soundName.contains("chest.open") || soundName.contains("chest.close") ||
                    soundName.contains("barrel.open") || soundName.contains("anvil.use") ||
                    soundName.contains("door.open")) {

                    ChunkPos pos = new ChunkPos((int) entity.getX() >> 4, (int) entity.getZ() >> 4);
                    addScore(pos, anomalyThreshold.get());

                    if (soundHeatmap.size() >= 500) soundHeatmap.remove(0);
                    soundHeatmap.add(entity.getBlockPos());
                }
            }
        } else if (event.packet instanceof ParticleS2CPacket packet) {
            if (packet.getY() < 0) {
                ChunkPos pos = new ChunkPos((int) packet.getX() >> 4, (int) packet.getZ() >> 4);
                addScore(pos, anomalyThreshold.get()); // Massive score
            }
        }
    }


    private void sendNotification(ChunkPos cPos, int score) {
        String msg = String.format("⚠ SUB-ZERO TARGET DETECTED at %d, %d (Score: %d)", cPos.getStartX(), cPos.getStartZ(), score);
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
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        SettingColor color = chunkGridColor.get();
        meteordevelopment.meteorclient.utils.render.color.Color outlineColor = new meteordevelopment.meteorclient.utils.render.color.Color(color.r, color.g, color.b, 255);
        meteordevelopment.meteorclient.utils.render.color.Color fillColor = new meteordevelopment.meteorclient.utils.render.color.Color(color.r, color.g, color.b, 60);
        meteordevelopment.meteorclient.utils.render.color.Color vectorColor = new meteordevelopment.meteorclient.utils.render.color.Color(0, 255, 255, 255); // Cyan for vector
        meteordevelopment.meteorclient.utils.render.color.Color heatColor = new meteordevelopment.meteorclient.utils.render.color.Color(255, 100, 0, 150); // Orange for sounds

        // Render Heatmap
        for (BlockPos heatPos : soundHeatmap) {
            event.renderer.box(heatPos.getX() - 0.2, heatPos.getY() - 0.2, heatPos.getZ() - 0.2, heatPos.getX() + 1.2, heatPos.getY() + 1.2, heatPos.getZ() + 1.2, heatColor, heatColor, ShapeMode.Both, 0);
        }

        // Render Prediction Vector
        if (predictedVector != null && !alertHistory.isEmpty()) {
            ChunkPos lastChunk = alertHistory.get(alertHistory.size() - 1);
            int startY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, lastChunk.getCenterX(), lastChunk.getCenterZ());
            int endY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, predictedVector.getCenterX(), predictedVector.getCenterZ());
            event.renderer.line(
                lastChunk.getCenterX(), startY, lastChunk.getCenterZ(),
                predictedVector.getCenterX(), endY, predictedVector.getCenterZ(),
                vectorColor
            );
        }

        for (ChunkPos cPos : alertedChunks) {
            double minX = cPos.getStartX();
            double minZ = cPos.getStartZ();
            double maxX = cPos.getEndX() + 1.0;
            double maxZ = cPos.getEndZ() + 1.0;

            // Calculate the top surface Y for the center of the chunk
            int surfaceY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cPos.getCenterX(), cPos.getCenterZ());

            if (drawBeacon.get()) {
                // Flat red layer 0.5 blocks thick on the surface
                event.renderer.box(minX, surfaceY, minZ, maxX, surfaceY + 0.5, maxZ, fillColor, outlineColor, ShapeMode.Both, 0);
            }

            if (drawTracers.get()) {
                double centerX = cPos.getCenterX();
                double centerZ = cPos.getCenterZ();
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    centerX, surfaceY, centerZ,
                    outlineColor
                );
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null || alertedChunks.isEmpty()) return;

        // Find closest alerted chunk
        ChunkPos closest = null;
        double minDistance = Double.MAX_VALUE;
        for (ChunkPos cPos : alertedChunks) {
            double dist = mc.player.squaredDistanceTo(cPos.getCenterX(), mc.player.getY(), cPos.getCenterZ());
            if (dist < minDistance) {
                minDistance = dist;
                closest = cPos;
            }
        }

        if (closest != null) {
            String text = String.format("⚠ SUB-ZERO TARGET DETECTED: %d, %d", closest.getStartX(), closest.getStartZ());
            TextRenderer.get().begin(1.5, false, true);
            double width = TextRenderer.get().getWidth(text);
            double x = (event.screenWidth - width) / 2.0;
            double y = 20.0;
            TextRenderer.get().render(text, x, y, new meteordevelopment.meteorclient.utils.render.color.Color(255, 50, 50, 255));
            TextRenderer.get().end();
        }
    }
}
