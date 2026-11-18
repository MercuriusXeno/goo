package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Lunar fixation view's hold on the caster: the field of view stays as it stands, and the look stays where the
 * caster held it when the fixation began, for the moon and the blue planet alike, whatever the mouse does.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision lunar-just-looks-at-the-moon-without-turning-your-body
 */
class LunarFixationViewTest {

    private static final long CAST_AT = 1000L;
    private static final int FIXATION_TICKS = 60;
    private static final long MID_FIXATION = CAST_AT + FIXATION_TICKS * 3 / 4;
    private static final float SPRINTING_MODIFIER = 1.15F;
    private static final float FULL_SCALE = 1F;
    private static final float EXACT = 1e-6F;

    private static final float STRAIGHT_UP = -90F;
    private static final float FACING_SOUTH = 0F;
    private static final float FACING_WEST = 90F;
    private static final float FACING_NORTH = 180F;
    private static final float FACING_EAST = -90F;
    /** A moon angle leaning the moon a few degrees west of the zenith. */
    private static final float MOON_PAST_ZENITH = 4F;
    /** A look two degrees east of the zenith, so the moon just past it stands behind the caster's facing. */
    private static final float JUST_SHORT_OF_STRAIGHT_UP = -88F;
    /** A moon angle standing the blue planet well up in lunar's western sky. */
    private static final float PLANET_UP_IN_THE_WEST = 40F;
    /** Just inside the planet's disc, past the reach a smaller body holds, in degrees off its center. */
    private static final double EDGE_OF_THE_DISC = 11.0;
    private static final float MOUSE_PITCH_PUSH = 30F;
    private static final float MOUSE_YAW_PUSH = 45F;

    /**
     * The field of view modifier a caster ends on once every LunarFixationView handler has seen the event.
     *
     * @param fixation the fixation the caster holds
     * @return the modifier
     */
    private static float fovModifierUnder(LunarFixation fixation) {
        Level level = mock(Level.class);
        when(level.getGameTime()).thenReturn(MID_FIXATION);
        Player caster = mock(Player.class);
        when(caster.level()).thenReturn(level);
        when(caster.getData(GooAttachments.LUNAR_FIXATION)).thenReturn(fixation);
        IEventBus bus = BusBuilder.builder().build();
        bus.register(LunarFixationView.class);
        ComputeFovModifierEvent event = new ComputeFovModifierEvent(caster, SPRINTING_MODIFIER, FULL_SCALE);
        bus.post(event);
        return event.getNewFovModifier();
    }

    private static LunarFixation fixationOn(LunarSight.Sky sky, Vec3 direction) {
        return LunarFixation.held(sky, direction, Vec3.ZERO, CAST_AT, FIXATION_TICKS);
    }

    /**
     * Runs every tick of a hold from a look the caster held as it began, the mouse still, and asserts each tick
     * ends on that look.
     *
     * @param fixation the fixation holding the caster
     * @param pitch    the pitch held as it began, in degrees
     * @param yaw      the yaw held as it began, in degrees
     */
    private static void assertLookHeldEveryTick(LunarFixation fixation, float pitch, float yaw) {
        LunarFixationView.HeldLook look = LunarFixationView.HeldLook.NONE;
        float tickPitch = pitch;
        float tickYaw = yaw;
        for (int tick = 0; tick < FIXATION_TICKS; tick++) {
            look = LunarFixationView.lookOnTick(look, fixation, tickPitch, tickYaw);
            int at = tick;
            LunarFixationView.HeldLook ended = look;
            assertAll("tick " + at,
                    () -> assertEquals(pitch, ended.pitch(), EXACT, "pitch"),
                    () -> assertEquals(yaw, ended.yaw(), EXACT, "yaw"));
            tickPitch = look.pitch();
            tickYaw = look.yaw();
        }
    }

    private static float pitchAlong(Vec3 direction) {
        return (float) Math.toDegrees(-Math.asin(direction.normalize().y()));
    }

    private static float yawAlong(Vec3 direction) {
        Vec3 unit = direction.normalize();
        return (float) Math.toDegrees(Math.atan2(-unit.x(), unit.z()));
    }

    @Test
    void aStandingFixationLeavesTheFieldOfViewAsNoFixationDoes() {
        LunarFixation standing = LunarFixation.held(LunarSight.Sky.MOON, new Vec3(0, 1, 0), Vec3.ZERO, CAST_AT,
                FIXATION_TICKS);
        assertEquals(fovModifierUnder(LunarFixation.NONE), fovModifierUnder(standing), EXACT);
    }

    @Nested
    class OnTheMoon {

        private final LunarFixation zenith = fixationOn(LunarSight.Sky.MOON, LunarSight.moonDirection(0F));

        @Test
        void aLookStraightUpFacingNorthStaysWhereItWas() {
            assertLookHeldEveryTick(zenith, STRAIGHT_UP, FACING_NORTH);
        }

        @Test
        void aLookStraightUpFacingEastStaysWhereItWas() {
            assertLookHeldEveryTick(zenith, STRAIGHT_UP, FACING_EAST);
        }

        @Test
        void aLookStraightUpFacingSouthStaysWhereItWas() {
            assertLookHeldEveryTick(zenith, STRAIGHT_UP, FACING_SOUTH);
        }

        @Test
        void aLookStraightUpFacingWestStaysWhereItWas() {
            assertLookHeldEveryTick(zenith, STRAIGHT_UP, FACING_WEST);
        }

        @Test
        void aLookAtAMoonJustPastTheZenithFromItsFarSideStaysWhereItWas() {
            LunarFixation pastZenith = fixationOn(LunarSight.Sky.MOON, LunarSight.moonDirection(MOON_PAST_ZENITH));
            assertLookHeldEveryTick(pastZenith, JUST_SHORT_OF_STRAIGHT_UP, FACING_EAST);
        }
    }

    @Nested
    class OnTheBluePlanet {

        private final Vec3 planet = LunarSight.moonDirection(PLANET_UP_IN_THE_WEST);
        private final LunarFixation onPlanet = fixationOn(LunarSight.Sky.BLUE_PLANET, planet);
        private final float centerPitch = pitchAlong(planet);
        private final float centerYaw = yawAlong(planet);
        private final float edgeYaw = (float) (EDGE_OF_THE_DISC / Math.cos(Math.toRadians(centerPitch)));

        @Test
        void aLookOnThePlanetsCenterStaysWhereItWas() {
            assertLookHeldEveryTick(onPlanet, centerPitch, centerYaw);
        }

        @Test
        void aLookAtTheDiscsEdgeAboveThePlanetsCenterStaysWhereItWas() {
            assertLookHeldEveryTick(onPlanet, centerPitch - (float) EDGE_OF_THE_DISC, centerYaw);
        }

        @Test
        void aLookAtTheDiscsEdgeBelowThePlanetsCenterStaysWhereItWas() {
            assertLookHeldEveryTick(onPlanet, centerPitch + (float) EDGE_OF_THE_DISC, centerYaw);
        }

        @Test
        void aLookAtTheDiscsEdgeLeftOfThePlanetsCenterStaysWhereItWas() {
            assertLookHeldEveryTick(onPlanet, centerPitch, centerYaw - edgeYaw);
        }

        @Test
        void aLookAtTheDiscsEdgeRightOfThePlanetsCenterStaysWhereItWas() {
            assertLookHeldEveryTick(onPlanet, centerPitch, centerYaw + edgeYaw);
        }
    }

    /**
     * decision diagnose-and-correct-the-moon-twitch-on-the-first-grab
     */
    @Nested
    class AsTheHoldTakes {
        private static final float HELD_PITCH = -60F;
        private static final float HELD_YAW = 120F;
        private static final double MOUSE_PUSH = 40.0;
        /** The degrees Entity.turn turns a mouse push into: 0.15 a unit. */
        private static final float MOUSE_TURN = (float) MOUSE_PUSH * 0.15F;
        private static final int TICKS = 4;
        /** The frames a tick draws between it and the next, each turned by the mouse. */
        private static final int FRAMES_A_TICK = 3;

        /**
         * Runs a client tick as the caster's client does: the last tick's look kept, then the view's hold at the
         * tick's end.
         *
         * @param caster   the held caster
         * @param kept     the look kept from the tick before
         * @param fixation the fixation holding them
         * @return the look kept
         */
        private static LunarFixationView.HeldLook tick(Player caster, LunarFixationView.HeldLook kept,
                LunarFixation fixation) {
            caster.xRotO = caster.getXRot();
            caster.yRotO = caster.getYRot();
            LunarFixationView.HeldLook look = LunarFixationView.lookOnTick(kept, fixation, caster.getXRot(),
                    caster.getYRot());
            LunarFixationView.holdView(caster, look);
            return look;
        }

        private static void assertHeld(Player caster, String at) {
            assertAll(at,
                    () -> assertEquals(HELD_PITCH, caster.getXRot(), EXACT, "this tick's pitch"),
                    () -> assertEquals(HELD_YAW, caster.getYRot(), EXACT, "this tick's yaw"),
                    () -> assertEquals(HELD_PITCH, caster.xRotO, EXACT, "the last tick's pitch"),
                    () -> assertEquals(HELD_YAW, caster.yRotO, EXACT, "the last tick's yaw"));
        }

        @Test
        void everyFrameDrawsTheLookHeldBeforeTheFixationArrivedThoughTheMouseTurns() {
            Player caster = mock(Player.class, Answers.CALLS_REAL_METHODS);
            caster.setXRot(HELD_PITCH);
            caster.setYRot(HELD_YAW);
            LunarFixation fixation = fixationOn(LunarSight.Sky.MOON, LunarSight.moonDirection(0F));
            LunarFixationView.HeldLook look = LunarFixationView.HeldLook.NONE;
            for (int tick = 0; tick <= TICKS; tick++) {
                look = tick(caster, look, fixation);
                assertHeld(caster, "tick " + tick + " after the fixation arrived");
                // Minecraft.runTick turns the caster by the mouse on every frame after the tick, as
                // MouseHandler.turnPlayer hands it on.
                for (int frame = 1; frame <= FRAMES_A_TICK; frame++) {
                    LunarFixationView.turnUnlessHeld(caster, fixation, MOUSE_PUSH, MOUSE_PUSH);
                    assertHeld(caster, "frame " + frame + " after tick " + tick + ", the mouse turned");
                }
            }
        }

        @Test
        void theMouseTurnsACasterNoFixationHolds() {
            Player caster = mock(Player.class, Answers.CALLS_REAL_METHODS);
            caster.setXRot(HELD_PITCH);
            caster.setYRot(HELD_YAW);
            LunarFixationView.turnUnlessHeld(caster, LunarFixation.NONE, MOUSE_PUSH, MOUSE_PUSH);
            assertAll(() -> assertEquals(HELD_PITCH + MOUSE_TURN, caster.getXRot(), EXACT, "pitch"),
                    () -> assertEquals(HELD_YAW + MOUSE_TURN, caster.getYRot(), EXACT, "yaw"));
        }
    }

    @Test
    void aLookTheMouseMovedIsSetBackToTheHeldLook() {
        LunarFixation zenith = fixationOn(LunarSight.Sky.MOON, LunarSight.moonDirection(0F));
        LunarFixationView.HeldLook held = LunarFixationView.lookOnTick(LunarFixationView.HeldLook.NONE, zenith,
                STRAIGHT_UP, FACING_NORTH);
        LunarFixationView.HeldLook setBack = LunarFixationView.lookOnTick(held, zenith,
                STRAIGHT_UP + MOUSE_PITCH_PUSH, FACING_NORTH + MOUSE_YAW_PUSH);
        assertAll(
                () -> assertEquals(STRAIGHT_UP, setBack.pitch(), EXACT, "pitch"),
                () -> assertEquals(FACING_NORTH, setBack.yaw(), EXACT, "yaw"));
    }
}
