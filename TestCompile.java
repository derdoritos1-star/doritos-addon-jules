import net.minecraft.client.MinecraftClient;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.Heightmap;
public class TestCompile {
    public void test() {
        WorldChunk chunk = null;
        int y = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, 0, 0);
    }
}
