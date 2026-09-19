with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# Update Render Settings
render_settings = r'''    private final Setting<SettingColor> chunkGridColor = sgRender.add(new ColorSetting.Builder().name("chunk-grid-color").description("Color of the highlighted chunk borders.").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Setting<Boolean> drawTracers = sgRender.add(new BoolSetting.Builder().name("draw-tracers").description("Draw lines from player to flagged chunks.").defaultValue(true).build());
    private final Setting<Boolean> drawBeacon = sgRender.add(new BoolSetting.Builder().name("draw-beacon").description("Draw highlight on the flagged chunk.").defaultValue(true).build());'''

new_render_settings = r'''    private final Setting<SettingColor> gridLowColor = sgRender.add(new ColorSetting.Builder().name("grid-low-score").description("Color for low score chunks.").defaultValue(new SettingColor(255, 255, 0, 100)).build());
    private final Setting<SettingColor> gridHighColor = sgRender.add(new ColorSetting.Builder().name("grid-high-score").description("Color for high score chunks.").defaultValue(new SettingColor(255, 0, 0, 100)).build());
    private final Setting<SettingColor> gridLineColor = sgRender.add(new ColorSetting.Builder().name("grid-line-color").description("Color for chunk border lines.").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Setting<Boolean> drawTracers = sgRender.add(new BoolSetting.Builder().name("draw-tracers").description("Draw lines from player to flagged chunks.").defaultValue(true).build());
    private final Setting<Boolean> drawBeacon = sgRender.add(new BoolSetting.Builder().name("draw-beacon").description("Draw highlight on the flagged chunk.").defaultValue(true).build());
    private final Setting<Boolean> drawText = sgRender.add(new BoolSetting.Builder().name("draw-text").description("Draw 3D text showing the score.").defaultValue(true).build());
    private final Setting<Double> textScale = sgRender.add(new DoubleSetting.Builder().name("text-scale").description("Scale of the 3D text.").defaultValue(1.5).sliderRange(0.5, 3.0).build());'''

content = content.replace(render_settings, new_render_settings)

# Update Render Event
render_event = r'''    @EventHandler
    private void onRender(Render3DEvent event) {
        if (alertedChunks.isEmpty()) return;

        for (ChunkPos pos : alertedChunks) {
            int cx = pos.getStartX();
            int cz = pos.getStartZ();
            int cy = mc.world.getBottomY();
            int h = mc.world.getHeight();

            if (drawBeacon.get()) {
                event.renderer.box(cx, cy, cz, cx + 16, cy + h, cz + 16, chunkGridColor.get(), chunkGridColor.get(), ShapeMode.Lines, 0);
            }

            if (drawTracers.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, cx + 8, cy + (h / 2.0), cz + 8, chunkGridColor.get());
            }
        }

        if (predictedVector != null) {
            int cx = predictedVector.getStartX() + 8;
            int cz = predictedVector.getStartZ() + 8;
            int cy = mc.player.getBlockY();
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, cx, cy, cz, new SettingColor(0, 255, 255, 255));
        }
    }'''

new_render_event = r'''    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (alertedChunks.isEmpty()) return;

        for (ChunkPos pos : alertedChunks) {
            int cx = pos.getStartX();
            int cz = pos.getStartZ();
            int cy = mc.world.getBottomY();
            int h = mc.world.getHeight();

            Integer scoreObj = chunkScores.get(pos);
            int score = scoreObj != null ? scoreObj : anomalyThreshold.get();

            // Gradient logic: Blend from low color to high color based on score (clamped at 5x threshold)
            float ratio = Math.min(1.0f, (float)(score - anomalyThreshold.get()) / (anomalyThreshold.get() * 4.0f));
            SettingColor low = gridLowColor.get();
            SettingColor high = gridHighColor.get();

            int r = (int)(low.r + ratio * (high.r - low.r));
            int g = (int)(low.g + ratio * (high.g - low.g));
            int b = (int)(low.b + ratio * (high.b - low.b));
            int a = (int)(low.a + ratio * (high.a - low.a));
            SettingColor fillColor = new SettingColor(r, g, b, a);

            if (drawBeacon.get()) {
                event.renderer.box(cx, cy, cz, cx + 16, cy + h, cz + 16, fillColor, gridLineColor.get(), ShapeMode.Both, 0);
            }

            if (drawTracers.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, cx + 8, mc.player.getY(), cz + 8, gridLineColor.get());
            }
        }

        if (predictedVector != null) {
            int cx = predictedVector.getStartX() + 8;
            int cz = predictedVector.getStartZ() + 8;
            int cy = mc.player.getBlockY();
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, cx, cy, cz, new SettingColor(0, 255, 255, 255));
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!drawText.get() || alertedChunks.isEmpty()) return;

        for (ChunkPos pos : alertedChunks) {
            Integer scoreObj = chunkScores.get(pos);
            int score = scoreObj != null ? scoreObj : anomalyThreshold.get();

            double x = pos.getStartX() + 8.0;
            double y = mc.player.getY() + 1.0;
            double z = pos.getStartZ() + 8.0;

            String text = String.format("[Sus: %d]", score);
            TextRenderer.get().begin(textScale.get(), false, true);
            TextRenderer.get().render(text, x, y, z, new meteordevelopment.meteorclient.utils.render.color.Color(255, 255, 255, 255));
            TextRenderer.get().end();
        }
    }'''

content = content.replace(render_event, new_render_event)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
