package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.server.level.CubeMapInternal;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ChunkMap.class, priority = 2000)
public abstract class MixinChunkMap_SendPlayerAllChunks implements CubeMapInternal {
    @Shadow @Final private PlayerMap playerMap;

    @Shadow private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    /** <b>MUST</b> match {@link io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk.MixinChunkMap#visibleCubeMap} **/
    @SuppressWarnings({ "JavadocReference", "unused", "MismatchedQueryAndUpdateOfCollection" })
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleCubeMap;

    @Shadow protected abstract void updateChunkTracking(ServerPlayer player, ChunkPos chunkPos, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache, boolean wasLoaded,
                                                        boolean load);

    /**
     * @author NotStirred
     * @reason Overwriting CC's overwrite of vanilla logic. Here we just send all chunks
     */
    @Overwrite
    void updatePlayerStatus(ServerPlayer player, boolean track) {
        if (track) {
            int xFloor = Coords.getCubeXForEntity(player);
            int yFloor = Coords.getCubeYForEntity(player);
            int zFloor = Coords.getCubeZForEntity(player);
            this.playerMap.addPlayer(CubePos.of(xFloor, yFloor, zFloor).asChunkPos().toLong(), player, true);
            this.updatePlayerCubePos(player); //This also sends the vanilla packet, as player#ManagedSectionPos is changed in this method.
        } else {
            SectionPos managedSectionPos = player.getLastSectionPos(); //Vanilla
            CubePos cubePos = CubePos.from(managedSectionPos);
            this.playerMap.removePlayer(cubePos.asChunkPos().toLong(), player);
        }

        //Vanilla
        this.visibleChunkMap.forEach((pos, holder) -> {
            ChunkPos chunkPos = new ChunkPos(pos);
            this.updateChunkTracking(player, chunkPos, new MutableObject<>(), !track, track);
        });

        //CC
        this.visibleCubeMap.forEach((pos, holder) -> {
            CubePos cubePos = CubePos.from(pos);
            this.updateCubeTracking(player, cubePos, new Object[2], !track, track);
        });
    }
}
