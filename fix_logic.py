with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'r') as f:
    content = f.read()

import re

# Add sliders for Dungeon Ignore, Chest Threshold, Block Whitelist
settings_block = r'''    private final Setting<TriggerMode> triggerMode = sgGeneral.add(new EnumSetting.Builder<TriggerMode>().name("trigger-mode").description("What to search for.").defaultValue(TriggerMode.All).build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder().name("scan-radius").description("Radius in chunks to scan around player.").defaultValue(4).sliderRange(1, 32).build());
    private final Setting<Integer> anomalyThreshold = sgGeneral.add(new IntSetting.Builder().name("anomaly-threshold").description("Score threshold for alerting.").defaultValue(50).sliderRange(1, 1000).build());'''

new_settings_block = r'''    private final Setting<TriggerMode> triggerMode = sgGeneral.add(new EnumSetting.Builder<TriggerMode>().name("trigger-mode").description("What to search for.").defaultValue(TriggerMode.All).build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder().name("scan-radius").description("Radius in chunks to scan around player.").defaultValue(4).sliderRange(1, 32).build());
    private final Setting<Integer> containerThreshold = sgGeneral.add(new IntSetting.Builder().name("container-threshold").description("Minimum chests/barrels/shulkers to flag.").defaultValue(5).sliderRange(1, 200).build());
    private final Setting<Integer> anomalyThreshold = sgGeneral.add(new IntSetting.Builder().name("anomaly-threshold").description("Score threshold for alerting.").defaultValue(50).sliderRange(1, 1000).build());
    private final Setting<Boolean> ignoreDungeons = sgGeneral.add(new BoolSetting.Builder().name("ignore-dungeons").description("Ignore mineshafts and ancient cities.").defaultValue(True).build());'''

content = content.replace(settings_block, new_settings_block.replace('True', 'true'))

# Update Minecart Entity Logic for Ignore Dungeons
minecart_logic = r'''                } else if (entity instanceof ChestMinecartEntity || entity instanceof HopperMinecartEntity) {
                    minecartCounts.put(cPos, minecartCounts.getOrDefault(cPos, 0) + 1);
                } else if (entity instanceof PassiveEntity || entity instanceof VillagerEntity) {'''

new_minecart_logic = r'''                } else if (!ignoreDungeons.get() && (entity instanceof ChestMinecartEntity || entity instanceof HopperMinecartEntity)) {
                    minecartCounts.put(cPos, minecartCounts.getOrDefault(cPos, 0) + 1);
                } else if (entity instanceof PassiveEntity || entity instanceof VillagerEntity) {'''

content = content.replace(minecart_logic, new_minecart_logic)


# Update Block Entity Logic
be_logic = r'''        // 1. Scan Block Entities as a fallback (if anti-xray happens to leak them)
        if (mode == TriggerMode.All || mode == TriggerMode.StorageBase) {
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                net.minecraft.block.entity.BlockEntity be = chunk.getBlockEntity(pos);
                if (be != null) {
                    net.minecraft.block.entity.BlockEntityType<?> type = be.getType();
                    if (type == net.minecraft.block.entity.BlockEntityType.SHULKER_BOX || type == net.minecraft.block.entity.BlockEntityType.TRAPPED_CHEST) {
                        localScore += anomalyThreshold.get(); // 100% Player Stash
                    } else if (type == net.minecraft.block.entity.BlockEntityType.CHEST || type == net.minecraft.block.entity.BlockEntityType.BARREL || type == net.minecraft.block.entity.BlockEntityType.HOPPER) {
                        localScore += 10; // Nerf normal chests to avoid mineshaft flags
                    }
                }
            }
        }'''

new_be_logic = r'''        // 1. Scan Block Entities as a fallback (if anti-xray happens to leak them)
        if (mode == TriggerMode.All || mode == TriggerMode.StorageBase) {
            int containerCount = 0;
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                net.minecraft.block.entity.BlockEntity be = chunk.getBlockEntity(pos);
                if (be != null) {
                    net.minecraft.block.entity.BlockEntityType<?> type = be.getType();
                    if (type == net.minecraft.block.entity.BlockEntityType.SHULKER_BOX || type == net.minecraft.block.entity.BlockEntityType.TRAPPED_CHEST || type == net.minecraft.block.entity.BlockEntityType.ENDER_CHEST) {
                        localScore += anomalyThreshold.get(); // 100% Player Stash
                    } else if (type == net.minecraft.block.entity.BlockEntityType.CHEST || type == net.minecraft.block.entity.BlockEntityType.BARREL || type == net.minecraft.block.entity.BlockEntityType.HOPPER) {
                        containerCount++;
                    } else if (type == net.minecraft.block.entity.BlockEntityType.MOB_SPAWNER && !ignoreDungeons.get()) {
                        localScore += anomalyThreshold.get(); // If we don't ignore dungeons, flag spawners
                    }
                }
            }
            if (containerCount >= containerThreshold.get()) {
                localScore += anomalyThreshold.get();
            }
        }'''

content = content.replace(be_logic, new_be_logic)

# Update Comprehensive Scan
scan_logic = r'''            // Ancient City False Positive Filter
            if (section.hasAny(state -> state.isOf(Blocks.SCULK) || state.isOf(Blocks.SCULK_SENSOR) || state.isOf(Blocks.SCULK_VEIN))) {
                continue; // Ignore this section to prevent flagging redstone in Ancient Cities
            }'''

new_scan_logic = r'''            // Dungeon & Ancient City False Positive Filter
            if (ignoreDungeons.get() && section.hasAny(state -> state.isOf(Blocks.SCULK) || state.isOf(Blocks.SCULK_SENSOR) || state.isOf(Blocks.SCULK_VEIN) || state.isOf(Blocks.COBWEB) || state.isOf(Blocks.RAIL) || state.isOf(Blocks.SPAWNER))) {
                continue; // Ignore this section to prevent flagging redstone in Ancient Cities or mineshafts
            }'''

content = content.replace(scan_logic, new_scan_logic)

with open('./src/main/java/com/example/addon/modules/SusChunkFinder.java', 'w') as f:
    f.write(content)
