package com.example.addon.modules.sus.signals;

import com.example.addon.modules.sus.FloodFillUtil;
import com.example.addon.modules.sus.SusSignal;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;

public class ContainerDensitySignal implements SusSignal {
    @Override
    public double evaluate(WorldChunk chunk, int minY, int maxY) {
        List<FloodFillUtil.Cavity> cavities = FloodFillUtil.findCavities(chunk, minY, maxY);
        if (cavities.isEmpty()) return 0.0;

        int containerCount = 0;
        for (BlockPos pos : chunk.getBlockEntityPositions()) {
            if (pos.getY() >= minY && pos.getY() <= maxY) {
                BlockEntity be = chunk.getBlockEntity(pos);
                if (be != null) {
                    BlockEntityType<?> t = be.getType();
                    if (t == BlockEntityType.CHEST || t == BlockEntityType.TRAPPED_CHEST || t == BlockEntityType.BARREL || t == BlockEntityType.SHULKER_BOX || t == BlockEntityType.HOPPER || t == BlockEntityType.ENDER_CHEST) {
                        containerCount++;
                    }
                }
            }
        }

        if (containerCount == 0) return 0.0;

        double minVolume = Double.MAX_VALUE;
        for (FloodFillUtil.Cavity cav : cavities) {
            double vol = cav.getVolume();
            if (vol < minVolume) minVolume = vol;
        }

        if (minVolume == Double.MAX_VALUE) return 0.0;

        double density = containerCount / minVolume;
        return Math.min(1.0, density / 0.05); // Max out at 5% density
    }
}
