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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
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

        // Entity Sniffing Below Y=0
        for (Entity entity : mc.world.getEntities()) {
            if (entity.getY() < 0 && (entity instanceof PassiveEntity || entity instanceof ItemFrameEntity)) {
                ChunkPos cPos = entity.getChunkPos();
                addScore(cPos, 10); // Adding smaller score constantly is safer than massive score for tick loop
            }
        }
    }

    private void processChunk(net.minecraft.world.chunk.WorldChunk chunk) {
        ChunkPos cPos = chunk.getPos();
        if (alertedChunks.contains(cPos)) return;

        int localScore = 0;
        int airBlocks = 0;

        for (int i = 0; i < chunk.getSectionArray().length; i++) {
            ChunkSection section = chunk.getSectionArray()[i];
            if (section == null || section.isEmpty()) continue;

            int sectionY = chunk.getBottomSectionCoord() + i;
            int worldYStart = sectionY * 16;

            // Only scan Y = -64 to Y = 0
            if (worldYStart >= 0 || worldYStart < -64) continue;

            for (int bx = 0; bx < 16; bx++) {
                for (int by = 0; by < 16; by++) {
                    for (int bz = 0; bz < 16; bz++) {
                        int worldY = worldYStart + by;
                        if (worldY >= 0) continue; // Strict check just in case

                        BlockState state = section.getBlockState(bx, by, bz);
                        Block block = state.getBlock();

                        // 1. Air-Gap Detection
                        if (block == Blocks.AIR) {
                            airBlocks++;
                        }

                        // 2. Block-Light Leak Detection
                        if (block == Blocks.DEEPSLATE || block == Blocks.BEDROCK) {
                            BlockPos pos = new BlockPos(cPos.getStartX() + bx, worldY, cPos.getStartZ() + bz);
                            int light = mc.world.getLightLevel(LightType.BLOCK, pos);
                            if (light > 0) {
                                localScore += 50; // Massive score for light leaking through disguised solid blocks
                            }
                        }
                    }
                }
            }
        }

        // Air-Gap Pattern: If there are significant standard AIR blocks underground, flag it.
        // CAVE_AIR is normal, but standard AIR usually implies an artificial hollow space.
        if (airBlocks > 10) {
            localScore += airBlocks * 2;
        }

        if (localScore > 0) {
            addScore(cPos, localScore);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof PlaySoundS2CPacket packet) {
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
            double minY = -64.0;
            double maxY = 0.0; // Render box exactly in the sub-zero zone

            event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, chunkGridColor.get(), chunkGridColor.get(), ShapeMode.Lines, 0);
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
