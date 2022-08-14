package io.github.opencubicchunks.cubicchunks.mixin.core.client.progress;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.client.gui.screens.CubicLevelLoadingScreen;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public class MixinLevelLoadingScreen extends Screen {

    @Shadow @Final private static Object2IntMap<ChunkStatus> COLORS;

    protected MixinLevelLoadingScreen(Component titleIn) {
        super(titleIn);
    }

    @Inject(method = "renderChunks", at = @At("HEAD"), cancellable = true)
    private static void renderCubes(PoseStack mStack, StoringChunkProgressListener trackerParam,
                                    int xBase, int yBase, int scale, int spacing, CallbackInfo ci) {

        Level level = Minecraft.getInstance().getSingleplayerServer().overworld();
        if (level == null || !((CubicLevelHeightAccessor) level).isCubic()) {
            return;
        }

        ci.cancel();
        CubicLevelLoadingScreen.doRender(mStack, trackerParam, xBase, yBase, scale, spacing, COLORS);
    }
}