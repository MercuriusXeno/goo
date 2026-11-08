package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.TransformationPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The landed Slime blob morphs into its slime where it splatted, with no hop
 * up and down first (decision slime-blob-morphs-into-the-slime-it-becomes).
 */
class SpawnSlimeStepTest {

    @Test
    void theBlobMorphsWhereItSplattedWithNoHop() {
        Vec3 splat = new Vec3(4.5, 64.0, -2.5);

        TransformationPayload morph = SpawnSlimeStep.morphOf(GooTypes.WEIRD, splat, 4242);

        assertEquals(splat, morph.from());
        assertEquals(splat, morph.to());
    }
}
