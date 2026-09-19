import re

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

# The anomalyThreshold is 50 default. If a chunk gets 5 chests, localScore = 50. It alerts immediately.
# Let's reduce the normal chest score from 10 to 2 or 0, or just add a proper mineshaft ignore, because we lost the full mineshaft ignore.
# The user wants "how it was before the massive list" but apparently "it's highlighting every chunk" meaning it's too sensitive.
# Wait! In the old version, normal chests gave 10 score. 5 chests = 50.
# The `surfaceTraceMaxY` was added but it defaults to 50, which captures dirt/stone if they are in the list?
# Ah! In the trace block list, did I add something common?
# b == Blocks.CRAFTING_TABLE || b == Blocks.GLASS || b == Blocks.FARMLAND || b == Blocks.END_ROD ||
# b == Blocks.ANVIL || b == Blocks.BREWING_STAND || b == Blocks.CAULDRON || b == Blocks.BOOKSHELF ||
# b == Blocks.JUKEBOX || b == Blocks.NOTE_BLOCK || b == Blocks.FURNACE || b == Blocks.SMOKER ||
# b == Blocks.BLAST_FURNACE || b == Blocks.ENCHANTING_TABLE || b == Blocks.NETHER_PORTAL || b == Blocks.ENDER_CHEST ||
# b instanceof net.minecraft.block.BedBlock;
# Is one of these super common? None of these spawn naturally underground except maybe in strongholds, mineshafts, ancient cities...
# BUT wait! Mineshafts have rails and cobwebs (which we don't scan here).
# Ancient Cities have chests and note blocks. We filter sculk.

# What about the Entity tracker?
# if (entity instanceof ChestMinecartEntity || entity instanceof HopperMinecartEntity) {
#   minecartCounts.put(...)
# }
# if (minecartCount >= 3) addScore(anomalyThreshold) -> Mineshafts have chest minecarts!

# To fix "every chunk is highlighted", let's adjust the scoring. Let's just make Chests and Barrels give 0, meaning we ONLY care about actual Traces (crafting tables, beds, etc) or Shulkers, unless they explicitly change it.

fix_chest = r'''                    } else if (type == net.minecraft.block.entity.BlockEntityType.CHEST || type == net.minecraft.block.entity.BlockEntityType.BARREL || type == net.minecraft.block.entity.BlockEntityType.HOPPER) {
                        localScore += 10; // Nerf normal chests to avoid mineshaft flags
                    }'''
new_chest = r'''                    } else if (type == net.minecraft.block.entity.BlockEntityType.CHEST || type == net.minecraft.block.entity.BlockEntityType.BARREL || type == net.minecraft.block.entity.BlockEntityType.HOPPER) {
                        localScore += 0; // Ignore standard chests by default to prevent natural mineshaft spam. Adjust via settings in full versions.
                    }'''
content = content.replace(fix_chest, new_chest)

# Fix minecarts giving instant alerts (mineshafts spam)
fix_carts = r'''            for (Map.Entry<ChunkPos, Integer> entry : minecartCounts.entrySet()) {
                if (entry.getValue() >= 3) { // 3+ storage carts indicates a stash/farm, bypassing standard mineshafts
                    addScore(entry.getKey(), anomalyThreshold.get());
                }
            }'''
new_carts = r'''            for (Map.Entry<ChunkPos, Integer> entry : minecartCounts.entrySet()) {
                if (entry.getValue() >= 10) { // Set to 10+ to avoid mineshaft spam completely
                    addScore(entry.getKey(), anomalyThreshold.get());
                }
            }'''
content = content.replace(fix_carts, new_carts)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
