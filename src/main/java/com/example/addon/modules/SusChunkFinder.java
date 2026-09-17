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

    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("target-blocks")
            .description("Blocks to search for.")
            .defaultValue(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.SHULKER_BOX, Blocks.SPAWNER, Blocks.HOPPER)
            .build()
    );

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder().name("scan-radius").description("Configurable range for chunk scanning.").defaultValue(4).sliderRange(1, 32).build());
    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder().name("threshold").description("Minimum unnatural blocks required to flag a chunk as sus.").defaultValue(5).sliderRange(1, 100).build());
    private final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder().name("scan-delay").description("Ticks to wait between scans to prevent TPS drops/lag.").defaultValue(10).sliderRange(0, 100).build());
    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder().name("min-y").defaultValue(-64).sliderRange(-64, 320).build());
    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder().name("max-y").defaultValue(320).sliderRange(-64, 320).build());

    private final Setting<LoggingMode> loggingMode = sgNotifications.add(new EnumSetting.Builder<LoggingMode>().name("logging-mode").description("How to notify when a sus chunk is found.").defaultValue(LoggingMode.Chat).build());

    private final Setting<Boolean> renderBoxes = sgRender.add(new BoolSetting.Builder().name("render-boxes").description("Draw bounding boxes over sus chunks.").defaultValue(true).build());
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder().name("tracers").description("Lines from player to blocks.").defaultValue(true).build());
    private final Setting<Integer> maxBoxes = sgRender.add(new IntSetting.Builder().name("max-boxes").defaultValue(50).sliderRange(1, 200).build());
    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder().name("fill-color").description("Fill color of the bounding box.").defaultValue(new SettingColor(255, 50, 50, 40)).build());
    private final Setting<SettingColor> outlineColor = sgRender.add(new ColorSetting.Builder().name("outline-color").description("Outline color of the bounding box.").defaultValue(new SettingColor(255, 50, 50, 255)).build());

    private final Setting<Boolean> renderBeam = sgRender.add(new BoolSetting.Builder().name("render-beam").description("Renders a vertical beam indicating the chunk.").defaultValue(true).build());
    private final Setting<SettingColor> beamColor = sgRender.add(new ColorSetting.Builder().name("beam-color").description("Color of the vertical beam.").defaultValue(new SettingColor(255, 255, 255, 100)).build());


    private final Set<BlockPos> foundBlocks = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, Integer> chunkBlockCount = new ConcurrentHashMap<>();
    private final Set<ChunkPos> alertedChunks = ConcurrentHashMap.newKeySet();

    private final java.util.Queue<ChunkScanRequest> scanQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private int ticksSinceScan = 0;
    private volatile java.util.List<BlockPos> blocksToRender = new java.util.ArrayList<>();

    public SusChunkFinder() {
        super(DoritosAddon.CATEGORY, "SusChunkFinder", "Finds suspicious chunks on DonutSMP. NOTE: Below Y=0 (Deepslate), servers heavily obfuscate blocks. You must manually dig down to load them!");
    }

    @Override
    public void onActivate() {
        foundBlocks.clear();
        chunkBlockCount.clear();
        alertedChunks.clear();
        scanQueue.clear();
        ticksSinceScan = 0;
    }

    @Override
    public void onDeactivate() {
        foundBlocks.clear();
        chunkBlockCount.clear();
        alertedChunks.clear();
        scanQueue.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            checkAndCache(packet.getPos(), packet.getState());
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::checkAndCache);
        }
    }

    private void checkAndCache(BlockPos pos, BlockState state) {
        if (targetBlocks.get().contains(state.getBlock())) {
            if (pos.getY() >= minY.get() && pos.getY() <= maxY.get()) {
                if (foundBlocks.add(pos)) {
                    checkThreshold(pos, state.getBlock());
                }
            }
        } else {
            if (foundBlocks.remove(pos)) {
                ChunkPos cPos = new ChunkPos(pos);
                int count = chunkBlockCount.getOrDefault(cPos, 1) - 1;
                if (count <= 0) {
                    chunkBlockCount.remove(cPos);
                    alertedChunks.remove(cPos);
                } else {
                    chunkBlockCount.put(cPos, count);
                }
            }
        }
    }

    private void checkThreshold(BlockPos pos, Block block) {
        ChunkPos cPos = new ChunkPos(pos);
        int count = chunkBlockCount.getOrDefault(cPos, 0) + 1;
        chunkBlockCount.put(cPos, count);

        if (count >= threshold.get() && alertedChunks.add(cPos)) {
            sendNotification(cPos, count);
        }
    }

    private void sendNotification(ChunkPos cPos, int count) {
        String msg = String.format("Sus chunk found at %d, %d (>= %d blocks)", cPos.getStartX(), cPos.getStartZ(), count);
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
    private void onChunkData(ChunkDataEvent event) {
        Chunk chunk = event.chunk();
        for (int i = 0; i < chunk.getSectionArray().length; i++) {
            ChunkSection section = chunk.getSectionArray()[i];
            if (section != null && !section.isEmpty()) {
                for (Block block : targetBlocks.get()) {
                    if (section.hasAny(state -> state.isOf(block))) {
                        int sectionY = chunk.getBottomSectionCoord() + i;
                        if (sectionY * 16 <= maxY.get() && (sectionY * 16 + 15) >= minY.get()) {
                            scanQueue.offer(new ChunkScanRequest(chunk.getPos(), sectionY));
                            break;
                        }
                    }
                }
            }
        }
    }

    private void scanSection(ChunkPos chunkPos, int sectionY, ChunkSection section) {
        int startY = sectionY * 16;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (targetBlocks.get().contains(state.getBlock())) {
                        int worldY = startY + y;
                        if (worldY >= minY.get() && worldY <= maxY.get()) {
                            BlockPos pos = chunkPos.getStartPos().add(x, worldY, z);
                            if (foundBlocks.add(pos)) {
                                checkThreshold(pos, state.getBlock());
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        ticksSinceScan++;
        if (ticksSinceScan >= scanDelay.get()) {
            int processed = 0;
            while (processed < 5 && !scanQueue.isEmpty()) {
                ChunkScanRequest req = scanQueue.poll();
                if (req != null) {
                    if (mc.world.getChunkManager().isChunkLoaded(req.pos().x, req.pos().z)) {
                        net.minecraft.world.chunk.WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(req.pos().x, req.pos().z);
                        if (chunk != null) {
                            int secIdx = req.sectionY() - chunk.getBottomSectionCoord();
                            if (secIdx >= 0 && secIdx < chunk.getSectionArray().length) {
                                ChunkSection section = chunk.getSectionArray()[secIdx];
                                if (section != null && !section.isEmpty()) {
                                    scanSection(req.pos(), req.sectionY(), section);
                                }
                            }
                        }
                    }
                    processed++;
                }
            }
            if (processed > 0) {
                ticksSinceScan = 0;
            }
        }

        if (mc.player.age % 40 == 0) {
            ChunkPos playerChunk = mc.player.getChunkPos();
            int maxDist = scanRadius.get();
            int cacheDist = maxDist * 2;
            foundBlocks.removeIf(pos -> {
                boolean remove = Math.abs((pos.getX() >> 4) - playerChunk.x) > cacheDist || Math.abs((pos.getZ() >> 4) - playerChunk.z) > cacheDist;
                if (remove) {
                    ChunkPos cPos = new ChunkPos(pos);
                    int count = chunkBlockCount.getOrDefault(cPos, 1) - 1;
                    if (count <= 0) {
                        chunkBlockCount.remove(cPos);
                        alertedChunks.remove(cPos);
                    } else {
                        chunkBlockCount.put(cPos, count);
                    }
                }
                return remove;
            });

            // Cache render blocks
            double maxDistSq = Math.pow(scanRadius.get() * 16.0, 2);
            blocksToRender = foundBlocks.stream()
                    .filter(p -> p.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ()) <= maxDistSq)
                    .filter(p -> chunkBlockCount.getOrDefault(new ChunkPos(p), 0) >= threshold.get())
                    .sorted(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ())))
                    .limit(maxBoxes.get())
                    .collect(Collectors.toList());
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || !renderBoxes.get()) return;

        List<BlockPos> currentBlocks = blocksToRender;
        if (currentBlocks == null) return;

        for (BlockPos pos : currentBlocks) {
            event.renderer.box(pos, fillColor.get(), outlineColor.get(), ShapeMode.Both, 0);

            if (tracers.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, outlineColor.get());
            }

            if (renderBeam.get()) {
                ChunkPos cPos = new ChunkPos(pos);
                double minX = cPos.getStartX() + 7;
                double maxX = cPos.getStartX() + 9;
                double minZ = cPos.getStartZ() + 7;
                double maxZ = cPos.getStartZ() + 9;
                double minY = pos.getY();
                double maxY = 320.0;
                event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, beamColor.get(), beamColor.get(), ShapeMode.Both, 0);
            }
        }
    }

    private record ChunkScanRequest(ChunkPos pos, int sectionY) {}
}
