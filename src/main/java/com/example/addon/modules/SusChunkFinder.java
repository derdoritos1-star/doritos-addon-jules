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

    private final Setting<Integer> minIndependentEvidence = sgScoring.add(new IntSetting.Builder()
            .name("min-independent-evidence")
            .description("Minimum independent evidence categories required to confirm.")
            .defaultValue(2)
            .min(1)
            .sliderMax(5)
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
        NEIGHBOR_CLUSTER,
        NORMALITY_TUNNEL,
        NORMALITY_SHAFT
    }

    public enum EvidenceCategory {
        STRUCTURAL, BLOCK_ENTITY, LIGHT, ENTITY, SOUND, NETWORK
    }

    public static class ComponentStats {
        public int size;
        public int bbWidthX;
        public int bbWidthZ;
        public int bbHeight;
        public double density;
        public boolean isTunnel;
        public boolean isShaft;

        public ComponentStats(int size, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
            this.size = size;
            this.bbWidthX = maxX - minX + 1;
            this.bbWidthZ = maxZ - minZ + 1;
            this.bbHeight = maxY - minY + 1;
            this.density = (double) size / (bbWidthX * bbWidthZ * Math.max(1, bbHeight));
            this.isShaft = bbWidthX <= 2 && bbWidthZ <= 2 && size > 5;
            this.isTunnel = Math.min(bbWidthX, bbWidthZ) <= 2 && Math.max(bbWidthX, bbWidthZ) > 6;
        }
    }

    public static class ChunkState {
        public ChunkPos pos;
        public int score;
        public long firstSeen;
        public long lastSeen;
        public long lastStrongEvidence;
        public EnumSet<Evidence> evidence;
        public int affectedSections;
        public int affectedArea;

        // Internal tracking
        public java.util.Map<Evidence, Integer> evidenceCounts;
        public EnumSet<EvidenceCategory> independentCategories;
        public Set<BlockPos> suspiciousBlocks;
        public Set<Integer> sectionYSet;
        public ComponentStats largestComponent;
        public boolean isDirty;
        public int cachedSurfaceY = -256;

        public ChunkState(ChunkPos pos, long time) {
            this.pos = pos;
            this.score = 0;
            this.firstSeen = time;
            this.lastSeen = time;
            this.lastStrongEvidence = time;
            this.evidence = EnumSet.noneOf(Evidence.class);
            this.affectedSections = 0;
            this.affectedArea = 0;
            this.evidenceCounts = new java.util.HashMap<>();
            this.independentCategories = EnumSet.noneOf(EvidenceCategory.class);
            this.suspiciousBlocks = new HashSet<>();
            this.sectionYSet = new HashSet<>();
            this.largestComponent = null;
            this.isDirty = false;
        }
    }

    // Tracks evidence already recorded to prevent duplicate packets from inflating score
    private final java.util.Map<BlockPos, EnumSet<EvidenceCategory>> processedPositions = new java.util.HashMap<>();

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
        processedPositions.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearCache();
    }

    private EvidenceCategory getCategory(Evidence ev) {
        switch(ev) {
            case BLOCK_PATTERN: case LARGE_AREA: case MULTI_SECTION: case NEIGHBOR_CLUSTER: return EvidenceCategory.STRUCTURAL;
            case BLOCK_ENTITY: return EvidenceCategory.BLOCK_ENTITY;
            case LIGHT: return EvidenceCategory.LIGHT;
            case ENTITY: return EvidenceCategory.ENTITY;
            case SOUND: return EvidenceCategory.SOUND;
            case PACKET_ANOMALY: return EvidenceCategory.NETWORK;
            default: return EvidenceCategory.STRUCTURAL;
        }
    }

    private void addEvidence(ChunkPos cPos, Evidence type, int maxContribution, BlockPos pos) {
        if (pos != null && pos.getY() >= 0) return; // Only process underground

        EvidenceCategory category = getCategory(type);
        if (pos != null) {
            EnumSet<EvidenceCategory> recorded = processedPositions.computeIfAbsent(pos, k -> EnumSet.noneOf(EvidenceCategory.class));
            if (!recorded.add(category)) {
                return; // Prevent duplicate evidence tracking for same block
            }
        }

        long now = System.currentTimeMillis();
        ChunkState state = chunkCache.computeIfAbsent(cPos.toLong(), k -> new ChunkState(cPos, now));

        state.lastSeen = now;

        // Block pattern implies structure updates, schedule BFS
        if (type == Evidence.BLOCK_PATTERN) {
            state.isDirty = true;
        }

        if (maxContribution >= 5 || category == EvidenceCategory.STRUCTURAL) {
            state.lastStrongEvidence = now;
        }

        if (pos != null && type == Evidence.BLOCK_PATTERN) {
            state.suspiciousBlocks.add(pos);
            state.sectionYSet.add(pos.getY() >> 4);
        }

        int count = state.evidenceCounts.getOrDefault(type, 0);
        if (count > 5) return; // Cap repeated evidence heavily
        state.evidenceCounts.put(type, count + 1);

        state.independentCategories.add(category);

        if (state.evidence.add(type)) {
            state.score += maxContribution;
        } else {
            int diminishing = Math.max(0, maxContribution - (count * (maxContribution / 3)));
            state.score += diminishing;
        }
    }

    private int getBlockEntityScore(Block b) {
        if (b == Blocks.SHULKER_BOX || b == Blocks.SPAWNER || b == Blocks.HOPPER || b == Blocks.ENDER_CHEST) return 6;
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) return 4;
        return 1;
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
                    int sc = getBlockEntityScore(state.getBlock());
                    if (sc > 1) {
                        addEvidence(new ChunkPos(packet.getPos()), Evidence.BLOCK_ENTITY, sc, packet.getPos());
                    }
                }
            }
        } else if (event.packet instanceof ChunkDataS2CPacket packet) {
            packet.getChunkData().getBlockEntities(packet.getChunkX(), packet.getChunkZ()).accept((pos, type, nbt) -> {
                if (pos.getY() < 0) {
                    // We don't have block state here securely, but generally assume medium impact
                    addEvidence(new ChunkPos(pos), Evidence.BLOCK_ENTITY, 3, pos);
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
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        Chunk chunk = event.chunk();
        ChunkPos cPos = chunk.getPos();

        int localSections = 0;
        boolean dirty = false;

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
                            hasSusBlock = true;
                            dirty = true;
                            BlockPos blockPos = new BlockPos(cPos.getStartX() + x, sectionY * 16 + y, cPos.getStartZ() + z);
                            addEvidence(cPos, Evidence.BLOCK_PATTERN, 2, blockPos);
                        }
                    }
                }
            }
            if (hasSusBlock) {
                localSections++;
            }
        }

        if (dirty) {
            ChunkState state = chunkCache.computeIfAbsent(cPos.toLong(), k -> new ChunkState(cPos, System.currentTimeMillis()));
            state.affectedSections = Math.max(state.affectedSections, localSections);

            if (state.affectedSections >= minAffectedSections.get()) {
                addEvidence(cPos, Evidence.MULTI_SECTION, 8, null);
            }
        }
    }

    private void analyzeComponents(ChunkState state) {
        if (state.suspiciousBlocks.isEmpty()) return;

        Set<BlockPos> unvisited = new HashSet<>(state.suspiciousBlocks);
        ComponentStats bestComponent = null;

        while (!unvisited.isEmpty()) {
            BlockPos start = unvisited.iterator().next();
            Set<BlockPos> component = new HashSet<>();
            java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();

            queue.add(start);
            component.add(start);
            unvisited.remove(start);

            int minX = start.getX(), maxX = start.getX();
            int minZ = start.getZ(), maxZ = start.getZ();
            int minY = start.getY(), maxY = start.getY();

            while (!queue.isEmpty()) {
                BlockPos curr = queue.poll();

                // Search 3x3x3 neighborhood
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            BlockPos neighbor = curr.add(x, y, z);

                            if (unvisited.contains(neighbor)) {
                                unvisited.remove(neighbor);
                                component.add(neighbor);
                                queue.add(neighbor);

                                minX = Math.min(minX, neighbor.getX()); maxX = Math.max(maxX, neighbor.getX());
                                minZ = Math.min(minZ, neighbor.getZ()); maxZ = Math.max(maxZ, neighbor.getZ());
                                minY = Math.min(minY, neighbor.getY()); maxY = Math.max(maxY, neighbor.getY());
                            }
                        }
                    }
                }
            }

            if (bestComponent == null || component.size() > bestComponent.size) {
                bestComponent = new ComponentStats(component.size(), minX, maxX, minZ, maxZ, minY, maxY);
            }
        }

        state.largestComponent = bestComponent;

        if (bestComponent != null) {
            state.affectedArea = bestComponent.size;

            if (bestComponent.isShaft) {
                state.evidence.add(Evidence.NORMALITY_SHAFT);
            } else if (bestComponent.isTunnel) {
                state.evidence.add(Evidence.NORMALITY_TUNNEL);
            } else if (bestComponent.size >= minLargeArea.get() && bestComponent.density > 0.1) {
                addEvidence(state.pos, Evidence.LARGE_AREA, 10, null);
                // clear normality flags if the component evolved into a real base
                state.evidence.remove(Evidence.NORMALITY_SHAFT);
                state.evidence.remove(Evidence.NORMALITY_TUNNEL);
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
            if (now - state.lastSeen > decayMillis) {
                return true;
            }
            return false;
        });

        // Compute dirty chunk components incrementally
        for (ChunkState state : chunkCache.values()) {
            if (state.isDirty) {
                analyzeComponents(state);
                state.isDirty = false;
            }
        }

        // Confirmation
        for (ChunkState state : chunkCache.values()) {
            int requiredScore = confirmedThreshold.get();
            if (state.evidence.contains(Evidence.NORMALITY_TUNNEL) || state.evidence.contains(Evidence.NORMALITY_SHAFT)) {
                requiredScore += 20; // Harder to confirm normal-looking excavations
            }

            int neighborCount = 0;
            if (state.score >= candidateThreshold.get()) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && z == 0) continue;
                        ChunkState neighbor = chunkCache.get(ChunkPos.toLong(state.pos.x + x, state.pos.z + z));
                        if (neighbor != null && neighbor.score >= candidateThreshold.get()) {
                            neighborCount++;
                        }
                    }
                }
            }
            // Dynamic clustering limit (cap it so we don't infinitely scale)
            int clusterScore = Math.min(10, neighborCount * 2);
            int dynamicScore = state.score + clusterScore;

            // Hard gates for confirmation
            boolean hasScore = dynamicScore >= requiredScore;
            boolean hasIndependent = state.independentCategories.size() >= minIndependentEvidence.get();
            boolean hasArea = state.affectedArea >= minLargeArea.get() || (state.largestComponent != null && state.largestComponent.size >= minLargeArea.get());

            if (hasScore && hasIndependent && hasArea) {
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
            String densityStr = state.largestComponent != null ? String.format("%.2f", state.largestComponent.density) : "N/A";
            String bbStr = state.largestComponent != null ? String.format("%dx%dx%d", state.largestComponent.bbWidthX, state.largestComponent.bbHeight, state.largestComponent.bbWidthZ) : "N/A";

            ChatUtils.info("Debug %d, %d | Score: %d | Cats: %d | Area: %d | BB: %s | Dens: %s | Flags: %s",
                state.pos.getStartX(), state.pos.getStartZ(),
                state.score, state.independentCategories.size(),
                state.affectedArea, bbStr, densityStr, state.evidence.toString());
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (ChunkState state : chunkCache.values()) {
            boolean isConfirmed = confirmedChunks.contains(state.pos);
            if (!isConfirmed && !debugMode.get()) continue;

            if (state.cachedSurfaceY == -256) {
                WorldChunk chunk = mc.world.getChunk(state.pos.x, state.pos.z);
                if (chunk == null) continue;

                int sy1 = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, 0, 0);
                int sy2 = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, 8, 8);
                int sy3 = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, 15, 15);
                state.cachedSurfaceY = Math.max(sy1, Math.max(sy2, sy3));

                if (state.cachedSurfaceY < mc.world.getBottomY()) {
                    state.cachedSurfaceY = mc.world.getBottomY();
                }
            }

            int surfaceY = state.cachedSurfaceY;
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
