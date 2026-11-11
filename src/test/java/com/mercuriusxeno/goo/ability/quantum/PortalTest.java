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

    /** A wall portal standing on the two blocks below the struck one's. */
    private static final Set<BlockPos> STANDING_BELOW = Set.of(STRUCK.below(), STRUCK.below(2));

    /**
     * A wall portal thrown at the block above one already standing opens up
     * from the struck block rather than down over the standing one's top,
     * which the two would share and draw fighting over, as the operator found
     * (decision portals-link-by-looking-through-portal-vision).
     */
    @Test
    void aWallPortalThrownAboveAStandingOneOpensUpClearOfIt() {
        Optional<Portal> frame = Portal.frameAt(pos -> true, pos -> true, STANDING_BELOW::contains, AT,
                Direction.SOUTH, Direction.NORTH);
        assertEquals(Optional.of(new Portal(AT, Direction.SOUTH, Direction.UP)), frame);
    }

    /** A wall portal thrown at a standing one's top block finds no frame clear of it, and opens none. */
    @Test
    void aWallPortalThrownAtAStandingOnesTopOpensNone() {
        Optional<Portal> frame = Portal.frameAt(pos -> true, pos -> true, STANDING_BELOW::contains,
                GlobalPos.of(OVERWORLD, STRUCK.below()), Direction.SOUTH, Direction.NORTH);
        assertEquals(Optional.empty(), frame);
    }

    /** A floor portal thrown along a standing floor portal's end finds the frame beyond it. */
    @Test
    void aFloorPortalThrownAtAStandingOnesEndOpensBeyondIt() {
        Set<BlockPos> standing = Set.of(STRUCK.south(), STRUCK.south(2));
        Optional<Portal> frame = Portal.frameAt(pos -> true, pos -> true, standing::contains, AT, Direction.UP,
                Direction.SOUTH);
        assertEquals(Optional.of(new Portal(GlobalPos.of(OVERWORLD, STRUCK.north()), Direction.UP, Direction.SOUTH)),
                frame);
    }

    @Test
    void aFloorPortalThrownNorthOrSouthHasNorthUp() {
        for (Direction facing : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
            Portal frame = Portal.frameAt(pos -> true, pos -> true, AT, Direction.UP, facing).orElseThrow();
            assertEquals(Direction.NORTH.getUnitVec3(), frame.gate().up(), "A floor portal thrown facing " + facing
                    + " should have north up");
        }
    }

    @Test
    void aCeilingPortalThrownEastOrWestHasWestUp() {
        for (Direction facing : new Direction[] {Direction.EAST, Direction.WEST}) {
            Portal frame = Portal.frameAt(pos -> true, pos -> true, AT, Direction.DOWN, facing).orElseThrow();
            assertEquals(Direction.WEST.getUnitVec3(), frame.gate().up(), "A ceiling portal thrown facing " + facing
                    + " should have west up");
        }
    }
}
