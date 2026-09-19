with open('./src/main/java/com/example/addon/modules/NetheriteFinder.java', 'r') as f:
    content = f.read()

import re

# Update module description and module class name from Zinc to Doritos Addon if needed
# Wait, I grep'd for "zinc" in NetheriteFinder.java and there were no results.
# Let's check the description line in the constructor just in case.
