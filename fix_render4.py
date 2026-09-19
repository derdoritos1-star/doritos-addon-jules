with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# Fix TextRenderer.get().render(...) signature for 3D text
content = re.sub(r'                TextRenderer\.get\(\)\.render\(text, x, y, z, new meteordevelopment\.meteorclient\.utils\.render\.color\.Color\(255, 255, 255, 255\)\);\n', '                TextRenderer.get().render(text, x, y, z, new meteordevelopment.meteorclient.utils.render.color.Color(255, 255, 255, 255), true);\n', content)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
