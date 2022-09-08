package io.github.opencubicchunks.cubicchunks.mixin.asmfixes.common.ticket;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DistanceManager.class)
public class DistanceManagerFixes {
    @Dynamic @Redirect(method = {"addCubePlayer", "removeCubePlayer"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;cube__ReplaceWithMixin()Lio/github/opencubicchunks/cc_core/api/CubePos;"))
    private CubePos sectionPosToCubePos(SectionPos sectionPos) {
        return CubePos.from(sectionPos);
    }
}
