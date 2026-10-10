package com.mercuriusxeno.goo.ability.frost;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iceborn's record of what it froze: a block holds while its Iceborn player
 * stands within range, goes back once they leave or are gone, and the record
 * survives being saved and read back.
 */
class IcebornFrozenBlocksTest {

    private static final BlockPos POOL = new BlockPos(10, 64, 10);
    private static final UUID PLAYER = new UUID(7L, 11L);

    @Test
    void aBlockHoldsWhileItsPlayerStandsInRange() {
        assertTrue(IcebornFrozenBlocks.holds(new Vec3(12.5, 64.5, 10.5), POOL));
    }

    @Test
    void aBlockGoesBackOnceItsPlayerLeavesRange() {
        double away = IcebornFrozenBlocks.HOLDS_WITHIN + 1;
        assertFalse(IcebornFrozenBlocks.holds(new Vec3(10.5 + away, 64.5, 10.5), POOL));
    }

    @Test
    void aBlockGoesBackOnceItsPlayerIsGoneOrNoLongerIceborn() {
        assertFalse(IcebornFrozenBlocks.holds(null, POOL));
    }

    @Test
    void theRecordReadsBackWhatItSaved() {
        IcebornFrozenBlocks saved = new IcebornFrozenBlocks(List.of(
                new IcebornFrozenBlocks.Frozen(POOL, false, PLAYER),
                new IcebornFrozenBlocks.Frozen(POOL.east(), true, PLAYER)));
        var json = IcebornFrozenBlocks.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
        IcebornFrozenBlocks read = IcebornFrozenBlocks.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(saved.frozen(), read.frozen());
    }
}
