package io.github.opencubicchunks.cubicchunks.server.level;

import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.level.ServerPlayer;

@UsedFromASM
public class FixedPlayerDistanceCubeTracker extends CubeTracker {
    @UsedFromASM
    public final Long2ByteMap cubes = new Long2ByteOpenHashMap();
    protected final int range;
    private final CubicDistanceManager cubicDistanceManager; // this$0

    public FixedPlayerDistanceCubeTracker(CubicDistanceManager cubicDistanceManager, int i) {
        super(i + 2, 16, 256);
        this.range = i;
        this.cubes.defaultReturnValue((byte) (i + 2));
        this.cubicDistanceManager = cubicDistanceManager;
    }

    protected int getLevel(long cubePosIn) {
        return this.cubes.get(cubePosIn);
    }

    protected void setLevel(long cubePosIn, int level) {
        byte b0;
        if (level > this.range) {
            b0 = this.cubes.remove(cubePosIn);
        } else {
            b0 = this.cubes.put(cubePosIn, (byte) level);
        }
        this.chunkLevelChanged(cubePosIn, b0, level);
    }

    protected void chunkLevelChanged(long cubePosIn, int oldLevel, int newLevel) {
    }

    protected int getLevelFromSource(long pos) {
        return this.hasPlayerInChunk(pos) ? 0 : Integer.MAX_VALUE;
    }

    private boolean hasPlayerInChunk(long cubePosIn) {
        ObjectSet<ServerPlayer> cubePlayers = cubicDistanceManager.getPlayersPerCube().get(cubePosIn);
        return cubePlayers != null && !cubePlayers.isEmpty();
    }

    @UsedFromASM
    public void runAllUpdates() {
        this.runUpdates(Integer.MAX_VALUE);
    }
}