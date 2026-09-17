package com.example.addon.modules;

import com.example.addon.DoritosAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DonutSpawnerFinder extends Module {
    public enum DimensionFilter {
        All,
        Overworld,
        Nether,
        End
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General Settings
    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
            .name("render-distance")
            .description("How far to render the ESP boxes (in chunks).")
            .defaultValue(4)
            .sliderRange(1, 16)
            .build());

    private final Setting<DimensionFilter> dimension = sgGeneral.add(new EnumSetting.Builder<DimensionFilter>()
            .name("dimension")
            .description("Which dimension to run the packet sniffer in.")
            .defaultValue(DimensionFilter.Overworld)
            .build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
            .name("min-y")
            .description("Minimum Y level to search.")
            .defaultValue(-64)
            .sliderRange(-64, 320)
            .build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
            .name("max-y")
            .description("Maximum Y level to search.")
            .defaultValue(100)
            .sliderRange(-64, 320)
            .build());

    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
            .name("chat-alerts")
            .description("Sends a message when a spawner is sniffed.")
            .defaultValue(true)
            .build());

    // Render Settings
    private final Setting<Integer> maxBoxes = sgRender.add(new IntSetting.Builder()
            .name("max-boxes")
            .description("Maximum number of spawners to render at once (prevents lag).")
            .defaultValue(32)
            .sliderRange(1, 128)
            .build());

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
            .name("tracers")
            .description("Draws lines to the found spawners.")
            .defaultValue(true)
            .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .defaultValue(new SettingColor(255, 100, 0, 75))
            .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .defaultValue(new SettingColor(255, 100, 0, 255))
            .build());

    // Thread-safe state
    private final Set<BlockPos> cachedSpawners = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<BlockPos> pendingAlerts = new ConcurrentLinkedQueue<>();

    // Volatile reference for zero-lock thread-safe reading in onRender
    private volatile List<BlockPos> renderList = List.of();

    public DonutSpawnerFinder() {
        super(DoritosAddon.CATEGORY, "donut-spawner-finder", "Sniffs network packets to bypass Donut SMP anti-xray.");
    }

    @Override
    public void onActivate() {
        clearAll();
    }

    @Override
    public void onDeactivate() {
        clearAll();
    }

    private void clearAll() {
        cachedSpawners.clear();
        pendingAlerts.clear();
        renderList = List.of();
    }

    @Override
    public String getInfoString() {
        return String.valueOf(cachedSpawners.size());
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null || !isInCorrectDimension()) return;

        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            handleBlockState(packet.getPos(), packet.getState());
        }
        else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::handleBlockState);
        }
        else if (event.packet instanceof BlockEntityUpdateS2CPacket packet) {
            if (packet.getBlockEntityType() == BlockEntityType.MOB_SPAWNER) {
                addSpawner(packet.getPos());
            }
        }
    }

    private void handleBlockState(BlockPos pos, BlockState state) {
        if (state.isOf(Blocks.SPAWNER)) {
            addSpawner(pos);
        } else if (cachedSpawners.contains(pos)) {
            cachedSpawners.remove(pos);
        }
    }

    private void addSpawner(BlockPos pos) {
        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) return;

        if (!cachedSpawners.contains(pos)) {
            BlockPos immutablePos = pos.toImmutable();
            cachedSpawners.add(immutablePos);

            if (chatAlerts.get()) {
                pendingAlerts.add(immutablePos);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // 1. Flush alerts with a 2-second cooldown to prevent chat spam
        if (mc.player.age % 40 == 0) {
            flushAlerts();
        }

        // 2. Prepare render list and cleanup cache every 5 ticks
        if (mc.player.age % 5 == 0) {
            updateRenderListAndCleanup();
        }
    }

    private void flushAlerts() {
        if (pendingAlerts.isEmpty()) return;

        List<BlockPos> flushed = new ArrayList<>();
        BlockPos p;
        while ((p = pendingAlerts.poll()) != null) {
            flushed.add(p);
        }

        if (flushed.size() == 1) {
            BlockPos pos = flushed.get(0);
            int dist = (int) Math.sqrt(pos.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            ChatUtils.info("§6[Bypass] §fSpawner sniffed at: §e%d, %d, %d §7(%dm)", pos.getX(), pos.getY(), pos.getZ(), dist);
        } else {
            ChatUtils.info("§6[Bypass] §fSniffed §e%d §fnew spawners nearby!", flushed.size());
        }
    }

    private void updateRenderListAndCleanup() {
        double renderDistSq = Math.pow(renderDistance.get() * 16.0, 2);
        double keepDistSq = Math.pow((renderDistance.get() + 2) * 16.0, 2);

        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();

        cachedSpawners.removeIf(pos -> pos.getSquaredDistance(playerX, playerY, playerZ) > keepDistSq);

        List<BlockPos> visible = new ArrayList<>();
        for (BlockPos pos : cachedSpawners) {
            if (pos.getSquaredDistance(playerX, playerY, playerZ) <= renderDistSq) {
                visible.add(pos);
            }
        }

        visible.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(playerX, playerY, playerZ)));

        // Enforce maxBoxes limit to protect renderer
        int limit = Math.min(visible.size(), maxBoxes.get());

        // Atomic update for the render thread
        renderList = List.copyOf(visible.subList(0, limit));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // Grab a local reference to the volatile list to ensure consistency during the frame
        List<BlockPos> currentRenderList = renderList;
        if (currentRenderList.isEmpty() || mc.player == null) return;

        for (BlockPos pos : currentRenderList) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);

            if (tracers.get()) {
                event.renderer.line(
                        RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        lineColor.get()
                );
            }
        }
    }

    private boolean isInCorrectDimension() {
        if (mc.world == null) return false;

        return switch (dimension.get()) {
            case Overworld -> mc.world.getRegistryKey() == World.OVERWORLD;
            case Nether -> mc.world.getRegistryKey() == World.NETHER;
            case End -> mc.world.getRegistryKey() == World.END;
            case All -> true;
        };
    }
}