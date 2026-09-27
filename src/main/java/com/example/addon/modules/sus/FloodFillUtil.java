package com.example.addon.modules.sus;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class FloodFillUtil {

    public static class Cavity {
        public final Set<BlockPos> blocks = new HashSet<>();
        public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        public void add(BlockPos pos) {
            blocks.add(pos);
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        public double getVolume() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        public double getCompactness() {
            if (blocks.isEmpty()) return 0.0;
            return blocks.size() / getVolume();
        }
    }

    public static List<Cavity> findCavities(WorldChunk chunk, int minY, int maxY) {
        List<Cavity> cavities = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        int startY = Math.max(minY, chunk.getBottomY());
        int endY = Math.min(maxY, 60);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = startY; y <= endY; y++) {
                    BlockPos startPos = chunk.getPos().getStartPos().add(x, y, z);
                    if (visited.contains(startPos)) continue;

                    BlockState state = chunk.getBlockState(startPos);
                    if (state.isAir() || state.isOf(Blocks.CAVE_AIR)) {
                        Cavity cavity = new Cavity();
                        Queue<BlockPos> queue = new LinkedList<>();
                        queue.offer(startPos);
                        visited.add(startPos);

                        while (!queue.isEmpty()) {
                            BlockPos curr = queue.poll();
                            cavity.add(curr);

                            for (Direction dir : Direction.values()) {
                                BlockPos neighbor = curr.offset(dir);
                                // Stay within chunk for performance limits on DFS/BFS
                                if (neighbor.getX() >= chunk.getPos().getStartX() && neighbor.getX() <= chunk.getPos().getEndX() &&
                                    neighbor.getZ() >= chunk.getPos().getStartZ() && neighbor.getZ() <= chunk.getPos().getEndZ() &&
                                    neighbor.getY() >= startY && neighbor.getY() <= endY) {

                                    if (!visited.contains(neighbor)) {
                                        BlockState nState = chunk.getBlockState(neighbor);
                                        if (nState.isAir() || nState.isOf(Blocks.CAVE_AIR)) {
                                            visited.add(neighbor);
                                            queue.offer(neighbor);
                                        }
                                    }
                                }
                            }
                        }
                        if (cavity.blocks.size() > 10) {
                            cavities.add(cavity);
                        }
                    } else {
                        visited.add(startPos);
                    }
                }
            }
        }
        return cavities;
    }
}
