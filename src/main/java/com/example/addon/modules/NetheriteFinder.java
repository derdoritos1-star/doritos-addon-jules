package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.World;
import com.example.addon.DoritosAddon;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NetheriteFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    public enum AlertMode { Chat, ActionBar, Sound, None }

    private final Setting<Boolean> onlyNether = sgGeneral.add(new BoolSetting.Builder().name("only-nether").defaultValue(true).build());
    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder().name("min-y").description("Min Y to search").defaultValue(8).sliderRange(0, 128).build());
    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder().name("max-y").description("Max Y to search").defaultValue(22).sliderRange(0, 128).build());
    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder().name("render-distance").defaultValue(4).sliderRange(1, 20).build());
    private final Setting<Integer> sectionsPerTick = sgGeneral.add(new IntSetting.Builder().name("sections-per-tick").defaultValue(2).sliderRange(1, 10).build());
    private final Setting<Boolean> antiObfuscation = sgGeneral.add(new BoolSetting.Builder().name("anti-obfuscation").description("Only scan blocks exposed to air/lava.").defaultValue(false).build());

    private final Setting<AlertMode> alertMode = sgNotifications.add(new EnumSetting.Builder<AlertMode>().name("alert-mode").defaultValue(AlertMode.Chat).build());
    private final Setting<Double> soundPitch = sgNotifications.add(new DoubleSetting.Builder().name("sound-pitch").description("Pitch of the sound alert.").defaultValue(1.0).sliderRange(0.5, 2.0).build());
    private final Setting<Integer> alertCooldown = sgNotifications.add(new IntSetting.Builder().name("alert-cooldown-ticks").defaultValue(40).sliderRange(0, 200).build());

    private final Setting<Boolean> coordsOnly = sgRender.add(new BoolSetting.Builder().name("coords-only-no-esp").defaultValue(false).build());
    private final Setting<Integer> maxBoxes = sgRender.add(new IntSetting.Builder().name("max-boxes").defaultValue(50).sliderRange(1, 100).build());
    private final Setting<Boolean> espSections = sgRender.add(new BoolSetting.Builder().name("esp-sections").defaultValue(true).build());
    private final Setting<Boolean> itemEsp = sgRender.add(new BoolSetting.Builder().name("item-esp").defaultValue(true).build());
    private final Setting<Boolean> blockEsp = sgRender.add(new BoolSetting.Builder().name("block-esp").description("Highlight the block").defaultValue(true).build());
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder().name("tracers").description("Lines from player to block").defaultValue(true).build());
    private final Setting<Double> tracerWidth = sgRender.add(new DoubleSetting.Builder().name("tracer-width").description("Width of the tracer line").defaultValue(1.0).sliderRange(0.1, 5.0).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("esp-fill-color").defaultValue(new SettingColor(255, 105, 180, 50)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("esp-outline-color").defaultValue(new SettingColor(255, 105, 180, 255)).build());
    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder().name("tracer-color").defaultValue(new SettingColor(255, 105, 180, 255)).build());

    private final Set<BlockPos> foundBlocks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkSectionPos> suspectedSet = ConcurrentHashMap.newKeySet();
    private final Set<ChunkSectionPos> enteredSections = ConcurrentHashMap.newKeySet();

    // Новая коллекция, которая запоминает секции, в которых мы 100% нашли точечные блоки
    private final Set<ChunkSectionPos> confirmedSections = ConcurrentHashMap.newKeySet();

    private final Queue<ChunkSectionPos> suspectedQueue = new ConcurrentLinkedQueue<>();
    private final Map<ChunkSectionPos, Integer> retryCounts = new ConcurrentHashMap<>();
    private final Map<ChunkSectionPos, BlockPos> blastTargets = new ConcurrentHashMap<>();


    private volatile BlockPos pendingAlertPos = null;
    private int ticksSinceAlert = 0;
    private volatile java.util.List<BlockPos> blocksToRender = new java.util.ArrayList<>();

    public NetheriteFinder() {
        super(DoritosAddon.CATEGORY, "NetheriteFinder", "Zync-style Palette truster. Never disappears while moving.");
    }

    @Override
    public void onActivate() {
        clearCaches();



        if (mc.world == null || mc.player == null) return;
        if (onlyNether.get() && mc.world.getRegistryKey() != World.NETHER) return;

        int r = renderDistance.get();
        ChunkPos pChunk = mc.player.getChunkPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int cx = pChunk.x + x;
                int cz = pChunk.z + z;
                if (mc.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk != null) scanChunkInit(chunk);
                }
            }
        }
    }

    @Override
    public void onDeactivate() {

        clearCaches();
    }

    private void clearCaches() {
        foundBlocks.clear();
        suspectedSet.clear();
        enteredSections.clear();
        confirmedSections.clear();
        suspectedQueue.clear();
        retryCounts.clear();
        blastTargets.clear();
        pendingAlertPos = null;
        ticksSinceAlert = alertCooldown.get();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;
        if (onlyNether.get() && mc.world.getRegistryKey() != World.NETHER) return;

        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            checkAndCache(packet.getPos(), packet.getState());
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::checkAndCache);
        }
    }

    private void checkAndCache(BlockPos pos, BlockState state) {
        pos = pos.toImmutable();
        if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
            if (pos.getY() >= minY.get() && pos.getY() <= maxY.get()) {
                if (foundBlocks.add(pos)) {
                    pendingAlertPos = pos;
                    confirmedSections.add(ChunkSectionPos.from(pos));
                }
            }
        } else {
            // ФИКС ОТ ИСЧЕЗНОВЕНИЯ: Если пришел пакет с незераком, удаляем блок ТОЛЬКО если
            // игрок находится достаточно близко (меньше 8 блоков), чтобы сломать его.
            // Иначе - это серверный Anti-Xray скрывает руду при движении, и мы это игнорируем!
            if (mc.player != null && mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                foundBlocks.remove(pos);
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (onlyNether.get() && mc.world != null && mc.world.getRegistryKey() != World.NETHER) return;
        scanChunkInit(event.chunk());
    }

    private void scanChunkInit(Chunk chunk) {
        if (suspectedQueue.size() > 1000) return;
        for (int i = 0; i < chunk.getSectionArray().length; i++) {
                ChunkSection section = chunk.getSectionArray()[i];
                if (section != null && !section.isEmpty()) {
                    if (section.hasAny(state -> state.isOf(Blocks.ANCIENT_DEBRIS))) {
                        int sectionY = chunk.getBottomSectionCoord() + i;
                        if (sectionY * 16 <= maxY.get() && (sectionY * 16 + 15) >= minY.get()) {
                            ChunkSectionPos sec = ChunkSectionPos.from(chunk.getPos().x, sectionY, chunk.getPos().z);
                            if (suspectedSet.add(sec)) {
                                suspectedQueue.offer(sec);
                            }
                        }
                    }
                }
            }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (mc.player.age % 5 != 0) return; // OPTIMIZATION: Process queues only every 5 ticks

        ticksSinceAlert++;

        ChunkSectionPos playerSec = ChunkSectionPos.from(mc.player.getBlockPos());

        if (suspectedSet.contains(playerSec)) {
            enteredSections.add(playerSec);

            BlockPos currentTarget = blastTargets.get(playerSec);
            if (currentTarget == null) {
                blastTargets.put(playerSec, generateTarget(playerSec));
            } else {
                if (!mc.world.getBlockState(currentTarget).isOf(Blocks.ANCIENT_DEBRIS)) {
                    blastTargets.put(playerSec, generateTarget(playerSec));
                }
            }
        }


        // Авто-удаление: удаляем ТОЛЬКО если точечные блоки были точно найдены (confirmed),
        // и теперь их там нет (выкопаны вблизи). Во время полета удаляться ничего не будет!
        suspectedSet.removeIf(sec -> {
            if (suspectedQueue.contains(sec)) return false;

            boolean hasBlocks = foundBlocks.stream().anyMatch(p -> ChunkSectionPos.from(p).equals(sec));

            if (confirmedSections.contains(sec) && !hasBlocks) {
                enteredSections.remove(sec);
                confirmedSections.remove(sec);
                blastTargets.remove(sec);
                return true;
            }
            return false;
        });

        processAlertQueue();
        processPaletteQueue();

        if (mc.player.age % 40 == 0) {
            cleanOutOfRange();
        }
    }



    private BlockPos generateTarget(ChunkSectionPos sec) {
        int cx = sec.getMinX();
        int cy = sec.getMinY();
        int cz = sec.getMinZ();

        java.util.List<BlockPos> suspectedDebris = new java.util.ArrayList<>();

        // Scan the chunk section precisely for blocks the server reports as Ancient Debris
        for (int bx = 0; bx < 16; bx++) {
            for (int by = 0; by < 16; by++) {
                for (int bz = 0; bz < 16; bz++) {
                    BlockPos pos = new BlockPos(cx + bx, cy + by, cz + bz);
                    if (mc.world.getBlockState(pos).isOf(Blocks.ANCIENT_DEBRIS)) {
                        suspectedDebris.add(pos);
                    }
                }
            }
        }

        if (!suspectedDebris.isEmpty()) {
            // Pick a random suspected debris block to act as the crystal blast epicenter
            return suspectedDebris.get((int) (Math.random() * suspectedDebris.size()));
        }

        // Fallback to center if section is completely empty of fakes
        return new BlockPos(cx + 8, cy + 8, cz + 8);
    }

    private void processPaletteQueue() {
        int processed = 0;

        while (processed < sectionsPerTick.get() && !suspectedQueue.isEmpty()) {
            ChunkSectionPos sectionPos = suspectedQueue.poll();
            if (sectionPos == null || !suspectedSet.contains(sectionPos)) continue;

            int cx = sectionPos.getSectionX();
            int cz = sectionPos.getSectionZ();

            if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) {
                retryCounts.remove(sectionPos);
                continue;
            }

            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
            if (chunk == null) continue;

            int secIdx = sectionPos.getSectionY() - chunk.getBottomSectionCoord();
            if (secIdx < 0 || secIdx >= chunk.getSectionArray().length) continue;

            ChunkSection section = chunk.getSectionArray()[secIdx];

            // Если секция пуста - просто перестаем её сканировать, но НЕ удаляем большой куб!
            if (section == null || section.isEmpty()) {
                retryCounts.remove(sectionPos);
                continue;
            }

            boolean foundVisible = false;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (section.getBlockState(x, y, z).isOf(Blocks.ANCIENT_DEBRIS)) {
                            BlockPos pos = sectionPos.getMinPos().add(x, y, z);
                            if (pos.getY() >= minY.get() && pos.getY() <= maxY.get()) {
                                boolean exposed = true;
                                if (antiObfuscation.get()) {
                                    exposed = false;
                                    for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                                        BlockState adj = mc.world.getBlockState(pos.offset(dir));
                                        if (adj.isAir() || adj.isOf(Blocks.LAVA) || adj.isOf(Blocks.WATER)) {
                                            exposed = true;
                                            break;
                                        }
                                    }
                                }
                                if (exposed) {
                                    if (foundBlocks.add(pos)) {
                                        pendingAlertPos = pos;
                                    }
                                    foundVisible = true;
                                }
                            }
                        }
                    }
                }
            }

            if (!foundVisible) {
                int attempts = retryCounts.getOrDefault(sectionPos, 0);
                if (attempts < 5) {
                    retryCounts.put(sectionPos, attempts + 1);
                    suspectedQueue.offer(sectionPos);
                } else {
                    // ГЛАВНЫЙ ФИКС: Раньше мы удаляли куб 16x16x16 при неудаче.
                    // Теперь мы доверяем палитре и просто оставляем куб висеть на экране!
                    retryCounts.remove(sectionPos);
                }
            } else {
                confirmedSections.add(sectionPos);
                retryCounts.remove(sectionPos);
            }
            processed++;
        }
    }

    private void processAlertQueue() {
        if (pendingAlertPos != null && alertMode.get() != AlertMode.None && ticksSinceAlert >= alertCooldown.get()) {
            String msg = String.format("Debris found at %d %d %d", pendingAlertPos.getX(), pendingAlertPos.getY(), pendingAlertPos.getZ());
            if (alertMode.get() == AlertMode.Chat) {
                ChatUtils.info(msg);
            } else if (alertMode.get() == AlertMode.ActionBar) {
                mc.player.sendMessage(Text.literal(msg), true);
            } else if (alertMode.get() == AlertMode.Sound) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, soundPitch.get().floatValue());
                mc.player.sendMessage(Text.literal(msg), true); // Optional: also show actionbar when sound plays so they know coords
            }
            pendingAlertPos = null;
            ticksSinceAlert = 0;
        }
    }

    private void cleanOutOfRange() {
        ChunkPos playerChunk = mc.player.getChunkPos();
        int maxDist = renderDistance.get();
        int cacheDist = maxDist * 3;

        foundBlocks.removeIf(pos -> Math.abs((pos.getX() >> 4) - playerChunk.x) > cacheDist || Math.abs((pos.getZ() >> 4) - playerChunk.z) > cacheDist);

        suspectedSet.removeIf(sec -> {
            boolean isFar = Math.abs(sec.getSectionX() - playerChunk.x) > cacheDist || Math.abs(sec.getSectionZ() - playerChunk.z) > cacheDist;
            if (isFar) {
                enteredSections.remove(sec);
                confirmedSections.remove(sec);
                retryCounts.remove(sec);
                blastTargets.remove(sec);
            }
            return isFar;
        });

        // Cache render blocks to fix FPS drops
        double maxDistSq = Math.pow(renderDistance.get() * 16.0, 2);
        blocksToRender = foundBlocks.stream()
                .filter(p -> p.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ()) <= maxDistSq)
                .sorted(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ())))
                .collect(Collectors.toList());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || coordsOnly.get()) return;

        java.util.List<BlockPos> currentBlocks = blocksToRender;
        if (currentBlocks == null) return;

        int rendered = 0;
        for (BlockPos pos : currentBlocks) {
            if (rendered >= maxBoxes.get()) break;

            if (blockEsp.get()) {
                event.renderer.box(pos, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
            }

            if (tracers.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tracerColor.get());
            }
            rendered++;
        }

        if (espSections.get()) {
            Color redLine = new Color(255, 0, 0, 255);
            Color redSide = new Color(255, 0, 0, 50);

            for (ChunkSectionPos sec : suspectedSet) {
                boolean hasEntered = enteredSections.contains(sec);
                Color currentLineColor = hasEntered ? redLine : lineColor.get();

                event.renderer.box(sec.getMinX(), sec.getMinY(), sec.getMinZ(), sec.getMaxX() + 1, sec.getMaxY() + 1, sec.getMaxZ() + 1, sideColor.get(), currentLineColor, ShapeMode.Lines, 0);


                if (hasEntered) {
                    BlockPos target = blastTargets.get(sec);
                    if (target != null) {
                        // Render an 8x8 cube centered around the target coordinate
                        double minX = target.getX() - 3.5;
                        double minY = target.getY() - 3.5;
                        double minZ = target.getZ() - 3.5;
                        double maxX = target.getX() + 4.5;
                        double maxY = target.getY() + 4.5;
                        double maxZ = target.getZ() + 4.5;
                        event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, redSide, redLine, ShapeMode.Both, 0);
                    }
                }

            }
        }

        if (itemEsp.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ItemEntity item && (item.getStack().getItem() == Items.ANCIENT_DEBRIS || item.getStack().getItem() == Items.NETHERITE_SCRAP)) {
                    Box bb = item.getBoundingBox();
                    event.renderer.box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
                }
            }
        }
    }
}