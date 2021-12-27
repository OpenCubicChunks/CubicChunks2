package io.github.opencubicchunks.cubicchunks.mixin.debug.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Vector3f;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightHeightmapGetter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.HeightMapRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(HeightMapRenderer.class)
public class MixinHeightMapRenderer {
    private static final boolean RENDER_SERVER_HEIGHTMAPS = System.getProperty("cubicchunks.debug.heightmaprenderer.server", "false").equalsIgnoreCase("true");
    private static final boolean RENDER_LIGHT_HEIGHTMAP = System.getProperty("cubicchunks.debug.heightmaprenderer.render_lightmap", "false").equalsIgnoreCase("true");
    private static final int CHUNK_RENDER_RADIUS = Integer.parseInt(System.getProperty("cubicchunks.debug.heightmaprenderer.radius", "2"));

    @Shadow @Final private Minecraft minecraft;

    @ModifyVariable(method = "render", ordinal = 0, at = @At(value = "STORE", ordinal = 0), require = 1)
    private LevelAccessor useServerHeightMaps(LevelAccessor original) {
        if(RENDER_SERVER_HEIGHTMAPS) {
            return Minecraft.getInstance().getSingleplayerServer().getLevel(Minecraft.getInstance().player.level.dimension());
        }
        return original;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void renderLightHeightMap(PoseStack arg0, MultiBufferSource arg1, double d, double e, double f, CallbackInfo ci, LevelAccessor levelAccessor, BlockPos blockPos, Tesselator tesselator, BufferBuilder bufferBuilder, int i, int j, ChunkAccess chunkAccess) {
        if(!RENDER_LIGHT_HEIGHTMAP) {
            return;
        }
        Heightmap heightmap = ((LightHeightmapGetter) chunkAccess).getLightHeightmap();

        ChunkPos chunkPos = chunkAccess.getPos();
        Vector3f color = new Vector3f(0.8f, 1.0f, 0.0f);

        for(int k = 0; k < 16; ++k) {
            for(int l = 0; l < 16; ++l) {
                int m = SectionPos.sectionToBlockCoord(chunkPos.x, k);
                int n = SectionPos.sectionToBlockCoord(chunkPos.z, l);

                double height = ((float) heightmap.getFirstAvailable(k, l) + 6 * 0.09375F);
                float g = (float)(height - e);
                LevelRenderer.addChainedFilledBoxVertices(bufferBuilder, (double)((float)m + 0.25F) - d, (double)g, (double)((float)n + 0.25F) - f, (double)((float)m + 0.75F) - d, (double)(g + 0.09375F), (double)((float)n + 0.75F) - f, color.x(), color.y(), color.z(), 1.0F);
            }
        }
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = -2))
    private int changeRadiusLower(int constant) {
        return -CHUNK_RENDER_RADIUS;
    }
    @ModifyConstant(method = "render", constant = @Constant(intValue = 2))
    private int changeRadiusUpper(int constant) {
        return CHUNK_RENDER_RADIUS;
    }
}
