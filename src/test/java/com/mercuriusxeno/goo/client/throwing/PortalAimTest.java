package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.ArmPose;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A right click with Portal in the glove: sneaking on a portal starts
 * choosing its partner, and sneaking on another while choosing links the
 * two; while choosing, right clicking anywhere else, or the chosen portal
 * again, cancels; standing, a right click places a portal
 * (decision portals-link-by-looking-through-portal-vision).
 */
class PortalAimTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final Portal HERE = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(0, 64, 0)), Direction.SOUTH,
            Direction.UP);
    private static final Portal THERE = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(9, 64, 0)), Direction.SOUTH,
            Direction.UP);

    @Test
    void sneakingOnAPortalStartsChoosingIt() {
        assertEquals(new PortalAim.Choose(HERE), PortalAim.pressed(Optional.empty(), true, Optional.of(HERE)));
    }

    @Test
    void sneakingOnAnotherWhileChoosingLinksTheTwo() {
        assertEquals(new PortalAim.Link(THERE), PortalAim.pressed(Optional.of(HERE), true, Optional.of(THERE)));
    }

    @Test
    void rightClickingAnywhereElseWhileChoosingCancels() {
        assertEquals(new PortalAim.Cancel(), PortalAim.pressed(Optional.of(HERE), true, Optional.empty()));
        assertEquals(new PortalAim.Cancel(), PortalAim.pressed(Optional.of(HERE), false, Optional.empty()));
    }

    @Test
    void rightClickingTheChosenPortalAgainCancels() {
        assertEquals(new PortalAim.Cancel(), PortalAim.pressed(Optional.of(HERE), true, Optional.of(HERE)));
    }

    @Test
    void standingARightClickPlacesAPortal() {
        assertEquals(new PortalAim.Place(), PortalAim.pressed(Optional.empty(), false, Optional.of(HERE)));
        assertEquals(new PortalAim.Place(), PortalAim.pressed(Optional.empty(), true, Optional.empty()));
    }

    /**
     * Only a Place press throws a blob, so only it swings the hand on release
     * (decision diagnose-and-correct-hand-swings-outside-throws).
     */
    @Nested
    class ReleaseSwings {

        static Stream<PortalAim.Press> pressesThrowingNoBlob() {
            return Stream.of(new PortalAim.Choose(HERE), new PortalAim.Link(THERE), new PortalAim.Cancel());
        }

        @ParameterizedTest
        @MethodSource("pressesThrowingNoBlob")
        void aPressThrowingNoBlobPlaysNoSwingWithPortalsSwungPose(PortalAim.Press press) {
            assertFalse(PortalAim.swingsOnRelease(press, ArmPose.SWUNG), press.toString());
        }

        @Test
        void aPlacePressThrowingABlobSwingsWithPortalsSwungPose() {
            assertTrue(PortalAim.swingsOnRelease(new PortalAim.Place(), ArmPose.SWUNG));
        }

        @Test
        void portalResolvesTheSwungPose() {
            assertEquals(ArmPose.SWUNG, AbilityJson.decode("quantum_portal").pose());
        }
    }
}
