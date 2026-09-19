with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# Fix chunkGridColor usage that got left behind
content = re.sub(r'        SettingColor color = chunkGridColor\.get\(\);\n', '        SettingColor color = gridHighColor.get();\n', content)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
