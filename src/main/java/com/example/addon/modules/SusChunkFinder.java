package com.example.addon.modules;

import com.example.addon.DoritosAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;

import net.minecraft.client.toast.SystemToast;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;

import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    public enum LoggingMode { Chat, ActionBar, Toast, None }

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder().name("scan-radius").description("Radius in chunks to scan around player.").defaultValue(4).sliderRange(1, 32).build());
    private final Setting<Integer> anomalyThreshold = sgGeneral.add(new IntSetting.Builder().name("anomaly-threshold").description("Score threshold for alerting.").defaultValue(50).sliderRange(1, 1000).build());

    private final Setting<SettingColor> chunkGridColor = sgRender.add(new ColorSetting.Builder().name("chunk-grid-color").description("Color of the highlighted chunk borders.").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Setting<LoggingMode> loggingMode = sgNotifications.add(new EnumSetting.Builder<LoggingMode>().name("logging-mode").description("How to notify when a sus chunk is found.").defaultValue(LoggingMode.Chat).build());

    private final Map<ChunkPos, Integer> chunkScores = new ConcurrentHashMap<>();
    private final Set<ChunkPos> alertedChunks = ConcurrentHashMap.newKeySet();

    public SusChunkFinder() {
        super(DoritosAddon.CATEGORY, "SusChunkFinder", "Sub-Zero Target Detector. Exploits anti-xray flaws below Y=0.");
    }

    @Override
    public void onActivate() {
        chunkScores.clear();
        alertedChunks.clear();
    }

    @Override
    public void onDeactivate() {
        chunkScores.clear();
        alertedChunks.clear();
    }

    private void addScore(ChunkPos pos, int score) {
        if (alertedChunks.contains(pos)) return;

        int newScore = chunkScores.merge(pos, score, Integer::sum);
        if (newScore >= anomalyThreshold.get() && alertedChunks.add(pos)) {
            sendNotification(pos, newScore);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (mc.player.age % 20 != 0) return; // OPTIMIZATION: Only run 1 time per second

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

        // Entity Sniffing (Lead Detection)
        if (mc.player.age % 20 == 0) {
            Map<ChunkPos, Integer> passiveCounts = new java.util.HashMap<>();
            for (Entity entity : mc.world.getEntities()) {
                ChunkPos cPos = entity.getChunkPos();
                if (alertedChunks.contains(cPos)) continue;

                if (entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity) {
                    addScore(cPos, anomalyThreshold.get()); // Instant alert for frames/armor stands
                } else if (entity instanceof PassiveEntity || entity instanceof VillagerEntity) {
                    passiveCounts.put(cPos, passiveCounts.getOrDefault(cPos, 0) + 1);
                }
            }

            for (Map.Entry<ChunkPos, Integer> entry : passiveCounts.entrySet()) {
                if (entry.getValue() > 12) { // 12+ animals/villagers is a farm
                    addScore(entry.getKey(), anomalyThreshold.get());
                }
            }
        }
    }


    private void processChunk(net.minecraft.world.chunk.WorldChunk chunk) {
        ChunkPos cPos = chunk.getPos();
        if (alertedChunks.contains(cPos)) return;

        int localScore = 0;

        // 1. Scan Block Entities as a fallback (if anti-xray happens to leak them)
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

        // 2. Scan for unobfuscated trace blocks (Redstone, Farmland, Glass, Beds)
        for (int i = 0; i < chunk.getSectionArray().length; i++) {
            ChunkSection section = chunk.getSectionArray()[i];
            if (section == null || section.isEmpty()) continue;

            if (section.hasAny(state -> {
                Block b = state.getBlock();
                return b == Blocks.FARMLAND || b == Blocks.REDSTONE_WIRE || b == Blocks.REPEATER ||
                       b == Blocks.COMPARATOR || b == Blocks.OBSERVER || b == Blocks.PISTON ||
                       b == Blocks.STICKY_PISTON || b == Blocks.CRAFTING_TABLE || b == Blocks.GLASS ||
                       b == Blocks.END_ROD || b instanceof net.minecraft.block.BedBlock;
            })) {
                localScore += anomalyThreshold.get(); // Instant alert if trace blocks found
                break;
            }
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
                ChunkPos pos = new ChunkPos((int) packet.getX() >> 4, (int) packet.getZ() >> 4);
                addScore(pos, 200); // Massive score
            }
        } else if (event.packet instanceof PlaySoundFromEntityS2CPacket packet) {
            Entity entity = mc.world.getEntityById(packet.getEntityId());
            if (entity != null && entity.getY() < 0) {
                ChunkPos pos = new ChunkPos((int) entity.getX() >> 4, (int) entity.getZ() >> 4);
                addScore(pos, 200);
            }
        } else if (event.packet instanceof ParticleS2CPacket packet) {
            if (packet.getY() < 0) {
                ChunkPos pos = new ChunkPos((int) packet.getX() >> 4, (int) packet.getZ() >> 4);
                addScore(pos, 200); // Massive score
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

        for (ChunkPos cPos : alertedChunks) {
            double minX = cPos.getStartX();
            double minZ = cPos.getStartZ();
            double maxX = cPos.getEndX() + 1.0;
            double maxZ = cPos.getEndZ() + 1.0;

            // Vertical beacon beam
            event.renderer.box(minX, -64.0, minZ, maxX, 320.0, maxZ, chunkGridColor.get(), chunkGridColor.get(), ShapeMode.Lines, 0);

            // Tracer line from player to chunk center
            double centerX = cPos.getCenterX();
            double centerZ = cPos.getCenterZ();
            event.renderer.line(
                RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                centerX, -64.0, centerZ,
                chunkGridColor.get()
            );
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
// trigger CI
