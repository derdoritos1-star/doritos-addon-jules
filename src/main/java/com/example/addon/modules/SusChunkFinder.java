package com.example.addon.modules;

import com.example.addon.DoritosAddon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScoring = settings.createGroup("Scoring");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    public enum LoggingMode { Chat, ActionBar, Toast, None }

    // General Settings
    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("target-blocks")
            .description("Blocks to search for.")
            .defaultValue(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.SHULKER_BOX, Blocks.SPAWNER, Blocks.HOPPER)
            .build()
    );

    private final Setting<Boolean> ignoreAmethyst = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-amethyst")
            .description("Completely ignore amethyst-related blocks/entities.")
            .defaultValue(true)
            .build()
    );

    // Scoring Settings
    private final Setting<Integer> candidateThreshold = sgScoring.add(new IntSetting.Builder()
            .name("candidate-threshold")
            .description("Score required to be considered a candidate.")
            .defaultValue(10)
            .min(1)
            .sliderMax(50)
            .build()
    );

    private final Setting<Integer> confirmedThreshold = sgScoring.add(new IntSetting.Builder()
            .name("confirmed-threshold")
            .description("Score required to be confirmed and rendered.")
            .defaultValue(25)
            .min(1)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> minLargeArea = sgScoring.add(new IntSetting.Builder()
            .name("min-large-area")
            .description("Minimum contiguous block area to trigger LARGE_AREA evidence.")
            .defaultValue(15)
            .min(1)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> minAffectedSections = sgScoring.add(new IntSetting.Builder()
            .name("min-affected-sections")
            .description("Minimum number of affected sections for MULTI_SECTION evidence.")
            .defaultValue(3)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> minClusterSize = sgScoring.add(new IntSetting.Builder()
            .name("min-cluster-size")
            .description("Minimum chunks in a cluster to give NEIGHBOR_CLUSTER evidence.")
            .defaultValue(2)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> scoreDecayTicks = sgScoring.add(new IntSetting.Builder()
            .name("score-decay-ticks")
            .description("Ticks before score starts decaying.")
            .defaultValue(1200) // 1 minute
            .min(20)
            .sliderMax(6000)
            .build()
    );

    // Render Settings
    private final Setting<Boolean> debugMode = sgRender.add(new BoolSetting.Builder()
            .name("debug-mode")
            .description("Render candidates and show scores.")
            .defaultValue(false)
            .build()
    );

    private final Setting<SettingColor> confirmedColor = sgRender.add(new ColorSetting.Builder()
            .name("confirmed-color")
            .description("The color of confirmed chunks.")
            .defaultValue(new SettingColor(255, 0, 0, 100))
            .build()
    );

    private final Setting<SettingColor> debugColor = sgRender.add(new ColorSetting.Builder()
            .name("debug-color")
            .description("The color of candidate chunks in debug mode.")
            .defaultValue(new SettingColor(255, 255, 0, 100))
            .visible(debugMode::get)
            .build()
    );

    private final Setting<Double> renderThickness = sgRender.add(new DoubleSetting.Builder()
            .name("render-thickness")
            .description("Thickness of the surface marker.")
            .defaultValue(0.2)
            .min(0.01)
            .sliderMax(1.0)
            .build()
    );

    // Notifications
    private final Setting<LoggingMode> loggingMode = sgNotifications.add(new EnumSetting.Builder<LoggingMode>()
            .name("logging-mode")
            .description("How to notify about confirmed chunks.")
            .defaultValue(LoggingMode.Chat)
            .build()
    );

    public enum Evidence {
        BLOCK_ENTITY,
        ENTITY,
        SOUND,
        LIGHT,
        BLOCK_PATTERN,
        PACKET_ANOMALY,
        LARGE_AREA,
        MULTI_SECTION,
        NEIGHBOR_CLUSTER
    }

    public static class ChunkState {
        public ChunkPos pos;
        public int score;
        public long firstSeen;
        public long lastSeen;
        public EnumSet<Evidence> evidence;
        public int affectedSections;
        public int affectedArea;

        // Internal tracking
        public Set<BlockPos> suspiciousBlocks;
        public Set<Integer> sectionYSet;

        public ChunkState(ChunkPos pos, long time) {
            this.pos = pos;
            this.score = 0;
            this.firstSeen = time;
            this.lastSeen = time;
            this.evidence = EnumSet.noneOf(Evidence.class);
            this.affectedSections = 0;
            this.affectedArea = 0;
            this.suspiciousBlocks = new HashSet<>();
            this.sectionYSet = new HashSet<>();
        }
    }

    private final Long2ObjectMap<ChunkState> chunkCache = new Long2ObjectOpenHashMap<>();
    private final Set<ChunkPos> confirmedChunks = new HashSet<>();

    public SusChunkFinder() {
        super(DoritosAddon.CATEGORY, "sus-chunk-finder", "Finds suspicious underground chunks.");
    }

    @Override
    public void onActivate() {
        clearCache();
    }

    @Override
    public void onDeactivate() {
        clearCache();
    }

    private void clearCache() {
        chunkCache.clear();
        confirmedChunks.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearCache();
    }

    private void addEvidence(ChunkPos cPos, Evidence type, int scoreContribution, BlockPos pos) {
        if (pos != null && pos.getY() >= 0) return; // Only process underground

        long now = System.currentTimeMillis();
        ChunkState state = chunkCache.computeIfAbsent(cPos.toLong(), k -> new ChunkState(cPos, now));

        state.lastSeen = now;

        if (pos != null) {
            state.suspiciousBlocks.add(pos);
            state.sectionYSet.add(pos.getY() >> 4);
        }

        if (state.evidence.add(type)) {
            // First time getting this evidence type gives full score
            state.score += scoreContribution;
        } else {
            // Diminishing returns for repeated evidence of the same type
            state.score += Math.max(1, scoreContribution / 3);
        }
    }

    private boolean isAmethyst(BlockState state) {
        if (!ignoreAmethyst.get()) return false;
        Block b = state.getBlock();
        return b == Blocks.AMETHYST_BLOCK || b == Blocks.BUDDING_AMETHYST ||
               b == Blocks.AMETHYST_CLUSTER || b == Blocks.LARGE_AMETHYST_BUD ||
               b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.SMALL_AMETHYST_BUD ||
               b == Blocks.CALCITE || b == Blocks.SMOOTH_BASALT;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            if (packet.getPos().getY() < 0) {
                if (isAmethyst(packet.getState())) return;

                if (targetBlocks.get().contains(packet.getState().getBlock())) {
                    addEvidence(new ChunkPos(packet.getPos()), Evidence.BLOCK_PATTERN, 2, packet.getPos());
                }
            }
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates((pos, state) -> {
                if (pos.getY() < 0) {
                    if (isAmethyst(state)) return;

                    if (targetBlocks.get().contains(state.getBlock())) {
                        addEvidence(new ChunkPos(pos), Evidence.BLOCK_PATTERN, 2, pos);
                    }
                }
            });
        } else if (event.packet instanceof BlockEntityUpdateS2CPacket packet) {
            if (packet.getPos().getY() < 0) {
                BlockState state = mc.world.getBlockState(packet.getPos());
                if (!isAmethyst(state)) {
                    addEvidence(new ChunkPos(packet.getPos()), Evidence.BLOCK_ENTITY, 5, packet.getPos());
                }
            }
        } else if (event.packet instanceof ChunkDataS2CPacket packet) {
            packet.getChunkData().getBlockEntities(packet.getChunkX(), packet.getChunkZ()).accept((pos, type, nbt) -> {
                if (pos.getY() < 0) {
                    addEvidence(new ChunkPos(pos), Evidence.BLOCK_ENTITY, 5, pos);
                }
            });
        } else if (event.packet instanceof PlaySoundS2CPacket packet) {
            if (packet.getY() < 0) {
                String soundId = packet.getSound().value().id().getPath();
                if (soundId.contains("piston") || soundId.contains("dispenser") || soundId.contains("chest") ||
                    soundId.contains("barrel") || soundId.contains("dropper") || soundId.contains("shulker_box") ||
                    soundId.contains("hopper") || soundId.contains("minecart")) {

                    BlockPos pos = BlockPos.ofFloored(packet.getX(), packet.getY(), packet.getZ());
                    addEvidence(new ChunkPos(pos), Evidence.SOUND, 4, pos);
                }
            }
        } else if (event.packet instanceof EntitySpawnS2CPacket packet) {
            if (packet.getY() < 0) {
                if (packet.getEntityType() == net.minecraft.entity.EntityType.HOPPER_MINECART ||
                    packet.getEntityType() == net.minecraft.entity.EntityType.CHEST_MINECART ||
                    packet.getEntityType() == net.minecraft.entity.EntityType.ARMOR_STAND ||
                    packet.getEntityType() == net.minecraft.entity.EntityType.ITEM_FRAME ||
                    packet.getEntityType() == net.minecraft.entity.EntityType.GLOW_ITEM_FRAME) {

                    BlockPos pos = BlockPos.ofFloored(packet.getX(), packet.getY(), packet.getZ());
                    addEvidence(new ChunkPos(pos), Evidence.ENTITY, 6, pos);
                }
            }
        } else if (event.packet instanceof LightUpdateS2CPacket packet) {
            java.util.BitSet initedBlock = packet.getData().getInitedBlock();
            if (initedBlock != null) {
                int chunkBottomSectionY = mc.world.getBottomSectionCoord();
                for (int i = 0; i < initedBlock.length(); i++) {
                    if (initedBlock.get(i)) {
                        int sectionY = i + chunkBottomSectionY;
                        if (sectionY < 0) { // Underground
                            BlockPos centerPos = new BlockPos(packet.getChunkX() * 16 + 8, sectionY * 16 + 8, packet.getChunkZ() * 16 + 8);
                            addEvidence(new ChunkPos(centerPos), Evidence.LIGHT, 3, centerPos);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        Chunk chunk = event.chunk();
        ChunkPos cPos = chunk.getPos();

        int localArea = 0;
        int localSections = 0;

        for (int i = 0; i < chunk.getSectionArray().length; i++) {
            ChunkSection section = chunk.getSectionArray()[i];
            if (section == null || section.isEmpty()) continue;

            int sectionY = chunk.getBottomSectionCoord() + i;
            if (sectionY >= 0) continue; // Only care about underground

            boolean hasSusBlock = false;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isAmethyst(state)) continue;

                        if (targetBlocks.get().contains(state.getBlock())) {
                            localArea++;
                            hasSusBlock = true;
                        }
                    }
                }
            }
            if (hasSusBlock) {
                localSections++;
            }
        }

        if (localArea > 0 || localSections > 0) {
            ChunkState state = chunkCache.computeIfAbsent(cPos.toLong(), k -> new ChunkState(cPos, System.currentTimeMillis()));
            state.affectedArea = Math.max(state.affectedArea, localArea);
            state.affectedSections = Math.max(state.affectedSections, localSections);

            if (state.affectedArea >= minLargeArea.get()) {
                addEvidence(cPos, Evidence.LARGE_AREA, 10, null);
            }
            if (state.affectedSections >= minAffectedSections.get()) {
                addEvidence(cPos, Evidence.MULTI_SECTION, 8, null);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        long decayMillis = scoreDecayTicks.get() * 50L;

        chunkCache.long2ObjectEntrySet().removeIf(entry -> {
            ChunkState state = entry.getValue();

            // Score decay
            if (now - state.lastSeen > decayMillis) {
                state.score = Math.max(0, state.score - 1);
                state.lastSeen = now - decayMillis + 1000; // decay every second roughly
            }

            return state.score <= 0 && state.evidence.isEmpty();
        });

        // Clustering
        for (ChunkState state : chunkCache.values()) {
            if (state.score >= candidateThreshold.get()) {
                int neighborCount = 0;
                ChunkPos pos = state.pos;

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && z == 0) continue;
                        ChunkState neighbor = chunkCache.get(ChunkPos.toLong(pos.x + x, pos.z + z));
                        if (neighbor != null && neighbor.score >= candidateThreshold.get()) {
                            neighborCount++;
                        }
                    }
                }

                if (neighborCount >= minClusterSize.get()) {
                    addEvidence(pos, Evidence.NEIGHBOR_CLUSTER, 5, null);
                }
            }
        }

        // Confirmation
        for (ChunkState state : chunkCache.values()) {
            if (state.score >= confirmedThreshold.get()) {
                if (confirmedChunks.add(state.pos)) {
                    sendNotification(state);
                }
            } else {
                confirmedChunks.remove(state.pos);
            }
        }
    }

    private void sendNotification(ChunkState state) {
        String msg = String.format("Sus chunk confirmed at %d, %d (Score: %d)", state.pos.getStartX(), state.pos.getStartZ(), state.score);
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

        if (debugMode.get()) {
            ChatUtils.info("Debug Evidence for %d, %d: %s", state.pos.getStartX(), state.pos.getStartZ(), state.evidence.toString());
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (ChunkState state : chunkCache.values()) {
            boolean isConfirmed = confirmedChunks.contains(state.pos);
            if (!isConfirmed && !debugMode.get()) continue;

            // Get surface Y for this chunk
            WorldChunk chunk = mc.world.getChunk(state.pos.x, state.pos.z);
            if (chunk == null) continue;

            int surfaceY = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, 8, 8);
            if (surfaceY < mc.world.getBottomY()) surfaceY = mc.world.getBottomY(); // fallback

            int startX = state.pos.getStartX();
            int startZ = state.pos.getStartZ();

            // Only render if close enough
            if (mc.player.squaredDistanceTo(startX + 8, surfaceY, startZ + 8) > 256 * 256) continue;

            SettingColor color = isConfirmed ? confirmedColor.get() : debugColor.get();
            double t = renderThickness.get();

            event.renderer.box(startX, surfaceY, startZ, startX + 16, surfaceY + t, startZ + 16, color, color, ShapeMode.Sides, 0);
            event.renderer.box(startX, surfaceY, startZ, startX + 16, surfaceY + t, startZ + 16, color, color, ShapeMode.Lines, 0);
        }
    }
}
