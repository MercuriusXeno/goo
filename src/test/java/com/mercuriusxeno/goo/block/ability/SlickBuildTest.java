package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.SlickStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slick multiplies a slider only while it tries to move, keeps the build
 * through a turn, and starts it afresh after a pause, remembering the
 * client's build apart from the server's.
 * slick-ramp-grows-while-a-thing-keeps-moving
 */
class SlickBuildTest {

    private static final double EXACT = 1e-9;
    private static final Vec3 EASTWARD = new Vec3(0.02, 0, 0);
    private static final int EASTWARD_TICKS = 10;

    @Test
    void aSliderThatStopsTryingKeepsTheVelocityItHeld() {
        SlickStep slick = SlickRampTest.shippedSlick();
        FakeSlider slider = new FakeSlider(EASTWARD, new Object(), false);
        slider.slideFor(slick.buildTicks() + 1, slick);
        Vec3 held = slider.velocity;

        slider.trying = false;
        slider.tick(slick);

        assertEquals(held.x, slider.velocity.x, EXACT);
        assertEquals(held.y, slider.velocity.y, EXACT);
        assertEquals(held.z, slider.velocity.z, EXACT);
    }

    @Test
    void aSliderThatTurnsKeepsItsBuild() {
        SlickStep slick = SlickRampTest.shippedSlick();
        FakeSlider slider = new FakeSlider(EASTWARD, new Object(), false);
        slider.slideFor(EASTWARD_TICKS, slick);
        double lastEastward = slider.lastRamp;

        slider.velocity = new Vec3(-slider.velocity.x, 0, 0);
        slider.tick(slick);

        assertTrue(slider.lastRamp >= lastEastward,
                "the first westward tick ramped " + slider.lastRamp + " after " + lastEastward);
        assertTrue(slider.lastRamp > slick.startRamp(),
                "the turn should keep the build past its start of " + slick.startRamp());
    }

    @Test
    void aSliderBackAfterAPauseStartsItsBuildAfresh() {
        SlickStep slick = SlickRampTest.shippedSlick();
        FakeSlider slider = new FakeSlider(EASTWARD, new Object(), false);
        slider.slideFor(EASTWARD_TICKS, slick);

        slider.gameTime += SlickBuild.FORGET_TICKS + 1;
        slider.velocity = EASTWARD;
        slider.tick(slick);

        assertEquals(slick.startRamp(), slider.lastRamp, EXACT);
    }

    @Test
    void theClientsBuildStandsApartFromTheServers() {
        SlickStep slick = SlickRampTest.shippedSlick();
        Object samePlayer = new Object();
        FakeSlider client = new FakeSlider(EASTWARD, samePlayer, true);
        FakeSlider server = new FakeSlider(EASTWARD, samePlayer, false);
        client.slideFor(EASTWARD_TICKS, slick);

        server.gameTime = client.gameTime;
        server.tick(slick);

        assertEquals(slick.startRamp(), server.lastRamp, EXACT);
    }

    private static final class FakeSlider implements SlickBuild.Slider {

        private final Object identity;
        private final boolean clientSide;
        private Vec3 velocity;
        private boolean trying = true;
        private long gameTime = 1;
        private double lastRamp;

        FakeSlider(Vec3 velocity, Object identity, boolean clientSide) {
            this.velocity = velocity;
            this.identity = identity;
            this.clientSide = clientSide;
        }

        void slideFor(int ticks, SlickStep slick) {
            for (int tick = 0; tick < ticks; tick++) {
                tick(slick);
            }
        }

        void tick(SlickStep slick) {
            gameTime++;
            SlickBuild.slide(this, slick);
        }

        @Override
        public Object identity() {
            return identity;
        }

        @Override
        public boolean clientSide() {
            return clientSide;
        }

        @Override
        public boolean tryingToMove() {
            return trying;
        }

        @Override
        public long gameTime() {
            return gameTime;
        }

        @Override
        public Vec3 velocity() {
            return velocity;
        }

        @Override
        public void slide(Vec3 slid) {
            lastRamp = slid.horizontalDistance() / velocity.horizontalDistance();
            velocity = slid;
        }
    }
}
