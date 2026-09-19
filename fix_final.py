with open('./src/main/java/com/example/addon/modules/NetheriteFinder.java', 'r') as f:
    content = f.read()

import re

# Fix tick logic inside suspectedSet.contains(playerSec)
content = re.sub(r'''        if \(suspectedSet\.contains\(playerSec\)\) \{
            enteredSections\.add\(playerSec\);

            BlockPos currentTarget = blastTargets\.get\(playerSec\);
            if \(currentTarget == null\) \{
                blastTargets\.put\(playerSec, generateTarget\(playerSec\)\);
            \} else \{
                if \(!mc\.world\.getBlockState\(currentTarget\)\.isOf\(Blocks\.ANCIENT_DEBRIS\)\) \{
                    blastTargets\.put\(playerSec, generateTarget\(playerSec\)\);
                \}
            \}
        \}''', '''        if (suspectedSet.contains(playerSec)) {
            enteredSections.add(playerSec);
        }''', content)

with open('./src/main/java/com/example/addon/modules/NetheriteFinder.java', 'w') as f:
    f.write(content)
