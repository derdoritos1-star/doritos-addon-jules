package com.example.addon.modules.sus.signals;

import com.example.addon.modules.sus.SusSignal;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class LightingSignal implements SusSignal {
    @Override
    public double evaluate(WorldChunk chunk, int minY, int maxY) {
        List<BlockPos> lights = new ArrayList<>();
        List<BlockPos> lavas = new ArrayList<>();

        int startY = Math.max(minY, chunk.getBottomY());
        int endY = Math.min(maxY, 60);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = startY; y <= endY; y++) {
                    BlockPos pos = chunk.getPos().getStartPos().add(x, y, z);
                    BlockState state = chunk.getBlockState(pos);

                    if (state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.LANTERN) || state.isOf(Blocks.GLOWSTONE) || state.isOf(Blocks.SHROOMLIGHT)) {
                        lights.add(pos);
                    } else if (state.isOf(Blocks.LAVA)) {
                        lavas.add(pos);
                    }
                }
            }
        }

        if (lights.isEmpty()) return 0.0;

        lights.removeIf(l -> lavas.stream().anyMatch(lava -> l.getSquaredDistance(lava) < 64)); // Radius 8

        if (lights.size() < 3) return 0.0;

        double sumDist = 0;
        List<Double> dists = new ArrayList<>();
        for (int i = 0; i < lights.size() - 1; i++) {
            double d = Math.sqrt(lights.get(i).getSquaredDistance(lights.get(i+1)));
            dists.add(d);
            sumDist += d;
        }

        double mean = sumDist / dists.size();
        double variance = 0;
        for (double d : dists) {
            variance += Math.pow(d - mean, 2);
        }
        double stdDev = Math.sqrt(variance / dists.size());

        if (stdDev == 0) return 1.0;
        if (stdDev > 5.0) return 0.1;
        return 1.0 - (stdDev / 5.0);
    }
}
