package com.example.addon.modules.sus;

import net.minecraft.world.chunk.WorldChunk;

public interface SusSignal {
    double evaluate(WorldChunk chunk, int minY, int maxY);
}
