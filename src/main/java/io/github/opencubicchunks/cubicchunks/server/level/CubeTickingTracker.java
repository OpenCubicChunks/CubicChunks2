package io.github.opencubicchunks.cubicchunks.server.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;

public class CubeTickingTracker extends CubeTracker {

    public CubeTickingTracker() {
        super(0, 0, 0);
    }

    public void addTicket(long l, Ticket<?> ticket) {
        throw new Error("ASM didn't apply");
    }

    public <T> void addTicket(TicketType<T> ticketType, CubePos chunkPos, int i, T object) {
        throw new Error("ASM didn't apply");
    }

    public void removeTicket(long l, Ticket<?> ticket) {
        throw new Error("ASM didn't apply");
    }

    public <T> void removeTicket(TicketType<T> ticketType, CubePos chunkPos, int i, T object) {
        throw new Error("ASM didn't apply");
    }

    public void replacePlayerTicketsLevel(int i) {
        throw new Error("ASM didn't apply");
    }

    public int getLevel(CubePos chunkPos) {
        throw new Error("ASM didn't apply");
    }

    public void runAllUpdates() {
        throw new Error("ASM didn't apply");
    }

    public String getTicketDebugString(long l) {
        throw new Error("ASM didn't apply");
    }

    @Override protected int getLevelFromSource(long pos) {
        throw new Error("ASM didn't apply");
    }

    @Override protected int getLevel(long sectionPos) {
        throw new Error("ASM didn't apply");
    }

    @Override protected void setLevel(long sectionPos, int level) {
        throw new Error("ASM didn't apply");
    }
}
