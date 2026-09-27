package com.example.addon.modules.sus.signals;

import com.example.addon.modules.sus.FloodFillUtil;
import com.example.addon.modules.sus.SusSignal;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;

public class GeometrySignal implements SusSignal {
    @Override
    public double evaluate(WorldChunk chunk, int minY, int maxY) {
        List<FloodFillUtil.Cavity> cavities = FloodFillUtil.findCavities(chunk, minY, maxY);
        double maxCompactness = 0.0;

        for (FloodFillUtil.Cavity cavity : cavities) {
            boolean ignore = false;
            for (BlockPos pos : cavity.blocks) {
                for (Direction dir : Direction.values()) {
                    BlockState s = chunk.getBlockState(pos.offset(dir));
                    if (s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.AMETHYST_BLOCK) || s.isOf(Blocks.BUDDING_AMETHYST) || s.isOf(Blocks.SMOOTH_BASALT)) {
                        ignore = true;
                        break;
                    }
                }
                if (ignore) break;
            }
            if (ignore) continue;

            double compactness = cavity.getCompactness();
            if (cavity.blocks.size() >= 15 && compactness > maxCompactness) {
                maxCompactness = compactness;
            }
        }

        if (maxCompactness < 0.4) return 0.0;
        return Math.min(1.0, (maxCompactness - 0.4) / 0.6);
    }
}
