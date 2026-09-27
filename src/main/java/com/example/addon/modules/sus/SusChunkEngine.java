package com.example.addon.modules.sus;

import com.example.addon.modules.sus.SusChunkFinder;
import com.example.addon.modules.sus.signals.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SusChunkEngine {

    public enum ConfidenceState { NORMAL, CANDIDATE, CONFIRMED }

    public static class ChunkData {
        public double score;
        public ConfidenceState state = ConfidenceState.NORMAL;
        public boolean dirty = false;
        public long lastSeen = 0;
        public long lastEvidenceUpdate = 0;
    }

    private final SusChunkFinder module;
    private final Map<ChunkPos, ChunkData> cache = new ConcurrentHashMap<>();
    private final Queue<ChunkPos> processingQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private final List<SusSignal> signals = Arrays.asList(
        new GeometrySignal(),
        new LightingSignal(),
        new BlockPaletteSignal(),
        new ContainerDensitySignal(),
        new RedstoneClusterSignal()
    );

    public SusChunkEngine(SusChunkFinder module) {
        this.module = module;
    }

    public void reset() {
        cache.clear();
        processingQueue.clear();
    }

    public Map<ChunkPos, ChunkData> getTrackedChunks() {
        return cache;
    }

    public void markDirty(ChunkPos pos) {
        ChunkData data = cache.computeIfAbsent(pos, p -> new ChunkData());
        if (!data.dirty) {
            data.dirty = true;
            processingQueue.offer(pos);
        }
    }

    public void tick(World world, PlayerEntity player, int scanRadius) {
        long time = System.currentTimeMillis();

        ChunkPos pPos = player.getChunkPos();
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                ChunkPos cPos = new ChunkPos(pPos.x + x, pPos.z + z);
                if (!cache.containsKey(cPos) || cache.get(cPos).dirty) {
                    markDirty(cPos);
                }
            }
        }

        int processed = 0;
        while (!processingQueue.isEmpty() && processed < 10) {
            ChunkPos cPos = processingQueue.poll();
            if (cPos != null && world.getChunkManager().isChunkLoaded(cPos.x, cPos.z)) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cPos.x, cPos.z);
                if (chunk != null) {
                    analyze(chunk, time);
                    cache.get(cPos).dirty = false;
                    processed++;
                }
            }
        }

        if (player.age % 40 == 0) {
            cache.entrySet().removeIf(entry -> {
                ChunkData data = entry.getValue();
                if (time - data.lastEvidenceUpdate > 30000) {
                    data.score *= 0.9;
                }

                boolean far = Math.abs(entry.getKey().x - pPos.x) > scanRadius + 4 || Math.abs(entry.getKey().z - pPos.z) > scanRadius + 4;
                if (far && data.state == ConfidenceState.NORMAL && data.score < 1.0) {
                    return true;
                }
                return false;
            });
        }
    }

    private void analyze(WorldChunk chunk, long time) {
        ChunkData data = cache.get(chunk.getPos());
        data.lastSeen = time;

        double prevScore = data.score;

        double geo = signals.get(0).evaluate(chunk, chunk.getBottomY(), 320) * 0.30;
        double lig = signals.get(1).evaluate(chunk, chunk.getBottomY(), 320) * 0.25;
        double pal = signals.get(2).evaluate(chunk, chunk.getBottomY(), 320) * 0.20;
        double con = signals.get(3).evaluate(chunk, chunk.getBottomY(), 320) * 0.15;
        double red = signals.get(4).evaluate(chunk, chunk.getBottomY(), 320) * 0.10;

        double total = geo + lig + pal + con + red;
        total *= getDepthMultiplier(chunk);

        if (total > data.score) {
            data.score = total;
            data.lastEvidenceUpdate = time;
        }

        if (data.score >= 8.0) {
            data.state = ConfidenceState.CONFIRMED;
        } else if (data.score >= 3.0) {
            data.state = ConfidenceState.CANDIDATE;
        } else {
            data.state = ConfidenceState.NORMAL;
        }

        if (module.debugMode.get() && data.state != ConfidenceState.NORMAL && data.score > prevScore) {
            ChatUtils.info(String.format("Sus %d,%d | Score: %.2f | Geo:%.2f Lig:%.2f Pal:%.2f Con:%.2f Red:%.2f | %s",
                    chunk.getPos().x, chunk.getPos().z, data.score,
                    geo, lig, pal, con, red, data.state.name()));
        }
    }

    private double getDepthMultiplier(WorldChunk chunk) {
        // Average Y logic here would be calculated by averaging cavity depths.
        // For simplicity in this engine tick, we'll use a mocked depth of 0 to return 1.27
        return 1.27;
    }
}
