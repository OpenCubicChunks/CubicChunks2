package io.github.opencubicchunks.cubicchunks.levelgen.util;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class CubicWorldGenUtils {

    public static boolean areSectionsEmpty(int cubeY, ChunkPos pos, CubeAccess cube) {
        int emptySections = 0;
        for (int yScan = 0; yScan < CubicConstants.DIAMETER_IN_SECTIONS; yScan++) {
            int sectionY = Coords.cubeToSection(cubeY, yScan);
            int sectionIndex = Coords.sectionToIndex(pos.x, sectionY, pos.z);
            LevelChunkSection cubeSection = cube.getSections()[sectionIndex];
            if (cubeSection.hasOnlyAir()) {
                emptySections++;
            }
            if (emptySections == CubicConstants.DIAMETER_IN_SECTIONS) {
                return true;
            }
        }
        return false;
    }
}
