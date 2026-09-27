package com.example.addon.modules.sus.signals;

import com.example.addon.modules.sus.SusSignal;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.WorldChunk;

public class RedstoneClusterSignal implements SusSignal {
    @Override
    public double evaluate(WorldChunk chunk, int minY, int maxY) {
        int redstoneCount = 0;

        int startY = Math.max(minY, chunk.getBottomY());
        int endY = Math.min(maxY, 60);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = startY; y <= endY; y++) {
                    BlockState state = chunk.getBlockState(chunk.getPos().getStartPos().add(x, y, z));
                    Block b = state.getBlock();

                    if (b == Blocks.REDSTONE_WIRE || b == Blocks.REPEATER || b == Blocks.COMPARATOR ||
                        b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.OBSERVER || b == Blocks.DISPENSER || b == Blocks.DROPPER) {
                        redstoneCount++;
                    }
                }
            }
        }

        if (redstoneCount == 0) return 0.0;
        return Math.min(1.0, redstoneCount / 20.0);
    }
}
