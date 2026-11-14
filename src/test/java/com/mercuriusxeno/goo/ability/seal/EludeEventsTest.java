package com.mercuriusxeno.goo.ability.seal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Elude's events: only a mob's or a projectile's hit moves the player, to a random safe spot within the radius
 * facing a random heading (decision elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage), and the
 * shimmer clears the tick its held effect ends, so its fade starts at the end (decision
 * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends).
 */
class EludeEventsTest {

    private static final int PLAYER_ID = 7;
    private static final int REFRESH_TICKS = 20;
    private static final int SHIMMER_TICKS = 40;
    private static final int RADIUS = 8;
    private static final BlockPos STOOD = new BlockPos(0, 64, 0);
    private static final long SEED = 20261110L;
    private ServerPlayer player;

    @BeforeEach
    void standUpThePlayer() {
        player = mock(ServerPlayer.class);
        when(player.getId()).thenReturn(PLAYER_ID);
    }

    @Nested
    class Escapes {

        private static DamageSource hit(@Nullable ResourceKey<DamageType> type, @Nullable Entity causing,
                @Nullable Entity direct) {
            DamageSource source = mock(DamageSource.class);
            when(source.is(any(ResourceKey.class))).thenAnswer(call -> call.getArgument(0).equals(type));
            when(source.getEntity()).thenReturn(causing);
            when(source.getDirectEntity()).thenReturn(direct);
            return source;
        }

        static Stream<ResourceKey<DamageType>> environmentalKinds() {
            return Stream.of(DamageTypes.FALL, DamageTypes.IN_FIRE, DamageTypes.ON_FIRE, DamageTypes.LAVA,
                    DamageTypes.DROWN, DamageTypes.IN_WALL, DamageTypes.MAGIC, DamageTypes.STARVE,
                    DamageTypes.FELL_OUT_OF_WORLD, DamageTypes.GENERIC);
        }

        @Test
        void aMobsHit() {
            Mob zombie = mock(Mob.class);
            assertTrue(EludeEvents.escapes(hit(DamageTypes.MOB_ATTACK, zombie, zombie)));
        }

        @Test
        void aMobsArrow() {
            assertTrue(EludeEvents.escapes(hit(DamageTypes.ARROW, mock(Mob.class), mock(Projectile.class))));
        }

        @Test
        void anArrowNobodyShot() {
            assertTrue(EludeEvents.escapes(hit(DamageTypes.ARROW, null, mock(Projectile.class))));
        }

        @Test
        void aPlayersArrow() {
            assertTrue(EludeEvents.escapes(hit(DamageTypes.ARROW, mock(ServerPlayer.class),
                    mock(Projectile.class))));
        }

        @Test
        void notAPlayersFist() {
            ServerPlayer hitter = mock(ServerPlayer.class);
            assertFalse(EludeEvents.escapes(hit(DamageTypes.PLAYER_ATTACK, hitter, hitter)));
        }

        @ParameterizedTest
        @MethodSource("environmentalKinds")
        void notTheWorldsHarm(ResourceKey<DamageType> kind) {
            assertFalse(EludeEvents.escapes(hit(kind, null, null)), kind::toString);
        }
    }

    @Nested
    class SafeSpot {

        private static boolean onTheFloor(BlockPos spot) {
            return spot.getY() == STOOD.getY();
        }

        @Test
        void everySpotLiesWithinTheRadiusOnASafeColumn() {
            LongStream.range(0, 64).forEach(seed -> {
                BlockPos spot = EludeEvents.safeSpotNear(STOOD, RADIUS, RandomSource.create(seed),
                        SafeSpot::onTheFloor).orElseThrow();
                assertTrue(onTheFloor(spot), spot::toString);
                assertTrue(spot.distSqr(STOOD) <= RADIUS * RADIUS, spot::toString);
                assertTrue(spot.distSqr(STOOD) >= EludeEvents.MIN_ESCAPE * EludeEvents.MIN_ESCAPE,
                        spot::toString);
            });
        }

        @Test
        void theSeededSpotIsNotTheLookDirectionPoint() {
            BlockPos lookPoint = STOOD.south(RADIUS);
            BlockPos spot = EludeEvents.safeSpotNear(STOOD, RADIUS, RandomSource.create(SEED),
                    SafeSpot::onTheFloor).orElseThrow();
            assertNotEquals(lookPoint, spot);
        }

        @Test
        void seedsLandDifferentSpots() {
            Set<BlockPos> spots = new HashSet<>();
            LongStream.range(0, 16).forEach(seed -> EludeEvents.safeSpotNear(STOOD, RADIUS,
                    RandomSource.create(seed), SafeSpot::onTheFloor).ifPresent(spots::add));
            assertTrue(spots.size() > 1, spots::toString);
        }

        @Test
        void noSafeSpotGivesUpAfterItsTries() {
            AtomicInteger tried = new AtomicInteger();
            Optional<BlockPos> spot = EludeEvents.safeSpotNear(STOOD, RADIUS, RandomSource.create(SEED),
                    candidate -> tried.incrementAndGet() < 0);
            assertEquals(Optional.empty(), spot);
            int columnHeights = 2 * RADIUS + 1;
            assertTrue(tried.get() <= EludeEvents.ESCAPE_TRIES * columnHeights, () -> "tried " + tried.get());
        }
    }

    @Nested
    class Heading {

        private static final float LOOK_YAW = 30f;

        @Test
        void theHeadingIsTheSeededDrawAcrossAFullTurn() {
            float expected = RandomSource.create(SEED).nextFloat() * 360f - 180f;
            assertEquals(expected, EludeEvents.randomHeading(RandomSource.create(SEED)));
        }

        @Test
        void aQuarterDrawFacesWest() {
            RandomSource random = mock(RandomSource.class);
            when(random.nextFloat()).thenReturn(0.25f);
            assertEquals(-90f, EludeEvents.randomHeading(random));
        }

        @Test
        void theBlinkTurnsThePlayerToTheHeadingRatherThanTheLook() {
            ServerLevel level = mock(ServerLevel.class);
            Vec3 feet = new Vec3(3.5, 64, -2.5);
            float heading = EludeEvents.randomHeading(RandomSource.create(SEED));
            when(player.position()).thenReturn(Vec3.atBottomCenterOf(STOOD));
            when(player.getYRot()).thenReturn(LOOK_YAW);
            try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
                EludeEvents.blinkTo(level, player, feet, heading);
            }
            verify(player).teleportTo(level, feet.x, feet.y, feet.z, Set.of(), heading, 0f, true);
            assertNotEquals(LOOK_YAW, heading);
        }
    }

    @Test
    void endingTheEludeSendsTheShimmerClear() {
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            EludeEvents.clearShimmer(player);

            visuals.verify(() -> EntityVisuals.sendToWatchers(player,
                    new AilmentPayload(PLAYER_ID, AilmentKind.ELUDE, 0)));
        }
    }

    @Test
    void aRefreshTickSendsTheShimmerForItsFortyTicks() {
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            EludeEvents.keepShimmer(player, EludeEvents.SHIMMER_REFRESH_TICKS);

            visuals.verify(() -> EntityVisuals.sendToWatchers(player,
                    new AilmentPayload(PLAYER_ID, AilmentKind.ELUDE, SHIMMER_TICKS)));
        }
    }

    @Test
    void theShimmerRefreshesEveryTwentyTicks() {
        assertEquals(REFRESH_TICKS, EludeEvents.SHIMMER_REFRESH_TICKS);
    }
}
