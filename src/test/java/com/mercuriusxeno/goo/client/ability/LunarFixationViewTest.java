package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Lunar fixation view's hold on the caster: the field of view stays as it stands, and the look stays where the
 * caster held it when the fixation began, for the moon and the blue star alike, whatever the mouse does.
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
    /** Just inside the look reach Lunar holds the star within, in degrees off its center. */
    private static final double EDGE_OF_REACH = 7.5;
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
    class OnTheBlueStar {

        private final Vec3 star = Lunar.OVERWORLD_STAR_DIRECTION;
        private final LunarFixation onStar = fixationOn(LunarSight.Sky.OVERWORLD_STAR, star);
        private final float centerPitch = pitchAlong(star);
        private final float centerYaw = yawAlong(star);
        private final float edgeYaw = (float) (EDGE_OF_REACH / Math.cos(Math.toRadians(centerPitch)));

        @Test
        void aLookOnTheStarsCenterStaysWhereItWas() {
            assertLookHeldEveryTick(onStar, centerPitch, centerYaw);
        }

        @Test
        void aLookAtTheEdgeAboveTheStarStaysWhereItWas() {
            assertLookHeldEveryTick(onStar, centerPitch - (float) EDGE_OF_REACH, centerYaw);
        }

        @Test
        void aLookAtTheEdgeBelowTheStarStaysWhereItWas() {
            assertLookHeldEveryTick(onStar, centerPitch + (float) EDGE_OF_REACH, centerYaw);
        }

        @Test
        void aLookAtTheEdgeLeftOfTheStarStaysWhereItWas() {
            assertLookHeldEveryTick(onStar, centerPitch, centerYaw - edgeYaw);
        }

        @Test
        void aLookAtTheEdgeRightOfTheStarStaysWhereItWas() {
            assertLookHeldEveryTick(onStar, centerPitch, centerYaw + edgeYaw);
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
