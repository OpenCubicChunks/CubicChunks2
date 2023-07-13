package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import static io.github.opencubicchunks.cubicchunks.test.IntegrationTests.DISABLE_NETWORK;

import java.io.IOException;
import java.net.InetAddress;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DedicatedServer.class)
public class MixinDedicatedServer_DisableNetwork {
    @Redirect(method = "initServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"))
    private void noTcpServer(ServerConnectionListener instance, InetAddress address, int port) throws IOException {
        if (DISABLE_NETWORK) {
            CubicChunks.LOGGER.warn("*".repeat(30));
            CubicChunks.LOGGER.warn("NETWORKING DISABLED!!");
            CubicChunks.LOGGER.warn("*".repeat(30));
        } else {
            instance.startTcpServerListener(address, port);
            CubicChunks.LOGGER.info("Networking Enabled");
        }
    }
}
