package io.github.opencubicchunks.cubicchunks.test.mixin;

import java.io.IOException;
import java.net.InetAddress;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DedicatedServer.class)
public class MixinDedicatedServer {
    @Redirect(method = "initServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"))
    private void noTcpServer(ServerConnectionListener instance, InetAddress address, int port) throws IOException {

        if (!System.getProperty("cubicchunks.test.disableNetwork", "false").equals("true")) {
            instance.startTcpServerListener(address, port);
            CubicChunks.LOGGER.info("Networking Enabled");
        } else {
            CubicChunks.LOGGER.warn("*".repeat(30));
            CubicChunks.LOGGER.warn("NETWORKING DISABLED!!");
            CubicChunks.LOGGER.warn("*".repeat(30));
        }
    }
}
