package io.github.opencubicchunks.cubicchunks.mock.interfaces;

import io.github.opencubicchunks.cc_core.world.ColumnCubeMapGetter;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightHeightmapGetter;
import net.minecraft.world.level.BlockGetter;

public interface BlockGetterLightHeightmapGetterColumnCubeMapGetter extends BlockGetter, LightHeightmapGetter, ColumnCubeMapGetter {
}
