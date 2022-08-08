package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.WIDTH_BLOCKS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.Nullable;

public class InterleavedHeightmapStorage implements HeightmapStorage {
    private static final int REGION_WIDTH_IN_NODES = 64;
    private static final int NODE_POSITION_SHIFT = MathUtil.log2(REGION_WIDTH_IN_NODES);
    private static final int NODE_POSITION_MASK = (1 << NODE_POSITION_SHIFT) - 1;

    private static final int ENTRIES_PER_FILE = REGION_WIDTH_IN_NODES * REGION_WIDTH_IN_NODES * WIDTH_BLOCKS * WIDTH_BLOCKS;

    private final Object2ReferenceOpenHashMap<NodeRegionPosition, BitSet> fileCache = new Object2ReferenceOpenHashMap<>(64);

    private final File storageFolder;
    private boolean isClosed = false;

    public InterleavedHeightmapStorage(File storageFolder) {
        this.storageFolder = storageFolder;
        try {
            Files.createDirectories(storageFolder.toPath());
        } catch (IOException cause) {
            throw new UncheckedIOException("Failed to create storage folder for heightmaps", cause);
        }
    }

    private String getRegionName(NodeRegionPosition nodeRegionPosition) {
        return String.format("%d.%d.%d.%d.%d.str",
            nodeRegionPosition.regionX,
            nodeRegionPosition.regionZ,
            nodeRegionPosition.scaledY,
            nodeRegionPosition.scale,
            nodeRegionPosition.heightmapType
        );
    }

    @Override public void saveNode(int globalSectionX, int globalSectionZ, SurfaceTrackerNode node) {
        if (isClosed) {
            throw new IllegalStateException("Heightmap storage already closed!");
        }

        if (!node.requiresSave()) {
            return;
        }

        int regionPosX = globalSectionX >> NODE_POSITION_SHIFT;
        int regionPosZ = globalSectionZ >> NODE_POSITION_SHIFT;

        NodeRegionPosition nodeRegionPosition = new NodeRegionPosition(regionPosX, regionPosZ,
            node.getScale(), node.getScaledY(), node.getRawType());
        String regionFileName = getRegionName(nodeRegionPosition);

        try {
            BitSet bits = fileCache.remove(nodeRegionPosition);
            if (bits == null) {
                ByteBuffer data;
                Path filePath = storageFolder.toPath().resolve(regionFileName);
                if (Files.exists(filePath)) {
                    data = ByteBuffer.wrap(Files.readAllBytes(filePath));
                    bits = BitSet.valueOf(data.position(0));
                } else {
                    bits = new BitSet(ENTRIES_PER_FILE * SurfaceTrackerNode.getBitsForScale(node.getScale()));
                }
            }

            writeNode(globalSectionX, globalSectionZ, node, bits);
            // clear after writing so that if it fails we attempt to write again
            node.clearRequiresSave();

            fileCache.put(nodeRegionPosition, bits);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable @Override public SurfaceTrackerNode loadNode(int globalSectionX, int globalSectionZ, SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY) {
        if (isClosed) {
            throw new IllegalStateException("Heightmap storage already closed!");
        }

        int regionPosX = globalSectionX >> NODE_POSITION_SHIFT;
        int regionPosZ = globalSectionZ >> NODE_POSITION_SHIFT;

        String regionFileName = String.format("%d.%d.%d.%d.%d.str", regionPosX, regionPosZ, scaledY, scale, heightmapType);

        try {
            NodeRegionPosition nodeRegionPosition = new NodeRegionPosition(regionPosX, regionPosZ, scale, scaledY, heightmapType);
            BitSet bits = fileCache.get(nodeRegionPosition);
            if (bits == null) {
                ByteBuffer data;
                Path filePath = storageFolder.toPath().resolve(regionFileName);
                if (Files.exists(filePath)) {
                    try (InputStream inputStream = new InflaterInputStream(new FileInputStream(filePath.toFile()))) {
                        data = ByteBuffer.wrap(inputStream.readAllBytes());
                    }
                } else {
                    return null;
                }
                bits = BitSet.valueOf(data.position(0));
                fileCache.put(nodeRegionPosition, bits);
            }

            SurfaceTrackerNode node;
            if (scale == 0) {
                node = new SurfaceTrackerLeaf(scaledY, parent, heightmapType);
            } else {
                node = new SurfaceTrackerBranch(scale, scaledY, parent, heightmapType);
            }

            readNode(globalSectionX, globalSectionZ, node, bits);

            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeNode(int globalSectionX, int globalSectionZ, SurfaceTrackerNode node, BitSet data) {
        int localNodeX = globalSectionX & NODE_POSITION_MASK;
        int localNodeZ = globalSectionZ & NODE_POSITION_MASK;

        int chunkIdx = localNodeX + localNodeZ * REGION_WIDTH_IN_NODES;

        int bitsForEntry = SurfaceTrackerNode.getBitsForScale(node.getScale());

        for (int blockZ = 0; blockZ < WIDTH_BLOCKS; blockZ++) {
            for (int blockX = 0; blockX < WIDTH_BLOCKS; blockX++) {
                int idx = chunkIdx * (WIDTH_BLOCKS * WIDTH_BLOCKS) + (blockX + blockZ * WIDTH_BLOCKS);
                int height = bin2gray(node.getRawHeight(blockX, blockZ));
                for (int bitIdx = 0; bitIdx < bitsForEntry; bitIdx++) {
                    int offset = bitIdx * (REGION_WIDTH_IN_NODES * REGION_WIDTH_IN_NODES) * (WIDTH_BLOCKS * WIDTH_BLOCKS);
                    int bit = (height >>> bitIdx) & 0x1;

                    if (bit != 0) {
                        data.set(idx + offset);
                    }
                }
            }
        }
    }

    private void readNode(int globalSectionX, int globalSectionZ, SurfaceTrackerNode node, BitSet data) {
        int localNodeX = globalSectionX & NODE_POSITION_MASK;
        int localNodeZ = globalSectionZ & NODE_POSITION_MASK;

        int chunkIdx = localNodeX + localNodeZ * REGION_WIDTH_IN_NODES;

        int bitsForEntry = SurfaceTrackerNode.getBitsForScale(node.getScale());

        for (int blockX = 0; blockX < WIDTH_BLOCKS; blockX++) {
            for (int blockZ = 0; blockZ < WIDTH_BLOCKS; blockZ++) {
                int idx = chunkIdx * (WIDTH_BLOCKS * WIDTH_BLOCKS) + (blockX + blockZ * WIDTH_BLOCKS);

                int height = 0;
                for (int bitIdx = 0; bitIdx < bitsForEntry; bitIdx++) {
                    int offset = bitIdx * (REGION_WIDTH_IN_NODES * REGION_WIDTH_IN_NODES) * (WIDTH_BLOCKS * WIDTH_BLOCKS);
                    height |= (data.get(idx + offset) ? 1 : 0) << bitIdx;
                }
                node.setRawHeight(blockX, blockZ, gray2bin(height));
            }
        }
    }

    @Override public void flush() throws IOException {
        if (isClosed) {
            return;
        }

        IOException suppressed = null; // java is stupid
        for (ObjectIterator<Map.Entry<NodeRegionPosition, BitSet>> iterator = this.fileCache.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<NodeRegionPosition, BitSet> entry = iterator.next();
            NodeRegionPosition nodeRegionPosition = entry.getKey();
            BitSet bitSet = entry.getValue();
            try {
                try (OutputStream outputStream = new DeflaterOutputStream(new FileOutputStream(
                    this.storageFolder.toPath().resolve(getRegionName(nodeRegionPosition)).toFile()))) {
                    outputStream.write(bitSet.toByteArray());
                    iterator.remove(); // successfully wrote the data, it should be removed from the cache
                }
            } catch (IOException e) { // add any exceptions to a single exception to throw after iteration
                if (suppressed == null) {
                    suppressed = e;
                } else {
                    suppressed.addSuppressed(e);
                }
            }
        }

        if (suppressed != null) {
            throw suppressed;
        }
    }

    @Override public void close() throws IOException {
        if (isClosed) {
            return;
        }

        isClosed = true;
        flush();
    }

    @Override public File storageDirectory() {
        return this.storageFolder;
    }

    private static int bin2gray(int n) {
        return n ^ (n >>> 1);
    }

    private static int gray2bin(int n1) {
        int n2 = n1;
        while ((n1 >>>= 1) != 0) {
            n2 ^= n1;
        }
        return n2;
    }

    private record NodeRegionPosition(int regionX, int regionZ, int scale, int scaledY, int heightmapType) { }
}
