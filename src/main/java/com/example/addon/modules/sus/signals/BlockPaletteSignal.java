package com.example.addon.modules.sus.signals;

import com.example.addon.modules.sus.SusSignal;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Set;

public class BlockPaletteSignal implements SusSignal {

    private final Set<Block> DEEPSLATE_PALETTE = Set.of(
            Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.DIRT, Blocks.GRAVEL, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.AIR, Blocks.CAVE_AIR, Blocks.WATER, Blocks.LAVA, Blocks.OBSIDIAN, Blocks.BEDROCK
    );
    private final Set<Block> STONE_PALETTE = Set.of(
            Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.GRAVEL, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.EMERALD_ORE, Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.COPPER_ORE, Blocks.AIR, Blocks.CAVE_AIR, Blocks.WATER, Blocks.LAVA, Blocks.OBSIDIAN, Blocks.BEDROCK
    );

    @Override
    public double evaluate(WorldChunk chunk, int minY, int maxY) {
        int totalBlocks = 0;
        int anomalousBlocks = 0;

        int startY = Math.max(minY, chunk.getBottomY());
        int endY = Math.min(maxY, 60);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = startY; y <= endY; y++) {
                    BlockState state = chunk.getBlockState(chunk.getPos().getStartPos().add(x, y, z));
                    Block b = state.getBlock();
                    totalBlocks++;

                    if (y < 0) {
                        if (!DEEPSLATE_PALETTE.contains(b)) anomalousBlocks++;
                    } else {
                        if (!STONE_PALETTE.contains(b)) anomalousBlocks++;
                    }
                }
            }
        }

        if (totalBlocks == 0) return 0.0;
        double ratio = (double) anomalousBlocks / totalBlocks;

        if (ratio > 0.05) return 1.0;
        return ratio / 0.05;
    }
}
