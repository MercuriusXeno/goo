package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.TransformationPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The blob morphs into the slime where the slime is born, at its feet in the
 * open cell before the struck face, both ends of the morph there, so it hops
 * not and never stands inside the struck block, lit as its dark inside
 * (decision slime-blob-morphs-into-the-slime-it-becomes).
 */
class SpawnSlimeStepTest {

    @Test
    void theBlobMorphsWhereTheSlimeIsBornWithNoHop() {
        BlockPos cell = new BlockPos(4, 64, -3);

        TransformationPayload morph = SpawnSlimeStep.morphOf(GooTypes.WEIRD, cell, 4242);

        assertEquals(Vec3.atBottomCenterOf(cell), morph.from(), "The morph should start at the slime's feet");
        assertEquals(Vec3.atBottomCenterOf(cell), morph.to(), "The morph should end at the slime's feet");
    }
}
