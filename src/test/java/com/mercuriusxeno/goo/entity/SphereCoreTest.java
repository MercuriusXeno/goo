package com.mercuriusxeno.goo.entity;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each compression sphere core names its own texture, standing in the mod's
 * assets: the black hole's jet black, the Blast's neon green as 26.1 shipped
 * it; a sphere saved under the black hole's old core name loads as its core.
 * Reads the textures through the classpath main's resources sit on.
 * large-blast-gathers-its-mined-drops-into-a-green-cored-sphere
 * black-hole-item-ball-draws-jet-black
 */
class SphereCoreTest {

    /** The brightest channel a jet black core's pixel may carry. */
    private static final int NEAR_BLACK = 0x30;
    private static final int ALPHA_SHIFT = 24;
    private static final int CHANNEL = 0xFF;
    /** compression_sphere_green.png's SHA-256 as 26.1 ships it. */
    private static final String GREEN_ON_26_1 = "6ee20f15d9721aa02ed8b61dbc3a2e61651f72b6fa6603a871b6f8a6dbdf3da6";

    @Test
    void blackNamesTheBlackHoleSphereTexture() {
        assertEquals("goo:textures/entity/compression_sphere.png", SphereCore.BLACK.texture().toString());
        assertNotNull(SphereCoreTest.class.getResource(asset(SphereCore.BLACK.texture())));
    }

    @Test
    void greenNamesTheBlastSphereTexture() {
        assertEquals("goo:textures/entity/compression_sphere_green.png", SphereCore.GREEN.texture().toString());
        assertNotNull(SphereCoreTest.class.getResource(asset(SphereCore.GREEN.texture())));
    }

    @Test
    void theBlackHolesCoreDrawsJetBlack() throws IOException {
        BufferedImage core = image(SphereCore.BLACK.texture());
        int opaque = 0;
        for (int y = 0; y < core.getHeight(); y++) {
            for (int x = 0; x < core.getWidth(); x++) {
                int argb = core.getRGB(x, y);
                if (argb >>> ALPHA_SHIFT == 0) {
                    continue;
                }
                opaque++;
                int red = argb >> 16 & CHANNEL;
                int green = argb >> 8 & CHANNEL;
                int blue = argb & CHANNEL;
                String at = "pixel " + x + "," + y + " reads " + Integer.toHexString(argb);
                assertFalse(red > green && red > blue, at + ", red-dominant");
                assertTrue(Math.max(red, Math.max(green, blue)) <= NEAR_BLACK, at + ", short of black");
            }
        }
        assertTrue(opaque > 0, "the core draws no pixel");
    }

    @Test
    void theBlastsCoreIsTheGreen26_1Shipped() throws IOException, NoSuchAlgorithmException {
        try (InputStream in = stream(SphereCore.GREEN.texture())) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            assertEquals(GREEN_ON_26_1, HexFormat.of().formatHex(digest));
        }
    }

    /** A sphere saved before the rename names its core "maroon" and loads as the black hole's. */
    @Test
    void aSavedMaroonCoreLoadsAsTheBlackHoles() {
        assertEquals(Optional.of(SphereCore.BLACK), saved("maroon"));
        assertEquals(Optional.of(SphereCore.GREEN), saved("green"));
    }

    private static Optional<SphereCore> saved(String name) {
        return SphereCore.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(name)).result();
    }

    private static BufferedImage image(Identifier texture) throws IOException {
        try (InputStream in = stream(texture)) {
            return ImageIO.read(in);
        }
    }

    private static InputStream stream(Identifier texture) {
        InputStream in = SphereCoreTest.class.getResourceAsStream(asset(texture));
        assertNotNull(in, "No texture stands at " + asset(texture));
        return in;
    }

    private static String asset(Identifier texture) {
        return "/assets/" + texture.getNamespace() + "/" + texture.getPath();
    }
}
