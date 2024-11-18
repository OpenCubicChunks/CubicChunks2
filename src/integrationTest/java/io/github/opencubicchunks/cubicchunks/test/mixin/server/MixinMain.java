package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Main.class)
public class MixinMain {
    @Redirect(method = "main", at = @At(value = "INVOKE", target = "Ljava/lang/Runtime;addShutdownHook(Ljava/lang/Thread;)V"), remap = false)
    private static void noShutdownHook(Runtime instance, Thread hook) {
        // Do nothing
    }
}
