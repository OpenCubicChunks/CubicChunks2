package io.github.opencubicchunks.cubicchunks.mixin.core.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    //Fixes the broken weather at high heights

    private static final int ROLLOVER = 64 * 4; //Will look stupid if it isn't a multiple of 4
    private int betterUForUV, betterVForUV;

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @Inject(
        method = "renderSnowAndRain",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;",
            shift = At.Shift.AFTER,
            ordinal = 1
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void prepareBetterUV(LightTexture lightTexture, float f, double d, double e, double g, CallbackInfo ci, float h, Level level, int i, int j, int k,
                                 Tesselator tesselator, BufferBuilder bufferBuilder, int l, int m, float n, BlockPos.MutableBlockPos mutableBlockPos, int o, int p, int q,
                                 double r, double s, Biome biome, int t, int u, int v, int w) {
        this.betterUForUV = u % ROLLOVER;
        this.betterVForUV = v % ROLLOVER;

        if (this.betterUForUV > this.betterVForUV) {
            this.betterVForUV += ROLLOVER;
        }
    }

    //This is the best way I could figure out how ot do with mixins.
    //Before VertexConsumer.uv() is called, it will swap u and v with betterUForUV and betterVForUV
    //This means uv() gets the rolled-over values to improve precision
    //After the call, it will swap them back so that the original values are used in any other places
    @ModifyVariable(
        method = "renderSnowAndRain",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;vertex(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
            shift = At.Shift.AFTER
        ),
        index = 29
    )
    private int swapUPre(int u) {
        int temp = this.betterUForUV;
        this.betterUForUV = u;

        return temp;
    }

    @ModifyVariable(
        method = "renderSnowAndRain",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;vertex(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
            shift = At.Shift.AFTER
        ),
        index = 30
    )
    private int swapVPre(int v) {
        int temp = this.betterVForUV;
        this.betterVForUV = v;

        return temp;
    }

    @ModifyVariable(
        method = "renderSnowAndRain",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;uv(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
            shift = At.Shift.AFTER
        ),
        index = 29
    )
    private int swapUPost(int u) {
        int temp = this.betterUForUV;
        this.betterUForUV = u;

        return temp;
    }

    @ModifyVariable(
        method = "renderSnowAndRain",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;uv(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
            shift = At.Shift.AFTER
        ),
        index = 30
    )
    private int swapVPost(int v) {
        int temp = this.betterVForUV;
        this.betterVForUV = v;

        return temp;
    }
}
