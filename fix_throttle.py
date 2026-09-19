with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# Add throttling for processChunk so it processes a max of 50 chunks per second in background queue
throttle_setup = r'''    private final List<BlockPos> soundHeatmap = new ArrayList<>();
    private ChunkPos predictedVector = null;'''

new_throttle_setup = r'''    private final List<BlockPos> soundHeatmap = new ArrayList<>();
    private ChunkPos predictedVector = null;
    private final java.util.Queue<net.minecraft.world.chunk.WorldChunk> chunkProcessQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();'''

content = content.replace(throttle_setup, new_throttle_setup)

throttle_clear = r'''        soundHeatmap.clear();
        predictedVector = null;'''

new_throttle_clear = r'''        soundHeatmap.clear();
        predictedVector = null;
        chunkProcessQueue.clear();'''

content = content.replace(throttle_clear, new_throttle_clear)

throttle_chunk = r'''    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;
        processChunk(event.chunk());
    }'''

new_throttle_chunk = r'''    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;
        chunkProcessQueue.offer(event.chunk());
    }'''

content = content.replace(throttle_chunk, new_throttle_chunk)

throttle_tick = r'''        if (mc.player.age % 20 != 0) return; // OPTIMIZATION: Only run 1 time per second'''

new_throttle_tick = r'''        // Throttling: Process max 50 chunks per tick
        int processed = 0;
        while (!chunkProcessQueue.isEmpty() && processed < 50) {
            net.minecraft.world.chunk.WorldChunk chunk = chunkProcessQueue.poll();
            if (chunk != null) processChunk(chunk);
            processed++;
        }

        if (mc.player.age % 20 != 0) return; // OPTIMIZATION: Only run 1 time per second'''

content = content.replace(throttle_tick, new_throttle_tick)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
