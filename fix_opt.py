with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# We will throttle chunk processing during movement using a queue to prevent FPS drop from scanning.
# First add the queue to variables:
queue_str = r'''    private final List<BlockPos> soundHeatmap = new ArrayList<>();
    private ChunkPos predictedVector = null;'''
new_queue_str = r'''    private final List<BlockPos> soundHeatmap = new ArrayList<>();
    private ChunkPos predictedVector = null;
    private final java.util.Queue<net.minecraft.world.chunk.WorldChunk> chunkQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();'''
content = content.replace(queue_str, new_queue_str)

# Clear queue in deactivate
clear_str = r'''        soundHeatmap.clear();
        predictedVector = null;'''
new_clear_str = r'''        soundHeatmap.clear();
        predictedVector = null;
        chunkQueue.clear();'''
content = content.replace(clear_str, new_clear_str)

# Add to queue in onChunkData
chunk_data_str = r'''    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;
        processChunk(event.chunk());
    }'''
new_chunk_data_str = r'''    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;
        chunkQueue.offer(event.chunk());
    }'''
content = content.replace(chunk_data_str, new_chunk_data_str)

# Process queue in Tick
tick_str = r'''    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (mc.player.age % 20 != 0) return; // OPTIMIZATION: Only run 1 time per second'''
new_tick_str = r'''    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        int processed = 0;
        while (!chunkQueue.isEmpty() && processed < 10) {
            net.minecraft.world.chunk.WorldChunk chunk = chunkQueue.poll();
            if (chunk != null) processChunk(chunk);
            processed++;
        }

        if (mc.player.age % 20 != 0) return; // OPTIMIZATION: Only run 1 time per second'''
content = content.replace(tick_str, new_tick_str)


# Fix the box thickness: 0.5 -> 0.2
box_str = r'''event.renderer.box(minX, surfaceY, minZ, maxX, surfaceY + 0.5, maxZ, fillColor, outlineColor, ShapeMode.Both, 0);'''
new_box_str = r'''event.renderer.box(minX, surfaceY, minZ, maxX, surfaceY + 0.2, maxZ, fillColor, outlineColor, ShapeMode.Both, 0);'''
content = content.replace(box_str, new_box_str)


with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
