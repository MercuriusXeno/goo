package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates and caches the radial wheel's hub mask: one anti-aliased white
 * circle per hub radius, spanning the wheel's full diameter so it blits over
 * the wheel's square. Petals draw live from the block atlas rather than from
 * a baked texture (decision petals-render-the-live-fluid).
 */
public final class RadialTextures {
    /**
     * Texture resolution of every mask; the renderer scales it to the wheel's diameter.
     */
    static final int TEX_SIZE = 256;

    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    /**
     * Resolution a mask's radius is keyed at: radii equal to this many parts share one texture.
     */
    private static final double KEY_SCALE = 10_000.0;

    private static final String HUB_LABEL = "goo_radial_hub_";
    private static final String HUB_PATH = "dynamic/radial_hub_";

    private static final Map<String, Identifier> MASKS = new HashMap<>();

    private RadialTextures() {
    }

    /**
     * Returns the mask of a filled circle at the wheel's center, generating it on first use.
     *
     * @param radiusNorm the circle's radius as a fraction of the wheel's
     * @return the registered texture identifier
     */
    public static Identifier getHubTexture(double radiusNorm) {
        String key = Long.toString(Math.round(radiusNorm * KEY_SCALE));
        return MASKS.computeIfAbsent(HUB_PATH + key, path -> register(path, HUB_LABEL + key,
                PetalMask.fill(TEX_SIZE, (x, y) -> Math.hypot(x, y) <= radiusNorm, OPAQUE_WHITE)));
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
}
