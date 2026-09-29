package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChainBurnouts holds each burnout from the game time it began, resolves its
 * visual by goo type, and drops it once that type's explosion has run.
 */
class ChainBurnoutsTest {

    private static final BlockPos POS = new BlockPos(3, 64, -7);
    private static final long START = 48_200L;
    private static final String ABILITY = "goo:test_ability";

    static final java.util.List<ResourceKey<GooTypeDefinition>> CHAIN_TYPES = BurnoutVisuals.CHAIN_TYPES;
    private static final String TUNNEL_ABILITY = "goo:rock_tunnel";
    /** Plays every ability's burnout. */
    private static final java.util.function.Predicate<String> NO_TUNNELS = id -> false;

    @ParameterizedTest
    @FieldSource("CHAIN_TYPES")
    void burnoutResolvesItsTypesVisualAndDropsAfterItsDuration(ResourceKey<GooTypeDefinition> gooType) {
        ChainBurnouts burnouts = new ChainBurnouts(NO_TUNNELS);
        ChainBurnouts.Burnout burnout = burnouts.add(POS, Direction.UP, gooType, ABILITY, 2, START);

        assertEquals(gooType, burnout.visual().gooType());
        assertEquals(START, burnout.startTick());
        int duration = burnout.visual().durationTicks();
        if (duration > 0) {
            assertTrue(burnouts.live(START + duration - 1).contains(burnout), gooType + " dropped early");
        }
        assertFalse(burnouts.live(START + duration).contains(burnout), gooType + " outlives its duration");
    }

    @Test
    void tunnelMarkerAddsNoBurnout() {
        ChainBurnouts burnouts = new ChainBurnouts(TUNNEL_ABILITY::equals);

        assertNull(burnouts.add(POS, Direction.UP, GooTypes.ROCK, TUNNEL_ABILITY, 3, START));
        assertTrue(burnouts.live(START).isEmpty());
        assertNotNull(burnouts.add(POS, Direction.UP, GooTypes.ROCK, ABILITY, 3, START));
    }

    @Test
    void unstableResolvesItsOwnExplosion() {
        assertSame(UnstableExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.UNSTABLE));
    }

    @Test
    void rockResolvesItsOwnExplosion() {
        assertSame(RockExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.ROCK));
    }

    @Test
    void blazeResolvesItsOwnExplosion() {
        assertSame(BlazeExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.BLAZE));
    }

    @Test
    void frostResolvesItsOwnExplosion() {
        assertSame(FrostExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.FROST));
    }

    @Test
    void netherResolvesItsOwnExplosion() {
        assertSame(NetherExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.NETHER));
    }

    @Test
    void metalResolvesItsOwnExplosion() {
        assertSame(MetalExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.METAL));
    }

    @Test
    void crystalResolvesItsOwnExplosion() {
        assertSame(CrystalExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.CRYSTAL));
    }

    @Test
    void glowResolvesItsOwnExplosion() {
        assertSame(GlowExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.GLOW));
    }

    @Test
    void glowBurnoutRendersWithNoBlockEntityAtItsPosition() {
        ChainBurnouts.Burnout burnout = new ChainBurnouts(NO_TUNNELS).add(POS, Direction.UP, GooTypes.GLOW, ABILITY, 1, START);
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        MultiBufferSource.BufferSource buffers = mock(MultiBufferSource.BufferSource.class);
        when(buffers.getBuffer(any())).thenReturn(consumer);

        burnout.visual().render(burnout, new BurnoutFrame(new PoseStack(), buffers, Vec3.ZERO, START + 5f));

        assertFalse(consumer.vertices().isEmpty(), "the glow explosion drew nothing");
    }

    @Test
    void progressRunsFromStartToDuration() {
        ChainBurnouts.Burnout burnout = new ChainBurnouts(NO_TUNNELS).add(POS, Direction.UP, GooTypes.UNSTABLE,
                ABILITY, 1, START);
        int duration = burnout.visual().durationTicks();
        assertEquals(0f, burnout.progress(START), 0f);
        assertEquals(0.5f, burnout.progress(START + duration / 2f), 1e-4f);
        assertEquals(1f, burnout.progress(START + duration * 2f), 0f);
    }
}
