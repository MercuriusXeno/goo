package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A strike in flight re-aims at its entity's body center as the entity
 * moves, keeping its entity and its age, so Urchin's spear stands where
 * the mob stands when the damage lands (decision urchin-rests-bristled-and-spears).
 */
class FieldStrikeTest {

    @Test
    void reaimMovesThePointAndKeepsEntityAndAge() {
        FieldStrike chosen = new FieldStrike(7, 1f, 2f, 3f, 4);

        FieldStrike reaimed = chosen.reaimedAt(new Vec3(2.5, 2.0, 4.25));

        assertEquals(new FieldStrike(7, 2.5f, 2f, 4.25f, 4), reaimed);
    }

    @Test
    void agingKeepsTheReaimedPoint() {
        FieldStrike reaimed = new FieldStrike(7, 0f, 0f, 0f, 2).reaimedAt(new Vec3(1, 1, 1));

        assertEquals(new FieldStrike(7, 1f, 1f, 1f, 3), reaimed.aged());
    }
}
