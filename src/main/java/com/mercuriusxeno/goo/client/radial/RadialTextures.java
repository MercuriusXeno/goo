package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Generates and caches anti-aliased mask textures for the radial wheel:
 * one DynamicTexture per wedge (a type wedge in either ring, or an ability
 * wedge of a fan), shaped as a {@link PetalMask} and filled with its goo's
 * fluid sprite, and one white mask per hub circle. A mask spans the wheel's
 * full diameter, so every mask blits over the same square.
 */
public final class RadialTextures {
    /**
     * Texture resolution of every mask; the renderer scales it to the wheel's diameter.
     */
    static final int TEX_SIZE = 256;

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    /**
     * Resolution a mask's angles and radii are keyed at: parameters equal
     * to this many parts share one texture.
     */
    private static final double KEY_SCALE = 10_000.0;

    private static final String ARC_LABEL = "goo_radial_arc_";
    private static final String ARC_PATH = "dynamic/radial_arc_";
    private static final String HUB_LABEL = "goo_radial_hub_";
    private static final String HUB_PATH = "dynamic/radial_hub_";
    private static final String KEY_SEPARATOR = "_";
    private static final String TEXTURE_PREFIX = "textures/";
    private static final String TEXTURE_SUFFIX = ".png";
    private static final String LOG_SPRITE_MISSING = "Radial wedge sprite {} has no texture at {}; the wedge fills white";
    private static final String LOG_SPRITE_UNREADABLE = "Radial wedge sprite {} failed to read; the wedge fills white";

    private static final Map<String, Identifier> MASKS = new HashMap<>();
    private static final Map<Identifier, PetalMask.PixelSource> SPRITES = new HashMap<>();

    private RadialTextures() {
    }

    /**
     * Returns the mask of a wedge's petal filled with a fluid sprite,
     * generating it on first use.
     *
     * @param startAngle the arc's start, clockwise from the top, in radians
     * @param arc        the arc's span in radians
     * @param innerNorm  the band's inner radius as a fraction of the wheel's
     * @param outerNorm  the band's outer radius as a fraction of the wheel's
     * @param sprite     the still fluid sprite id on the block atlas
     * @param tint       the ARGB tint the sprite renders under
     * @return the registered texture identifier
     */
    public static Identifier getArcTexture(double startAngle, double arc, double innerNorm, double outerNorm,
                                           Identifier sprite, int tint) {
        double start = wrap(startAngle);
        String key = keyOf(start) + KEY_SEPARATOR + keyOf(arc) + KEY_SEPARATOR
                + keyOf(innerNorm) + KEY_SEPARATOR + keyOf(outerNorm) + KEY_SEPARATOR
                + sprite.getNamespace() + KEY_SEPARATOR + sprite.getPath().replace('/', '_')
                + KEY_SEPARATOR + Integer.toHexString(tint);
        return MASKS.computeIfAbsent(ARC_PATH + key, path -> register(path, ARC_LABEL + key,
                PetalMask.fill(TEX_SIZE, (x, y) -> PetalMask.isInsidePetal(x, y, start, arc, innerNorm, outerNorm),
                        spritePixels(sprite), tint)));
    }

    /**
     * Returns the mask of a filled circle at the wheel's center, generating it on first use.
     *
     * @param radiusNorm the circle's radius as a fraction of the wheel's
     * @return the registered texture identifier
     */
    public static Identifier getHubTexture(double radiusNorm) {
        String key = keyOf(radiusNorm);
        return MASKS.computeIfAbsent(HUB_PATH + key, path -> register(path, HUB_LABEL + key,
                PetalMask.fill(TEX_SIZE, (x, y) -> Math.hypot(x, y) <= radiusNorm,
                        PetalMask.PixelSource.solid(OPAQUE_WHITE), OPAQUE_WHITE)));
    }

    private static String keyOf(double value) {
        return Long.toString(Math.round(value * KEY_SCALE));
    }

    private static double wrap(double angle) {
        double wrapped = angle % TWO_PI;
        return wrapped < 0 ? wrapped + TWO_PI : wrapped;
    }

    private static Identifier register(String path, String label, int[] pixels) {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        for (int py = 0; py < TEX_SIZE; py++) {
            for (int px = 0; px < TEX_SIZE; px++) {
                image.setPixel(px, py, pixels[py * TEX_SIZE + px]);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(Goo.MODID, path);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> label, image));
        return id;
    }

    private static PetalMask.PixelSource spritePixels(Identifier sprite) {
        return SPRITES.computeIfAbsent(sprite, RadialTextures::readFirstFrame);
    }

    /**
     * Reads a sprite's first animation frame, the square at the top of its
     * texture, falling back to white when the texture does not read.
     *
     * @param sprite the sprite id on the block atlas
     * @return the frame's pixels
     */
    private static PetalMask.PixelSource readFirstFrame(Identifier sprite) {
        Identifier file = Identifier.fromNamespaceAndPath(sprite.getNamespace(),
                TEXTURE_PREFIX + sprite.getPath() + TEXTURE_SUFFIX);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            Goo.LOGGER.warn(LOG_SPRITE_MISSING, sprite, file);
            return PetalMask.PixelSource.solid(OPAQUE_WHITE);
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            return copyFirstFrame(image);
        } catch (IOException e) {
            Goo.LOGGER.warn(LOG_SPRITE_UNREADABLE, sprite, e);
            return PetalMask.PixelSource.solid(OPAQUE_WHITE);
        }
    }

    private static PetalMask.PixelSource copyFirstFrame(NativeImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        int[] pixels = new int[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                pixels[y * side + x] = image.getPixel(x, y);
            }
        }
        return new FramePixels(side, pixels);
    }

    /** A square frame's pixels, copied off its image so the image can close. */
    private record FramePixels(int side, int[] pixels) implements PetalMask.PixelSource {
        @Override
        public int width() {
            return side;
        }

        @Override
        public int height() {
            return side;
        }

        @Override
        public int pixel(int x, int y) {
            return pixels[y * side + x];
        }
    }
}
