package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import javax.annotation.Nullable;

public class LightSurfaceTrackerSection extends SurfaceTrackerSection {
    public LightSurfaceTrackerSection() {
        this(MAX_SCALE, 0, null);
    }

    public LightSurfaceTrackerSection(int scale, int scaledY, SurfaceTrackerSection parent) {
        super(scale, scaledY, parent, (byte) -1);
    }

    public LightSurfaceTrackerSection(int scale, int scaledY, SurfaceTrackerSection parent, HeightmapNode cube) {
        super(scale, scaledY, parent, cube, (byte) -1);
    }

    protected LightSurfaceTrackerSection createNewChild(int newScale, int newScaledY, HeightmapNode cube) {
        if (newScale == 0) {
            return new LightSurfaceTrackerSection(newScale, newScaledY, this, cube);
        } else {
            return new LightSurfaceTrackerSection(newScale, newScaledY, this);
        }
    }

    private LightSurfaceTrackerSection getRoot() {
        SurfaceTrackerSection section = this;
        while (section.parent != null) {
            section = section.parent;
        }
        return (LightSurfaceTrackerSection) section;
    }

    @Nullable
    public LightSurfaceTrackerSection getSectionAbove() {
        if (scale != 0) {
            throw new IllegalStateException("Attempted to get section above for a non-zero scale section");
        }
        // TODO this can be optimized - don't need to go to the root every time, just the lowest node that is a parent of both this node and the node above.
        return (LightSurfaceTrackerSection) this.getRoot().getMinScaleNode(scaledY + 1);
    }

    /**
     * Used when upgrading CubePrimers to BigCubes; should never be used elsewhere.
     */
    public void upgradeNode(HeightmapNode node) {
        if (this.scale != 0) {
            throw new IllegalStateException("Attempted to upgrade node on a non-zero scale section");
        }
        if (this.cubeOrNodes == null) {
            throw new IllegalStateException("Attempting to upgrade node " + node + " for an unloaded surface tracker section");
        }
        this.cubeOrNodes = node;
    }
}
