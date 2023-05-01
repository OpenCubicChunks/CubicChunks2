package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.Comparator;

import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.TicketTypeAccess;
import net.minecraft.server.level.TicketType;

public class CubicTicketType {
    public static final TicketType<CubePos> PLAYER = create("player", Comparator.comparingLong(CubePos::asLong));
    public static final TicketType<CubePos> FORCED = create("forced", Comparator.comparingLong(CubePos::asLong));
    public static final TicketType<CubePos> LIGHT = create("light", Comparator.comparingLong(CubePos::asLong));
    public static final TicketType<CubePos> UNKNOWN = create("unknown", Comparator.comparingLong(CubePos::asLong), 1);
    @UsedFromASM public static final TicketType<CubePos> POST_TELEPORT = create("post_teleport", Comparator.comparingLong(CubePos::asLong), 5);
    public static final TicketType<CubePos> COLUMN = create("column", Comparator.comparingLong(CubePos::asLong));

    public static <T> TicketType<T> create(String nameIn, Comparator<T> comparator) {
        return TicketTypeAccess.createNew(nameIn, comparator, 0L);
    }

    public static <T> TicketType<T> create(String nameIn, Comparator<T> comparator, int lifespanIn) {
        return TicketTypeAccess.createNew(nameIn, comparator, lifespanIn);
    }
}