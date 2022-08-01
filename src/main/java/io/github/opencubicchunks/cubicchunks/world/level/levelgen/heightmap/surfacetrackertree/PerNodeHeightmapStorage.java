package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.util.Arrays;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;

// WARNING: THIS IS VERY VERY VERY SLOW AND SHOULD ONLY BE USED FOR DEBUGGING
public class PerNodeHeightmapStorage implements HeightmapStorage {
    private final File storageFolder;

    public PerNodeHeightmapStorage(File storageFolder) {
        this.storageFolder = storageFolder;
        try {
            Files.createDirectories(storageFolder.toPath());
        } catch (IOException cause) {
            throw new UncheckedIOException("Failed to create storage folder for heightmaps", cause);
        }
    }

    @Override
    public void unloadNode(int globalSectionX, int globalSectionZ, SurfaceTrackerNode node) {
        // Save data to storage
        long[] longs = node.heights.getRaw();
        ByteBuffer data = ByteBuffer.allocate(longs.length * Long.BYTES); //.order(ByteOrder.LITTLE_ENDIAN);
        data.asLongBuffer().put(longs);

        try {
            save(globalSectionX, globalSectionZ, node);
        } catch (IOException e) {
            e.printStackTrace();
            assert false; // crash in debug
        }

        // Clear node
        if (node.scale == 0) {
            ((SurfaceTrackerLeaf) node).node = null;
        } else {
            Arrays.fill(((SurfaceTrackerBranch) node).children, null);
        }
        node.parent = null;
    }

    @Override
    public SurfaceTrackerNode loadNode(int globalSectionX, int globalSectionZ, SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY) {
        SurfaceTrackerNode loaded = null;
        try {
            loaded = load(parent, globalSectionX, globalSectionZ, scaledY, scale, heightmapType);
        } catch (FileNotFoundException ignored) {
            // file not there? we ignore it
        } catch (IOException e) {
            e.printStackTrace();
            assert false; // crash in debug
        }

        if (loaded != null) {
            loaded.parent = parent;
        }
        return loaded;
    }

    private void save(int globalSectionX, int globalSectionZ, SurfaceTrackerNode node) throws IOException {
        String filename = String.format("%d.%d.%d.%d.%d.stn", globalSectionX, globalSectionZ, node.scaledY, node.scale, node.heightmapType);
        try (OutputStream file = new FileOutputStream(new File(storageFolder, filename))) {
            long[] heights = node.heights.getRaw();

            ByteBuffer buffer = ByteBuffer.allocate(heights.length * Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            buffer.asLongBuffer().put(heights);
            file.write(buffer.array());
        }
    }
    private SurfaceTrackerNode load(SurfaceTrackerBranch parent, int globalSectionX, int globalSectionZ, int scaledY, int scale, byte heightmapType) throws IOException {
        String filename = String.format("%d.%d.%d.%d.%d.stn", globalSectionX, globalSectionZ, scaledY, scale, heightmapType);
        try (InputStream file = new FileInputStream(new File(storageFolder, filename))) {
            LongBuffer buffer = ByteBuffer.wrap(file.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
            long[] heights = new long[buffer.limit()];
            buffer.get(heights);
            if (scale == 0) {
                return new SurfaceTrackerLeaf(scaledY, parent, heightmapType, heights);
            } else {
                return new SurfaceTrackerBranch(scale, scaledY, parent, heightmapType, heights);
            }
        }
    }
}