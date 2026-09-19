import re

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    nf = f.read()

# I found the file contents, this is the version BEFORE any massive overhaul was even requested at the start of today!
# This is exactly what was requested by the user.
