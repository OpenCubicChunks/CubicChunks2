package io.github.opencubicchunks.cubicchunks.network;

import static io.github.opencubicchunks.cc_core.utils.Coords.cubeToSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.MathUtil;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class PacketCubes {
    // vanilla has max chunk size of 2MB, it works out to be 128kB for a 32^3 cube
    private static final int MAX_CUBE_SIZE = (CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS) * 128 * 1024;

    private final CubePos[] cubePositions;
    private final LevelCube[] cubes;
    private final BitSet cubeExists;
    private final byte[] cubeData;
    private final List<CubeBlockEntityInfo> blockEntities;

    public PacketCubes(List<LevelCube> cubes) {
        this.cubes = cubes.toArray(new LevelCube[0]);
        this.cubePositions = new CubePos[this.cubes.length];
        this.cubeExists = new BitSet(cubes.size());
        this.cubeData = new byte[calculateDataSize(cubes)];
        fillDataBuffer(wrapBuffer(this.cubeData), cubes, cubeExists);
        this.blockEntities = cubes.stream()
            .flatMap(cube -> cube.getTileEntityMap().values().stream())
            .map(CubeBlockEntityInfo::of)
            .collect(Collectors.toList());
    }

    PacketCubes(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.cubes = new LevelCube[count];
        this.cubePositions = new CubePos[count];
        for (int i = 0; i < count; i++) {
            cubePositions[i] = CubePos.of(buf.readInt(), buf.readInt(), buf.readInt());
        }

        // one long stores information about 64 chunks
        int length = MathUtil.ceilDiv(cubes.length, 64);
        this.cubeExists = BitSet.valueOf(buf.readLongArray(new long[length]));

        int packetLength = buf.readVarInt();
        if (packetLength > MAX_CUBE_SIZE * cubes.length) {
            throw new RuntimeException("Cubes Packet trying to allocate too much memory on read: " +
                packetLength + " bytes for " + cubes.length + " cubes");
        }
        this.cubeData = new byte[packetLength];
        buf.readBytes(this.cubeData);
        int teTagCount = buf.readVarInt();
        this.blockEntities = new ArrayList<>(teTagCount);
        for (int i = 0; i < teTagCount; i++) {
            this.blockEntities.add(CubeBlockEntityInfo.read(buf));
        }
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(cubes.length);
        for (LevelCube cube : cubes) {
            buf.writeInt(cube.getCubePos().getX());
            buf.writeInt(cube.getCubePos().getY());
            buf.writeInt(cube.getCubePos().getZ());
        }

        buf.writeLongArray(cubeExists.toLongArray());

        buf.writeVarInt(this.cubeData.length);
        buf.writeBytes(this.cubeData);
        buf.writeVarInt(this.blockEntities.size());

        for (var blockEntity : this.blockEntities) {
            blockEntity.write(buf);
        }
    }

    private static void fillDataBuffer(FriendlyByteBuf buf, List<LevelCube> cubes, BitSet existingChunks) {
        buf.writerIndex(0);
        int i = 0;
        for (LevelCube cube : cubes) {
            if (!cube.isEmptyCube()) {
                existingChunks.set(i);
                cube.write(buf);
            }
            i++;
        }
    }

    private static FriendlyByteBuf wrapBuffer(byte[] packetData) {
        ByteBuf bytebuf = Unpooled.wrappedBuffer(packetData);
        return new FriendlyByteBuf(bytebuf);
    }

    private static int calculateDataSize(List<LevelCube> cubes) {
        return cubes.stream()
            .filter(c -> !c.isEmptyCube())
            .flatMap(c -> Arrays.stream(c.getCubeSections()))
            .mapToInt(LevelChunkSection::getSerializedSize)
            .sum();
    }

    public static class Handler {

        public static void handle(PacketCubes packet, Level level) {
            ClientLevel clientLevel = (ClientLevel) level;

            FriendlyByteBuf dataReader = wrapBuffer(packet.cubeData);
            BitSet cubeExists = packet.cubeExists;
            for (int i = 0; i < packet.cubes.length; i++) {
                CubePos pos = packet.cubePositions[i];
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();

                ((ClientCubeCache) clientLevel.getChunkSource()).replaceWithPacketData(
                    x, y, z,
                    dataReader,
                    new CompoundTag(),
                    (blockEntityLoader, cubePos) -> {
                        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();

                        for (CubeBlockEntityInfo blockEntity : packet.blockEntities) {
                            if (blockEntity.isInCube(cubePos.getX(), cubePos.getY(), cubePos.getZ())) {
                                posMutable.set(
                                    Coords.blockToLocal(blockEntity.posX()),
                                    Coords.blockToLocal(blockEntity.posY()),
                                    Coords.blockToLocal(blockEntity.posZ())
                                );

                                blockEntityLoader.accept(
                                    posMutable,
                                    blockEntity.type(),
                                    blockEntity.tag()
                                );
                            }
                        }
                    },
                    cubeExists.get(i)
                );

                for (int dx = 0; dx < CubeAccess.DIAMETER_IN_SECTIONS; dx++) {
                    for (int dy = 0; dy < CubeAccess.DIAMETER_IN_SECTIONS; dy++) {
                        for (int dz = 0; dz < CubeAccess.DIAMETER_IN_SECTIONS; dz++) {
                            clientLevel.setSectionDirtyWithNeighbors(
                                cubeToSection(x, dx),
                                cubeToSection(y, dy),
                                cubeToSection(z, dz));
                        }
                    }
                }
            }
        }
    }

    private static record CubeBlockEntityInfo(int posX, int posY, int posZ, BlockEntityType<?> type, @Nullable CompoundTag tag) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(posX);
            buf.writeVarInt(posY);
            buf.writeVarInt(posZ);
            buf.writeVarInt(Registry.BLOCK_ENTITY_TYPE.getId(type));
            buf.writeNbt(tag);
        }

        public boolean isInCube(int cubeX, int cubeY, int cubeZ) {
            return Coords.blockToCube(this.posX) == cubeX
                && Coords.blockToCube(this.posY) == cubeY
                && Coords.blockToCube(this.posZ) == cubeZ;
        }

        public static CubeBlockEntityInfo read(FriendlyByteBuf buf) {
            int posX = buf.readVarInt();
            int posY = buf.readVarInt();
            int posZ = buf.readVarInt();
            BlockEntityType<?> type = Registry.BLOCK_ENTITY_TYPE.byId(buf.readVarInt());
            CompoundTag tag = buf.readNbt();
            return new CubeBlockEntityInfo(posX, posY, posZ, type, tag);
        }

        public static CubeBlockEntityInfo of(BlockEntity blockEntity) {
            CompoundTag tag = new CompoundTag();
            BlockPos pos = blockEntity.getBlockPos();

            return new CubeBlockEntityInfo(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                blockEntity.getType(),
                tag.isEmpty() ? null : tag
            );
        }
    }
}
