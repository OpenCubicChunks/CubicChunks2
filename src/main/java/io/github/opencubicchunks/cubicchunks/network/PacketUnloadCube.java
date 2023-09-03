package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLevelLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEventListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class PacketUnloadCube {
    private final CubePos pos;

    public PacketUnloadCube(CubePos posIn) {
        this.pos = posIn;
    }

    PacketUnloadCube(FriendlyByteBuf buf) {
        this.pos = CubePos.of(buf.readInt(), buf.readInt(), buf.readInt());
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
    }

    public static class Handler {
        public static void handle(PacketUnloadCube packet, Level level) {
            ChunkSource chunkSource = level.getChunkSource();
            ((ClientCubeCache) chunkSource).drop(packet.pos.getX(), packet.pos.getY(), packet.pos.getZ());
            LevelLightEngine lightEngine = chunkSource.getLightEngine();

            for (int i = 0; i < CubicConstants.SECTION_COUNT; ++i) {
                SectionPos pos = Coords.sectionPosByIndex(packet.pos, i);
                ((ClientLevel) level).setSectionDirtyWithNeighbors(pos.x(), pos.y(), pos.z());
                lightEngine.updateSectionStatus(pos, true);
            }
            ((CubicLightEventListener) lightEngine).setLightEnabled(packet.pos, false);

        }
    }
}