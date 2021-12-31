package io.github.opencubicchunks.cubicchunks.network;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelHeightAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

public class PacketCCLevelInfo {
    private final String worldStyle;

    /** The WorldStyle that will be applied to the next constructed ClientLevel */
    private static CubicLevelHeightAccessor.WorldStyle queuedWorldStyle;

    public PacketCCLevelInfo(CubicLevelHeightAccessor.WorldStyle worldStyle) {
        this.worldStyle = worldStyle.name();
    }

    PacketCCLevelInfo(FriendlyByteBuf buf) {
        this.worldStyle = buf.readUtf();
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.worldStyle);
    }

    public static class Handler {
        public static void handle(PacketCCLevelInfo packet, Level level) {
            queuedWorldStyle = CubicLevelHeightAccessor.WorldStyle.valueOf(packet.worldStyle.toUpperCase());
        }
    }

    @Nullable
    public static CubicLevelHeightAccessor.WorldStyle getQueuedWorldStyle() {
        var style = queuedWorldStyle;
        // Clear the queued style afterwards to prevent possible issues with stale data
        queuedWorldStyle = null;
        return style;
    }
}
