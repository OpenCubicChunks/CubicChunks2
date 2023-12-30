package io.github.opencubicchunks.cubicchunks.world;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class CubicChunksSavedData extends SavedData {
    public static final String FILE_ID = CubicChunks.MODID;

    public final CubicLevelHeightAccessor.WorldStyle worldStyle;

    public CubicChunksSavedData(CubicLevelHeightAccessor.WorldStyle worldStyle) {
        this.worldStyle = worldStyle;
    }

    public static CubicChunksSavedData load(CompoundTag tag) {
        return new CubicChunksSavedData(CubicLevelHeightAccessor.WorldStyle.valueOf(tag.getString("worldStyle")));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("worldStyle", this.worldStyle.name());
        return tag;
    }
}
