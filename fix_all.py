import re

with open('./src/main/java/com/example/addon/modules/NetheriteFinder.java', 'r') as f:
    nf = f.read()

# Update module description from Zinc to Doritos
nf = re.sub(r'super\(DoritosAddon\.CATEGORY, "NetheriteFinder", "Zync-style Palette truster\. Never disappears while moving\."\);', 'super(DoritosAddon.CATEGORY, "NetheriteFinder", "Doritos-style Palette truster. Never disappears while moving.");', nf)

nf = re.sub(r'    private final Map<ChunkSectionPos, BlockPos> blastTargets = new ConcurrentHashMap<>\(\);\n', '', nf)
nf = re.sub(r'        blastTargets\.clear\(\);\n', '', nf)
nf = re.sub(r'                blastTargets\.remove\(sec\);\n', '', nf)

nf = re.sub(r'''        if \(suspectedSet\.contains\(playerSec\)\) \{
            enteredSections\.add\(playerSec\);

            BlockPos currentTarget = blastTargets\.get\(playerSec\);
            if \(currentTarget == null\) \{
                blastTargets\.put\(playerSec, generateTarget\(playerSec\)\);
            \} else if \(mc\.player\.age % 100 == 0\) \{
                 // Periodically recalculate target to account for newly found blocks while inside
                blastTargets\.put\(playerSec, generateTarget\(playerSec\)\);
            \}
        \}''', '''        if (suspectedSet.contains(playerSec)) {
            enteredSections.add(playerSec);
        }''', nf)

nf = re.sub(r'''        if \(suspectedSet\.contains\(playerSec\)\) \{
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
        }''', nf)

nf = re.sub(r'''    private BlockPos generateTarget\(ChunkSectionPos sec\) \{[\s\S]*?    private void processPaletteQueue\(\) \{''', '    private void processPaletteQueue() {', nf)

nf = re.sub(r'''                if \(hasEntered\) \{
                    BlockPos target = blastTargets\.get\(sec\);
                    if \(target != null\) \{
                        // Render an 8x8 cube centered around the target coordinate
                        double minX = target\.getX\(\) - 3\.5;
                        double minY = target\.getY\(\) - 3\.5;
                        double minZ = target\.getZ\(\) - 3\.5;
                        double maxX = target\.getX\(\) \+ 4\.5;
                        double maxY = target\.getY\(\) \+ 4\.5;
                        double maxZ = target\.getZ\(\) \+ 4\.5;
                        event\.renderer\.box\(minX, minY, minZ, maxX, maxY, maxZ, redSide, redLine, ShapeMode\.Both, 0\);
                    \}
                \}''', '', nf)

nf = re.sub(r'            Color redSide = blastSideColor\.get\(\);\n', '', nf)

with open('./src/main/java/com/example/addon/modules/NetheriteFinder.java', 'w') as f:
    f.write(nf)

# 2. Fix the GameRendererMixin.java block interaction bug in Freecam
with open('./src/main/java/com/example/addon/mixin/GameRendererMixin.java', 'r') as f:
    gr = f.read()

# Only cancel execution if freecam is actually active from Meteor Client
gr_new = r'''package com.example.addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "updateCrosshairTarget", at = @At("HEAD"), cancellable = true)
    private void onUpdateTargetedEntity(float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.interactionManager != null) {
            Freecam freecam = Modules.get().get(Freecam.class);
            if (freecam != null && freecam.isActive()) {
                client.crosshairTarget = client.player.raycast(4.5F, tickDelta, false);
                ci.cancel();
            }
        }
    }
}
'''
with open('./src/main/java/com/example/addon/mixin/GameRendererMixin.java', 'w') as f:
    f.write(gr_new)
