package io.github.opencubicchunks.cubicchunks.server.level;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

// TODO: DASM, have to duplicate whole class
public class CubeTicketTracker extends CubeTracker {
    private final CubicDistanceManager cubicDistanceManager;

    public CubeTicketTracker(CubicDistanceManager cubicDistanceManager) {
        //TODO: change the arguments passed into super to CCCubeManager or CCColumnManager
        super(CubeMap.MAX_CUBE_DISTANCE + 2, 16, 256);
        this.cubicDistanceManager = cubicDistanceManager;
    }

    @Override protected int getLevelFromSource(long pos) {
        SortedArraySet<Ticket<?>> sortedArraySet = cubicDistanceManager.getCubeTickets().get(pos);
        if (sortedArraySet == null) {
            return Integer.MAX_VALUE;
        } else {
            return sortedArraySet.isEmpty() ? Integer.MAX_VALUE : sortedArraySet.first().getTicketLevel();
        }
    }

    protected int getLevel(long sectionPos) {
        if (!cubicDistanceManager.isCubeToRemove(sectionPos)) {
            ChunkHolder chunkHolder = cubicDistanceManager.getCube(sectionPos);
            if (chunkHolder != null) {
                return chunkHolder.getTicketLevel();
            }
        }

        return ChunkMap.MAX_CHUNK_DISTANCE + 1;
    }

    protected void setLevel(long sectionPos, int level) {
        ChunkHolder chunkHolder = cubicDistanceManager.getCube(sectionPos);
        int i = chunkHolder == null ? ChunkMap.MAX_CHUNK_DISTANCE + 1 : chunkHolder.getTicketLevel();
        if (i != level) {
            chunkHolder = cubicDistanceManager.updateCubeScheduling(sectionPos, level, chunkHolder, i);
            if (chunkHolder != null) {
                cubicDistanceManager.getCubesToUpdateFutures().add(chunkHolder);
            }

        }
    }

    public int runDistanceUpdates(int distance) {
        return this.runUpdates(distance);
    }
}