with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# Increase distance for cleanup to avoid pop-in/pop-out if flying fast. Let's make it 64 instead of 32 chunks for cleanup, which is 1024 blocks.
# Also clean up heatmaps to avoid memory leak if player travels far
gc_logic = r'''        // Clear ghost chunks after /rtp
        alertedChunks.removeIf(cPos -> Math.abs(cPos.x - playerChunk.x) > 32 || Math.abs(cPos.z - playerChunk.z) > 32);
        chunkScores.keySet().removeIf(cPos -> Math.abs(cPos.x - playerChunk.x) > 32 || Math.abs(cPos.z - playerChunk.z) > 32);
        if (alertedChunks.isEmpty()) { alertHistory.clear(); soundHeatmap.clear(); predictedVector = null; }'''

new_gc_logic = r'''        // Strict Garbage Collection: Clear memory of distant chunks
        int maxDist = 64; // Approx 1000 blocks
        alertedChunks.removeIf(cPos -> Math.abs(cPos.x - playerChunk.x) > maxDist || Math.abs(cPos.z - playerChunk.z) > maxDist);
        chunkScores.keySet().removeIf(cPos -> Math.abs(cPos.x - playerChunk.x) > maxDist || Math.abs(cPos.z - playerChunk.z) > maxDist);
        soundHeatmap.removeIf(bPos -> Math.abs((bPos.getX() >> 4) - playerChunk.x) > maxDist || Math.abs((bPos.getZ() >> 4) - playerChunk.z) > maxDist);

        if (alertedChunks.isEmpty()) { alertHistory.clear(); soundHeatmap.clear(); predictedVector = null; }'''

content = content.replace(gc_logic, new_gc_logic)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
