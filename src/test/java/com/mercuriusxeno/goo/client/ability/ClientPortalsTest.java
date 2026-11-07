package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Portal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * While choosing, the look picks a partner among the chooser's portals in the
 * chosen-from portal's own dimension alone, however squarely a portal in
 * another dimension sits in the cone (decision portals-link-by-looking-through-portal-vision).
 */
class ClientPortalsTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final Portal FROM = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(0, 64, -5)), Direction.SOUTH, Direction.UP);
    /** A portal straight down the look, in another dimension. */
    private static final Portal ELSEWHERE = new Portal(GlobalPos.of(NETHER, new BlockPos(0, 64, 20)), Direction.NORTH, Direction.UP);
    /** A portal just off the look, in the chooser's dimension. */
    private static final Portal BESIDE = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(1, 64, 20)), Direction.NORTH, Direction.UP);
    private static final Vec3 EYE = new Vec3(0.5, 64.5, 0.5);
    private static final Vec3 LOOK = new Vec3(0, 0, 1);

    @Test
    void aPortalInAnotherDimensionIsNeverPicked() {
        assertEquals(Optional.empty(), ClientPortals.pickedOnLook(List.of(FROM, ELSEWHERE), FROM, EYE, LOOK));
    }

    @Test
    void theLookPicksThePortalInTheChoosersDimension() {
        assertEquals(Optional.of(BESIDE), ClientPortals.pickedOnLook(List.of(FROM, ELSEWHERE, BESIDE), FROM, EYE, LOOK));
    }
}
