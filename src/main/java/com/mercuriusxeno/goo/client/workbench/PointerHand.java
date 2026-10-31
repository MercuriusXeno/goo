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
 * The little pixel-art pointer hand the manager shows while shift is held over a link, drawn from the mod's own image
 * and built once into a GLFW cursor, each image pixel a SCALE-wide square so the pixels stay crisp; the hot spot is the
 * fingertip. CursorType's constructor is opened by META-INF/accesstransformer.cfg.
 * Decision shift-over-a-link-shows-a-pixel-pointer-hand.
 */
final class PointerHand {
    /** The image's path inside the mod's assets. */
    static final String TEXTURE_PATH = "textures/gui/cursor/pointer_hand.png";
    /** The fingertip's column in the image. */
    static final int HOT_X = 5;
    /** The fingertip's row in the image. */
    static final int HOT_Y = 0;
    private static final int SCALE = 2;
    private static final int CHANNELS = 4;
    private static final int BYTE_MASK = 0xFF;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int ALPHA_SHIFT = 24;
    private static final String NAME = "goo_pointer_hand";
    private static final String LOAD_FAILED = "The pointer hand {} did not load; the cursor stays the default";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable CursorType built;

    private PointerHand() {
    }

    /**
     * @return the hand, built on the first call; the default cursor where the image or GLFW fails it
     */
    static CursorType cursor() {
        if (built == null) {
            built = build();
        }
        return built;
    }

    private static CursorType build() {
        Identifier image = Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH);
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(image);
             NativeImage hand = NativeImage.read(in)) {
            return wrap(hand);
        } catch (IOException failed) {
            LOGGER.warn(LOAD_FAILED, image, failed);
            return CursorType.DEFAULT;
        }
    }

    private static CursorType wrap(NativeImage hand) {
        int width = hand.getWidth() * SCALE;
        int height = hand.getHeight() * SCALE;
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * CHANNELS);
        GLFWImage glfwImage = GLFWImage.malloc();
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    putRgba(pixels, hand.getPixel(x / SCALE, y / SCALE));
                }
            }
            pixels.flip();
            glfwImage.set(width, height, pixels);
            long handle = GLFW.glfwCreateCursor(glfwImage, HOT_X * SCALE, HOT_Y * SCALE);
            return handle == 0L ? CursorType.DEFAULT : new CursorType(NAME, handle);
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
