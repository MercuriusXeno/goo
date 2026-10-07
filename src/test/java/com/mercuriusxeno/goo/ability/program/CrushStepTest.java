package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Crush's step breaks from the struck block inward along the strike to its
 * depth, passing air and stopping at a solid block outside its tag, and
 * shoves the living things around the landing along the strike
 * (decision crush-blob-breaks-along-its-strike).
 */
class CrushStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "bore_breakable"));
    private static final int DEPTH = 3;
    private static final double RADIUS = 1.5;
    private static final double PUSH = 0.8;
    /** The cell the blob lands in, west of the struck block. */
    private static final BlockPos LANDING = new BlockPos(1, 1, 2);
    private static final BlockPos STRUCK = LANDING.east();

    /**
     * A landing on the west face of STRUCK, the given blocks breakable and
     * solid, every other block air.
     */
    private static PlacedFaceHost landingWith(Set<BlockPos> breakable, Set<BlockPos> solid) {
        PlacedFaceHost host = mock(PlacedFaceHost.class,
                withSettings().extraInterfaces(BlockBreakHost.class, EntityScanHost.class));
        when(host.kind()).thenReturn(HostKind.LANDING);
        when(host.position()).thenReturn(LANDING);
        when(host.placedFace()).thenReturn(Direction.WEST);
        BlockBreakHost blocks = (BlockBreakHost) host;
        when(blocks.blockIn(any(), any())).thenAnswer(call -> breakable.contains(call.<BlockPos>getArgument(0)));
        when(blocks.airAt(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return !breakable.contains(pos) && !solid.contains(pos);
        });
        return host;
    }

    private static void land(PlacedFaceHost host) {
        ProgramBehavior.forHost(List.of(new CrushStep(BREAKABLE, DEPTH, RADIUS, PUSH)), HostKind.LANDING)
                .tick(host);
    }

    @Test
    void breaksFromTheStruckBlockToTheDepth() {
        PlacedFaceHost host = landingWith(
                Set.of(STRUCK, STRUCK.east(1), STRUCK.east(2), STRUCK.east(DEPTH)), Set.of());

        land(host);

        BlockBreakHost blocks = (BlockBreakHost) host;
        verify(blocks).breakBlock(STRUCK);
        verify(blocks).breakBlock(STRUCK.east(1));
        verify(blocks).breakBlock(STRUCK.east(2));
        verify(blocks, never()).breakBlock(STRUCK.east(DEPTH));
    }

    @Test
    void passesAirAndStopsAtASolidBlockOutsideTheTag() {
        PlacedFaceHost host = landingWith(Set.of(STRUCK, STRUCK.east(2)), Set.of());
        PlacedFaceHost blocked = landingWith(Set.of(STRUCK.east(2)), Set.of(STRUCK.east(1)));

        land(host);
        land(blocked);

        verify((BlockBreakHost) host).breakBlock(STRUCK.east(2));
        verify((BlockBreakHost) blocked, never()).breakBlock(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shovesLivingThingsAroundTheLandingAlongTheStrike() {
        PlacedFaceHost host = landingWith(Set.of(), Set.of());
        ArgumentCaptor<Consumer<TargetHost>> body = ArgumentCaptor.forClass(Consumer.class);
        TargetHost zombie = mock(TargetHost.class);

        land(host);
        verify((EntityScanHost) host).forEachEntityWithin(eq(SelectionShape.SPHERE), anyDouble(),
                eq(Set.of(EntityFilter.LIVING)), body.capture());
        body.getValue().accept(zombie);

        verify(zombie).push(new Vec3(PUSH, 0, 0));
    }
}
