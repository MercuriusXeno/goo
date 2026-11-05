package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A pixel-art cursor the manager shows, drawn from one of the mod's own images and built once into a GLFW cursor, each
 * image pixel a SCALE-wide square so the pixels stay crisp. CursorType's constructor is opened by
 * META-INF/accesstransformer.cfg.
 * Decision shift-over-a-link-shows-a-pixel-pointer-hand.
 * Decision ctrl-during-a-rail-drag-shows-a-green-plus-and-drops-a-copy.
 */
final class PixelCursor {
    private static final int SCALE = 2;
    private static final int CHANNELS = 4;
    private static final int BYTE_MASK = 0xFF;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int ALPHA_SHIFT = 24;
    private static final String LOAD_FAILED = "The cursor {} did not load; the cursor stays the default";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String texturePath;
    private final int hotX;
    private final int hotY;
    private final String name;
    private @Nullable CursorType built;

    /**
     * @param texturePath the image's path inside the mod's assets
     * @param hotX        the hot spot's column in the image
     * @param hotY        the hot spot's row in the image
     * @param name        the cursor's name
     */
    PixelCursor(String texturePath, int hotX, int hotY, String name) {
        this.texturePath = texturePath;
        this.hotX = hotX;
        this.hotY = hotY;
        this.name = name;
    }

    /**
     * @return the cursor, built on the first call; the default cursor where the image or GLFW fails it
     */
    CursorType cursor() {
        if (built == null) {
            built = build();
        }
        return built;
    }

    private CursorType build() {
        Identifier image = Identifier.fromNamespaceAndPath(Goo.MODID, texturePath);
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(image);
             NativeImage drawn = NativeImage.read(in)) {
            return wrap(drawn);
        } catch (IOException failed) {
            LOGGER.warn(LOAD_FAILED, image, failed);
            return CursorType.DEFAULT;
        }
    }

    private CursorType wrap(NativeImage drawn) {
        int width = drawn.getWidth() * SCALE;
        int height = drawn.getHeight() * SCALE;
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * CHANNELS);
        GLFWImage glfwImage = GLFWImage.malloc();
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    putRgba(pixels, drawn.getPixel(x / SCALE, y / SCALE));
                }
            }
            pixels.flip();
            glfwImage.set(width, height, pixels);
            long handle = GLFW.glfwCreateCursor(glfwImage, hotX * SCALE, hotY * SCALE);
            return handle == 0L ? CursorType.DEFAULT : new CursorType(name, handle);
        } finally {
            glfwImage.free();
            MemoryUtil.memFree(pixels);
        }
    }

    // GLFW reads each pixel as red, green, blue and alpha bytes; the image answers ARGB.
    private static void putRgba(ByteBuffer pixels, int argb) {
        pixels.put((byte) (argb >> RED_SHIFT & BYTE_MASK));
        pixels.put((byte) (argb >> GREEN_SHIFT & BYTE_MASK));
        pixels.put((byte) (argb & BYTE_MASK));
        pixels.put((byte) (argb >>> ALPHA_SHIFT));
    }
}
