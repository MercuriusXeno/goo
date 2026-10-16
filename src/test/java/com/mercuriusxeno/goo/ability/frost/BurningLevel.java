package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.mockito.MockedStatic;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A mocked level holding fire blocks and burning entities, whose sound and
 * particle resolvers are stubbed since the unit suite holds no registry, so a
 * test watches frost put the fire out. Closing it releases the static stubs.
 */
public final class BurningLevel implements AutoCloseable {

    private final ServerLevel level = mock(ServerLevel.class);
    private final Set<BlockPos> fire;
    private final MockedStatic<SoundPlays> sounds = mockStatic(SoundPlays.class);
    private final MockedStatic<SimpleParticles> particles = mockStatic(SimpleParticles.class);
    private final SimpleParticleType smoke = mock(SimpleParticleType.class);

    /**
     * A level burning at the given blocks, whose box scan for burning
     * entities hands over the given entities.
     *
     * @param fireAt  the fire blocks
     * @param burning the entities the burning scan answers
     */
    public BurningLevel(Set<BlockPos> fireAt, List<Entity> burning) {
        fire = new HashSet<>(fireAt);
        BlockState fireState = mock(BlockState.class);
        when(fireState.is(BlockTags.FIRE)).thenReturn(true);
        BlockState stone = mock(BlockState.class);
        when(stone.getFluidState()).thenReturn(mock(FluidState.class));
        when(level.getBlockState(any())).thenAnswer(inv -> fire.contains(inv.<BlockPos>getArgument(0))
                ? fireState : stone);
        doAnswer(inv -> fire.remove(inv.<BlockPos>getArgument(0))).when(level).removeBlock(any(), eq(false));
        when(level.getEntities((Entity) isNull(), any(AABB.class), any())).thenReturn(burning);
        particles.when(() -> SimpleParticles.resolve(FrostFire.SMOKE)).thenReturn(Optional.of(smoke));
    }

    /**
     * A burning entity standing at a squared distance from the frost's center.
     *
     * @param distanceSquared its squared distance from any point asked
     * @return the entity, on fire
     */
    public static Entity burningAt(double distanceSquared) {
        Entity entity = mock(Entity.class);
        when(entity.isOnFire()).thenReturn(true);
        when(entity.distanceToSqr(any(Vec3.class))).thenReturn(distanceSquared);
        return entity;
    }

    /**
     * The mocked level.
     *
     * @return the level
     */
    public ServerLevel level() {
        return level;
    }

    /**
     * Asserts a fire block went out: it is air now, and the extinguish hiss
     * played and a puff of smoke was sent at its center.
     *
     * @param pos the fire block
     */
    public void assertPutOut(BlockPos pos) {
        assertFalse(fire.contains(pos), "the fire at " + pos + " still burns");
        Vec3 at = Vec3.atCenterOf(pos);
        sounds.verify(() -> SoundPlays.play(level, at, FrostFire.HISS));
        assertTrue("block.fire.extinguish".equals(FrostFire.HISS.sound().getPath()));
        verify(level).sendParticles(eq(smoke), eq(at.x()), eq(at.y()), eq(at.z()), eq(FrostFire.SMOKE_COUNT),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    /**
     * Asserts a fire block still burns, with no hiss played at it.
     *
     * @param pos the fire block
     */
    public void assertStillBurning(BlockPos pos) {
        assertTrue(fire.contains(pos), "the fire at " + pos + " went out");
        sounds.verify(() -> SoundPlays.play(level, Vec3.atCenterOf(pos), FrostFire.HISS), never());
    }

    @Override
    public void close() {
        sounds.close();
        particles.close();
    }
}
