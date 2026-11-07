package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A portal on a wall opens down from the struck block, so one thrown level
 * with the eye opens before the caster rather than above; it opens up from
 * the struck block only where there is no room below
 * (decision portals-link-by-looking-through-portal-vision).
 */
class PortalTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final BlockPos STRUCK = new BlockPos(0, 65, 0);
    private static final GlobalPos AT = GlobalPos.of(OVERWORLD, STRUCK);

    @Test
    void aWallPortalOpensDownFromTheStruckBlock() {
        Optional<Portal> frame = Portal.frameAt(pos -> true, pos -> true, AT, Direction.SOUTH, Direction.NORTH);
        assertEquals(Optional.of(new Portal(GlobalPos.of(OVERWORLD, STRUCK.below()), Direction.SOUTH, Direction.UP)),
                frame);
    }

    @Test
    void aWallPortalOpensUpWhereThereIsNoRoomBelow() {
        Set<BlockPos> solid = Set.of(STRUCK, STRUCK.above());
        Optional<Portal> frame = Portal.frameAt(solid::contains, pos -> true, AT, Direction.SOUTH, Direction.NORTH);
        assertEquals(Optional.of(new Portal(AT, Direction.SOUTH, Direction.UP)), frame);
    }
}
