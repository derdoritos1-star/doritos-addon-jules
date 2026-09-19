with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# Update Render Event logic to use the new 3D text and gradients
render_event = r'''    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        SettingColor color = gridHighColor.get();
        meteordevelopment.meteorclient.utils.render.color.Color outlineColor = new meteordevelopment.meteorclient.utils.render.color.Color(color.r, color.g, color.b, 255);
        meteordevelopment.meteorclient.utils.render.color.Color fillColor = new meteordevelopment.meteorclient.utils.render.color.Color(color.r, color.g, color.b, 60);
        meteordevelopment.meteorclient.utils.render.color.Color vectorColor = new meteordevelopment.meteorclient.utils.render.color.Color(0, 255, 255, 255); // Cyan for vector
        meteordevelopment.meteorclient.utils.render.color.Color heatColor = new meteordevelopment.meteorclient.utils.render.color.Color(255, 100, 0, 150); // Orange for sounds

        // Render Heatmap
        for (BlockPos heatPos : soundHeatmap) {
            event.renderer.box(heatPos.getX() - 0.2, heatPos.getY() - 0.2, heatPos.getZ() - 0.2, heatPos.getX() + 1.2, heatPos.getY() + 1.2, heatPos.getZ() + 1.2, heatColor, heatColor, ShapeMode.Both, 0);
        }

        // Render Prediction Vector
        if (predictedVector != null && !alertHistory.isEmpty()) {
            ChunkPos lastChunk = alertHistory.get(alertHistory.size() - 1);
            int startY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, lastChunk.getCenterX(), lastChunk.getCenterZ());
            int endY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, predictedVector.getCenterX(), predictedVector.getCenterZ());
            event.renderer.line(
                lastChunk.getCenterX(), startY, lastChunk.getCenterZ(),
                predictedVector.getCenterX(), endY, predictedVector.getCenterZ(),
                vectorColor
            );
        }

        for (ChunkPos cPos : alertedChunks) {
            double minX = cPos.getStartX();
            double minZ = cPos.getStartZ();
            double maxX = cPos.getEndX() + 1.0;
            double maxZ = cPos.getEndZ() + 1.0;

            // Calculate the top surface Y for the center of the chunk
            int surfaceY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cPos.getCenterX(), cPos.getCenterZ());

            if (drawBeacon.get()) {
                // Flat red layer 0.5 blocks thick on the surface
                event.renderer.box(minX, surfaceY, minZ, maxX, surfaceY + 0.5, maxZ, fillColor, outlineColor, ShapeMode.Both, 0);
            }

            if (drawTracers.get()) {
                double centerX = cPos.getCenterX();
                double centerZ = cPos.getCenterZ();
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    centerX, surfaceY, centerZ,
                    outlineColor
                );
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null || alertedChunks.isEmpty()) return;

        // Find closest alerted chunk
        ChunkPos closest = null;
        double minDistance = Double.MAX_VALUE;
        for (ChunkPos cPos : alertedChunks) {
            double dist = mc.player.squaredDistanceTo(cPos.getCenterX(), mc.player.getY(), cPos.getCenterZ());
            if (dist < minDistance) {
                minDistance = dist;
                closest = cPos;
            }
        }

        if (closest != null) {
            String text = String.format("⚠ SUB-ZERO TARGET DETECTED: %d, %d", closest.getStartX(), closest.getStartZ());
            TextRenderer.get().begin(1.5, false, true);
            double width = TextRenderer.get().getWidth(text);
            double x = (event.screenWidth - width) / 2.0;
            double y = 20.0;
            TextRenderer.get().render(text, x, y, new meteordevelopment.meteorclient.utils.render.color.Color(255, 50, 50, 255));
            TextRenderer.get().end();
        }
    }'''

new_render_event = r'''    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        meteordevelopment.meteorclient.utils.render.color.Color vectorColor = new meteordevelopment.meteorclient.utils.render.color.Color(0, 255, 255, 255); // Cyan for vector
        meteordevelopment.meteorclient.utils.render.color.Color heatColor = new meteordevelopment.meteorclient.utils.render.color.Color(255, 100, 0, 150); // Orange for sounds

        // Render Heatmap
        for (BlockPos heatPos : soundHeatmap) {
            event.renderer.box(heatPos.getX() - 0.2, heatPos.getY() - 0.2, heatPos.getZ() - 0.2, heatPos.getX() + 1.2, heatPos.getY() + 1.2, heatPos.getZ() + 1.2, heatColor, heatColor, ShapeMode.Both, 0);
        }

        // Render Prediction Vector
        if (predictedVector != null && !alertHistory.isEmpty()) {
            ChunkPos lastChunk = alertHistory.get(alertHistory.size() - 1);
            int startY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, lastChunk.getCenterX(), lastChunk.getCenterZ());
            int endY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, predictedVector.getCenterX(), predictedVector.getCenterZ());
            event.renderer.line(
                lastChunk.getCenterX(), startY, lastChunk.getCenterZ(),
                predictedVector.getCenterX(), endY, predictedVector.getCenterZ(),
                vectorColor
            );
        }

        for (ChunkPos cPos : alertedChunks) {
            double minX = cPos.getStartX();
            double minZ = cPos.getStartZ();
            double maxX = cPos.getEndX() + 1.0;
            double maxZ = cPos.getEndZ() + 1.0;

            // Calculate the top surface Y for the center of the chunk
            int surfaceY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cPos.getCenterX(), cPos.getCenterZ());

            Integer scoreObj = chunkScores.get(cPos);
            int score = scoreObj != null ? scoreObj : anomalyThreshold.get();

            // Gradient logic: Blend from low color to high color based on score (clamped at 5x threshold)
            float ratio = Math.min(1.0f, (float)(score - anomalyThreshold.get()) / (anomalyThreshold.get() * 4.0f));
            SettingColor low = gridLowColor.get();
            SettingColor high = gridHighColor.get();

            int r = (int)(low.r + ratio * (high.r - low.r));
            int g = (int)(low.g + ratio * (high.g - low.g));
            int b = (int)(low.b + ratio * (high.b - low.b));

            meteordevelopment.meteorclient.utils.render.color.Color fillColor = new meteordevelopment.meteorclient.utils.render.color.Color(r, g, b, 60);
            meteordevelopment.meteorclient.utils.render.color.Color outlineColor = new meteordevelopment.meteorclient.utils.render.color.Color(gridLineColor.get().r, gridLineColor.get().g, gridLineColor.get().b, 255);

            if (drawBeacon.get()) {
                // Flat layer 0.5 blocks thick on the surface
                event.renderer.box(minX, surfaceY, minZ, maxX, surfaceY + 0.5, maxZ, fillColor, outlineColor, ShapeMode.Both, 0);
            }

            if (drawTracers.get()) {
                double centerX = cPos.getCenterX();
                double centerZ = cPos.getCenterZ();
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    centerX, surfaceY, centerZ,
                    outlineColor
                );
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null || alertedChunks.isEmpty()) return;

        // 3D Text at chunk positions
        if (drawText.get()) {
            for (ChunkPos pos : alertedChunks) {
                Integer scoreObj = chunkScores.get(pos);
                int score = scoreObj != null ? scoreObj : anomalyThreshold.get();

                int surfaceY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getCenterX(), pos.getCenterZ());
                double x = pos.getStartX() + 8.0;
                double y = surfaceY + 2.0; // Render slightly above the beacon
                double z = pos.getStartZ() + 8.0;

                String text = String.format("[Sus: %d]", score);
                TextRenderer.get().begin(textScale.get(), false, true);
                TextRenderer.get().render(text, x, y, z, new meteordevelopment.meteorclient.utils.render.color.Color(255, 255, 255, 255));
                TextRenderer.get().end();
            }
        }

        // Find closest alerted chunk for HUD
        ChunkPos closest = null;
        double minDistance = Double.MAX_VALUE;
        for (ChunkPos cPos : alertedChunks) {
            double dist = mc.player.squaredDistanceTo(cPos.getCenterX(), mc.player.getY(), cPos.getCenterZ());
            if (dist < minDistance) {
                minDistance = dist;
                closest = cPos;
            }
        }

        if (closest != null) {
            String text = String.format("⚠ SUB-ZERO TARGET DETECTED: %d, %d", closest.getStartX(), closest.getStartZ());
            TextRenderer.get().begin(1.5, false, true);
            double width = TextRenderer.get().getWidth(text);
            double x = (event.screenWidth - width) / 2.0;
            double y = 20.0;
            TextRenderer.get().render(text, x, y, new meteordevelopment.meteorclient.utils.render.color.Color(255, 50, 50, 255));
            TextRenderer.get().end();
        }
    }'''

content = content.replace(render_event, new_render_event)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
