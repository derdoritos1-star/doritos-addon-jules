with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# Update Text renderer inside onRender3D
# Meteor provides text rendering inside 3D via event.renderer? No, usually through TextRenderer.get().render(...) but for 2D.
# We'll stick to just the HUD in onRender2D to be safe, as it avoids complex 3D text rotation math, and the HUD already tells them what is found. The 3D grid shows the color gradient.
# Let's just make sure we use the HUD properly for score display.

content = re.sub(r'String text = String\.format\("⚠ SUB-ZERO TARGET DETECTED: %d, %d", closest\.getStartX\(\), closest\.getStartZ\(\)\);\n', 'String text = String.format("⚠ SUS CHUNK DETECTED: %d, %d | Score: %d", closest.getStartX(), closest.getStartZ(), chunkScores.getOrDefault(closest, anomalyThreshold.get()));\n', content)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
