package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.TicketAccess;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;

public class CubeTickingTracker extends CubeTracker {
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
    private final Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = new Long2ObjectOpenHashMap<>();

    public CubeTickingTracker() {
        super(34, 16, 256);
        this.chunks.defaultReturnValue((byte) 33);
    }

    private SortedArraySet<Ticket<?>> getTickets(long l) {
        return this.tickets.computeIfAbsent(l, (lx) -> SortedArraySet.create(4));
    }

    private int getTicketLevelAt(SortedArraySet<Ticket<?>> sortedArraySet) {
        return sortedArraySet.isEmpty() ? 34 : sortedArraySet.first().getTicketLevel();
    }

    public void addTicket(long l, Ticket<?> ticket) {
        SortedArraySet<Ticket<?>> sortedArraySet = this.getTickets(l);
        int i = this.getTicketLevelAt(sortedArraySet);
        sortedArraySet.add(ticket);
        if (ticket.getTicketLevel() < i) {
            this.update(l, ticket.getTicketLevel(), true);
        }
    }

    public <T> void addTicket(TicketType<T> ticketType, CubePos chunkPos, int i, T object) {
        this.addTicket(chunkPos.asLong(), TicketAccess.createNew(ticketType, i, object));
    }

    public void removeTicket(long l, Ticket<?> ticket) {
        SortedArraySet<Ticket<?>> sortedArraySet = this.getTickets(l);
        sortedArraySet.remove(ticket);
        if (sortedArraySet.isEmpty()) {
            this.tickets.remove(l);
        }

        this.update(l, this.getTicketLevelAt(sortedArraySet), false);
    }

    public <T> void removeTicket(TicketType<T> ticketType, CubePos chunkPos, int i, T object) {
        Ticket<T> ticket = TicketAccess.createNew(ticketType, i, object);
        this.removeTicket(chunkPos.asLong(), ticket);
    }

    public void replacePlayerTicketsLevel(int i) {
        List<Pair<Ticket<CubePos>, Long>> list = new ArrayList<>();
        ObjectIterator<Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>>> var3 = this.tickets.long2ObjectEntrySet().iterator();

        Ticket<CubePos> ticket;
        while (var3.hasNext()) {
            Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> entry = var3.next();

            for (Ticket<?> value : entry.getValue()) {
                ticket = (Ticket<CubePos>) value;
                if (ticket.getType() == CubicTicketType.PLAYER) {
                    list.add(Pair.of(ticket, entry.getLongKey()));
                }
            }
        }

        for (Pair<Ticket<CubePos>, Long> ticketLongPair : list) {
            Long pos = ticketLongPair.getSecond();
            ticket = ticketLongPair.getFirst();
            this.removeTicket(pos, ticket);
            CubePos chunkPos = new CubePos(pos);
            TicketType<CubePos> ticketType = ticket.getType();
            this.addTicket(ticketType, chunkPos, i, chunkPos);
        }
    }

    protected int getLevelFromSource(long pos) {
        SortedArraySet<Ticket<?>> sortedArraySet = this.tickets.get(pos);
        return sortedArraySet != null && !sortedArraySet.isEmpty() ? sortedArraySet.first().getTicketLevel() : Integer.MAX_VALUE;
    }

    public int getLevel(CubePos chunkPos) {
        return this.getLevel(chunkPos.asLong());
    }

    protected int getLevel(long sectionPos) {
        return this.chunks.get(sectionPos);
    }

    protected void setLevel(long sectionPos, int level) {
        if (level > 33) {
            this.chunks.remove(sectionPos);
        } else {
            this.chunks.put(sectionPos, (byte) level);
        }
    }

    public void runAllUpdates() {
        this.runUpdates(Integer.MAX_VALUE);
    }

    public String getTicketDebugString(long l) {
        SortedArraySet<Ticket<?>> sortedArraySet = this.tickets.get(l);
        return sortedArraySet != null && !sortedArraySet.isEmpty() ? sortedArraySet.first().toString() : "no_ticket";
    }
}
