package com.mercuriusxeno.goo.entity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Each compression sphere core names its own texture, and that texture stands
 * in the mod's assets, read through the classpath main's resources sit on.
 * large-blast-gathers-its-mined-drops-into-a-green-cored-sphere
 */
class SphereCoreTest {

    @Test
    void maroonNamesTheBlackHoleSphereTexture() {
        assertEquals("goo:textures/entity/compression_sphere.png", SphereCore.MAROON.texture().toString());
        assertTextureStands(SphereCore.MAROON.texture());
    }

    @Test
    void greenNamesTheBlastSphereTexture() {
        assertEquals("goo:textures/entity/compression_sphere_green.png", SphereCore.GREEN.texture().toString());
        assertTextureStands(SphereCore.GREEN.texture());
    }

    private static void assertTextureStands(Identifier texture) {
        String asset = "/assets/" + texture.getNamespace() + "/" + texture.getPath();
        assertNotNull(SphereCoreTest.class.getResource(asset), "No texture stands at " + asset);
    }
}
