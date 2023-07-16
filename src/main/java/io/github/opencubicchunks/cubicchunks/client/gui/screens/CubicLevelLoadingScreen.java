package io.github.opencubicchunks.cubicchunks.client.gui.screens;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Transformation;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.levelgen.placement.UserFunction;
import io.github.opencubicchunks.cubicchunks.server.level.progress.StoringCubeProgressListener;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class CubicLevelLoadingScreen {
    public static final Object2IntMap<ChunkStatus> STATUS_COLORS = Object2IntMaps.unmodifiable(getStatusColors());

    public static final UserFunction STATUS_ALPHAS =
        UserFunction.builder().point(0, 0f)
            .point(ChunkStatus.STRUCTURE_STARTS.getIndex(), 0.15f)
            .point(ChunkStatus.BIOMES.getIndex(), 0.28f)
            .point(ChunkStatus.CARVERS.getIndex(), 0.7f)
            .point(ChunkStatus.INITIALIZE_LIGHT.getIndex(), 0.2f)
            .point(ChunkStatus.FULL.getIndex(), 1).build();

    public static Object2IntMap<ChunkStatus> getStatusColorMap() {
        return Object2IntMaps.unmodifiable(STATUS_COLORS);
    }

    @SuppressWarnings("unused") private static void unusedColors() {
        // TODO update these to be accurate
        //@formatter:off
        /* MINECRAFT:EMPTY: */                  new java.awt.Color(0xFF00FF);
        /* MINECRAFT:STRUCTURE_STARTS: */       new java.awt.Color(0x444444);
        /* MINECRAFT:STRUCTURE_REFERENCES: */   new java.awt.Color(0xFF0000);
        /* MINECRAFT:BIOMES: */                 new java.awt.Color(0xFF9900);
        /* MINECRAFT:NOISE: */                  new java.awt.Color(0xCBFF00);
        /* MINECRAFT:SURFACE: */                new java.awt.Color(0x32FF00);
        /* MINECRAFT:CARVERS: */                new java.awt.Color(0x00FF66);
        /* MINECRAFT:LIQUID_CARVERS: */         new java.awt.Color(0x00FFFF);
        /* MINECRAFT:FEATURES: */               new java.awt.Color(0x0065FF);
        /* MINECRAFT:LIGHT: */                  new java.awt.Color(0x3200FF);
        /* MINECRAFT:SPAWN: */                  new java.awt.Color(0xCC00FF);
        /* MINECRAFT:HEIGHTMAPS: */             new java.awt.Color(0xFF0099);
        /* MINECRAFT:FULL: */                   new java.awt.Color(0xFFFFFF);
        //@formatter:on
    }

    public static Object2IntArrayMap<ChunkStatus> getStatusColors() {
        Object2IntArrayMap<ChunkStatus> map = new Object2IntArrayMap<>();
        List<ChunkStatus> statusList = ChunkStatus.getStatusList();

        map.put(statusList.get(0), 0xFF00FF);
        CubicChunks.LOGGER.debug(statusList.get(0) + ": 0x" + Integer.toHexString(0xFF00FF).toUpperCase(Locale.ROOT));
        map.put(statusList.get(1), 0x444444);
        CubicChunks.LOGGER.debug(statusList.get(1) + ": 0x" + Integer.toHexString(0).toUpperCase(Locale.ROOT));

        for (int i = 2; i < statusList.size() - 1; i++) {
            ChunkStatus chunkStatus = statusList.get(i);
            int v = hsvToRgb((float) (i - 2) / (statusList.size() - 3), 1, 1);
            map.put(chunkStatus, v);
            CubicChunks.LOGGER.debug(chunkStatus + ": 0x" + Integer.toHexString(v).toUpperCase(Locale.ROOT));
        }

        map.put(statusList.get(statusList.size() - 1), 0xFFFFFF);
        CubicChunks.LOGGER.debug(statusList.get(statusList.size() - 1) + ": 0x" + Integer.toHexString(0xFFFFFF).toUpperCase(Locale.ROOT));

        return map;
    }

    public static int hsvToRgb(float hue, float saturation, float value) {

        int h = (int) (hue * 6);
        float f = hue * 6 - h;
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);

        switch (h) {
            case 0:
                return packRgb(value, t, p);
            case 1:
                return packRgb(q, value, p);
            case 2:
                return packRgb(p, value, t);
            case 3:
                return packRgb(p, q, value);
            case 4:
                return packRgb(t, p, value);
            case 5:
                return packRgb(value, p, q);
            default:
                return 0;
        }
    }

    public static int packRgb(float r, float g, float b) {
        return (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }

    public static void doRender(PoseStack stack, StoringChunkProgressListener trackerParam, int xBase, int yBase, int scale, int spacing,
                                Object2IntMap<ChunkStatus> colors) {
        renderColorKey(STATUS_COLORS, stack);
        render3d(trackerParam, xBase, yBase, scale, spacing, STATUS_COLORS);
        // TODO: config option
        // render2d(mStack, trackerParam, xBase, yBase, scale, spacing, colors);
    }

    private static void renderColorKey(Object2IntMap<ChunkStatus> colors, PoseStack poseStack) {
        Font font = Minecraft.getInstance().font;

        int x = 1;
        int y = 1;

        int radius = 11;
        int margin = 3;

        for (ChunkStatus status : ChunkStatus.getStatusList()) {
//            font.draw(poseStack, BuiltInRegistries.CHUNK_STATUS.getKey(status).toString(), x + radius + margin, y + 2, 0xFFFFFFFF);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            renderColorKeySquare(Transformation.identity().getMatrix(), x, y, x + radius, y + radius, 0xFF000000 | colors.getOrDefault(status, 0xFF00FF),
                    (int) (255 * STATUS_ALPHAS.getValue(status.getIndex())));
            y += radius + margin;
        }
    }

    private static void renderColorKeySquare(Matrix4f transform, float x1, float y1, float x2, float y2, int color, int alpha) {
        if (x1 > x2) {
            float i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 > y2) {
            float j = y1;
            y1 = y2;
            y2 = j;
        }

        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
//        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        // Display one triangle with full alpha so it's easier to see
        buffer.vertex(transform, x2, y1, 0.0F).color(red, green, blue, 255).endVertex();
        buffer.vertex(transform, x1, y1, 0.0F).color(red, green, blue, 255).endVertex();
        buffer.vertex(transform, x1, y2, 0.0F).color(red, green, blue, 255).endVertex();
        // Use actual alpha for other triangle
        buffer.vertex(transform, x2, y1, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(transform, x1, y2, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(transform, x2, y2, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.end();

        BufferUploader.draw(buffer.end());
//        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private static void render3d(StoringChunkProgressListener trackerParam, int xBase, int yBase, int scale, int spacing,
                                 Object2IntMap<ChunkStatus> colors) {
        float aspectRatio = Minecraft.getInstance().screen.width / (float) Minecraft.getInstance().screen.height;

        float scaleWithCineSize = scale * CubicConstants.DIAMETER_IN_SECTIONS / 2.0f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
//        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f previousP = RenderSystem.getProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().perspective(60, aspectRatio, 0.01f, -10), VertexSorting.DISTANCE_TO_ORIGIN);

        Matrix4f modelViewMatrix = RenderSystem.getModelViewMatrix();
        Matrix4f previousMV = new Matrix4f(modelViewMatrix);
        modelViewMatrix.identity();
        modelViewMatrix.translate(new Vector3f(0, 0, -20));
        // TODO loading screen rotation
//        modelViewMatrix.multiply(new Quaternionf(30, (float) ((System.currentTimeMillis() * 0.04) % 360), 0, true));

        render3dDrawCubes(trackerParam, xBase, yBase, scaleWithCineSize, spacing, colors);

        RenderSystem.setProjectionMatrix(previousP, VertexSorting.DISTANCE_TO_ORIGIN);
        modelViewMatrix.get(previousMV);

//        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private static void render3dDrawCubes(StoringChunkProgressListener trackerParam, int xBase, int yBase, float scale, int spacing,
                                          Object2IntMap<ChunkStatus> colors) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int sectionRenderRadius = trackerParam.getDiameter();

        EnumSet<Direction> renderFaces = EnumSet.noneOf(Direction.class);

        for (int cdx = 0; cdx < sectionRenderRadius; cdx++) {
            for (int cdz = 0; cdz < sectionRenderRadius; cdz++) {
                ChunkStatus columnStatus = trackerParam.getStatus(cdx, cdz);
                if (columnStatus == null) {
                    continue;
                }
                int alpha = 0xB0;
                int c = colors.getOrDefault(columnStatus, 0xFFFF00FF) | (alpha << 24);
                drawCube(buffer, cdx - sectionRenderRadius / 2, -30, cdz - sectionRenderRadius / 2,
                    0.12f * scale / CubicConstants.DIAMETER_IN_SECTIONS, c, EnumSet.of(Direction.UP));
            }
        }

        final int renderRadius = Coords.sectionToCubeCeil(sectionRenderRadius);

        StoringCubeProgressListener tracker = (StoringCubeProgressListener) trackerParam;
        for (int dx = -1; dx <= renderRadius + 1; dx++) {
            for (int dz = -1; dz <= renderRadius + 1; dz++) {
                for (int dy = -1; dy <= renderRadius + 1; dy++) {
                    ChunkStatus status = tracker.getStatus(dx, dy, dz);
                    if (status == null) {
                        continue;
                    }
                    renderFaces.clear();
                    float ratio = STATUS_ALPHAS.getValue(status.getIndex());
                    int alpha = (int) (0x20 + ratio * (0xFF - 0x20));
                    int c = colors.getOrDefault(status, 0xFFFF00FF) | (alpha << 24);
                    for (Direction value : Direction.values()) {
                        ChunkStatus cubeStatus = tracker.getStatus(dx + value.getStepX(), dy + value.getStepY(), dz + value.getStepZ());
                        if (cubeStatus == null || !cubeStatus.isOrAfter(status)) {
                            renderFaces.add(value);
                        }
                    }
                    drawCube(buffer, dx - renderRadius / 2, dy - renderRadius / 2, dz - renderRadius / 2,
                        0.12f * scale, c, renderFaces);
                }
            }
        }

        Matrix4f m = new Matrix4f(RenderSystem.getModelViewMatrix());
        m.invert();
        Vector4f vec = new Vector4f(0, 0, 0, 1);
        vec.mul(m);

        buffer.setQuadSorting(VertexSorting.byDistance(vec.x, vec.y, vec.z));
        BufferUploader.draw(buffer.end());
    }

    private static void drawCube(BufferBuilder buffer, int x, int y, int z, float scale, int color, EnumSet<Direction> renderFaces) {

        float x0 = x * scale;
        float x1 = x0 + scale;
        float y0 = y * scale;
        float y1 = y0 + scale;
        float z0 = z * scale;
        float z1 = z0 + scale;
        if (renderFaces.contains(Direction.UP)) {
            // up face
            vertex(buffer, x0, y1, z0, 0, 1, 0, color);
            vertex(buffer, x0, y1, z1, 0, 1, 0, color);
            vertex(buffer, x1, y1, z1, 0, 1, 0, color);
            vertex(buffer, x1, y1, z0, 0, 1, 0, color);
        }
        if (renderFaces.contains(Direction.DOWN)) {
            int c = darken(color, 40);
            // down face
            vertex(buffer, x1, y0, z0, 0, -1, 0, c);
            vertex(buffer, x1, y0, z1, 0, -1, 0, c);
            vertex(buffer, x0, y0, z1, 0, -1, 0, c);
            vertex(buffer, x0, y0, z0, 0, -1, 0, c);
        }
        if (renderFaces.contains(Direction.EAST)) {
            int c = darken(color, 30);
            // right face
            vertex(buffer, x1, y1, z0, 1, 0, 0, c);
            vertex(buffer, x1, y1, z1, 1, 0, 0, c);
            vertex(buffer, x1, y0, z1, 1, 0, 0, c);
            vertex(buffer, x1, y0, z0, 1, 0, 0, c);
        }
        if (renderFaces.contains(Direction.WEST)) {
            int c = darken(color, 30);
            // left face
            vertex(buffer, x0, y0, z0, -1, 0, 0, c);
            vertex(buffer, x0, y0, z1, -1, 0, 0, c);
            vertex(buffer, x0, y1, z1, -1, 0, 0, c);
            vertex(buffer, x0, y1, z0, -1, 0, 0, c);
        }
        if (renderFaces.contains(Direction.NORTH)) {
            int c = darken(color, 20);
            // front face (facing camera)
            vertex(buffer, x0, y1, z0, 0, 0, -1, c);
            vertex(buffer, x1, y1, z0, 0, 0, -1, c);
            vertex(buffer, x1, y0, z0, 0, 0, -1, c);
            vertex(buffer, x0, y0, z0, 0, 0, -1, c);
        }
        if (renderFaces.contains(Direction.SOUTH)) {
            int c = darken(color, 20);
            // back face
            vertex(buffer, x0, y0, z1, 0, 0, 1, c);
            vertex(buffer, x1, y0, z1, 0, 0, 1, c);
            vertex(buffer, x1, y1, z1, 0, 0, 1, c);
            vertex(buffer, x0, y1, z1, 0, 0, 1, c);
        }
    }

    private static int darken(int color, int amount) {
        int r = color >>> 16 & 0xFF;
        r -= (r * amount) / 100;
        int g = color >>> 8 & 0xFF;
        g -= (g * amount) / 100;
        int b = color & 0xFF;
        b -= (b * amount) / 100;
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void vertex(BufferBuilder buffer, float x, float y, float z, int nx, int ny, int nz, int color) {
        // color = (color & 0xFF000000) | ((~color) & 0x00FFFFFF);
        float scale = 1f / 255;
        float r = (color >>> 16 & 0xFF) * scale;
        float g = (color >>> 8 & 0xFF) * scale;
        float b = (color & 0xFF) * scale;
        float a = (color >>> 24) * scale;

        buffer.vertex(x, y, z).color(r, g, b, a).endVertex();
    }
}