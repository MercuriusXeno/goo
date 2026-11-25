package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import com.mercuriusxeno.goo.client.CountingVertexConsumer;
import com.mercuriusxeno.goo.client.TranslucentVisual;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.TranslucentVisualPathRegistrations;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.List;
import java.util.Map;

/**
 * Sculker held headless over the {@link SculkerEchoWorld}, its player still
 * at a tunnel crossing and looking along +x: each tick hears a fixed stream
 * of sounds through the real noise joiner, then advances the echo state as
 * the client tick does; each frame runs the echo's draw through the path's
 * gather and sort, the real lit and shroom reads and the real draw, into a
 * counting consumer. The counts and the timing run read one hold through it.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
final class SculkerEchoHold {

    /** Frames the client draws each tick: sixty a second. */
    static final int FRAMES_A_TICK = 3;
    /** Ticks a ten second hold runs. */
    static final int TEN_SECONDS = 200;
    /** The render distance the senses reach, twelve chunks, in blocks. */
    static final double SIGHT_REACH = 192;
    /** Where the player stands: the foot of a tunnel crossing. */
    static final Vec3 PLAYER = HeldSweepWorld.CASTER;
    private static final double EYE = 1.62;
    private static final long START = 1_000;
    /** The player's own swim through stone: a step every fifth of a second, a vibration of frequency 1 beside it. */
    private static final int STEP_EVERY = 4;
    private static final String STEP = "minecraft:block.stone.step";
    private static final int STEP_FREQUENCY = 1;
    /** A mob's groan, ranked 9 by the shipped table, from one of the mobs down the tunnels, every half second. */
    private static final int GROAN_EVERY = 10;
    private static final String GROAN = "minecraft:entity.zombie.ambient";
    private static final List<Vec3> MOBS = List.of(new Vec3(13.5, 0, 1.5), new Vec3(-10.5, 0, 1.5),
            new Vec3(1.5, 0, 13.5), new Vec3(25.5, 0, 1.5), new Vec3(1.5, 0, -22.5), new Vec3(1.5, 12, 1.5));
    /** A shrieker down the tunnel, ranked 15 by the shipped table, every two seconds. */
    private static final int SHRIEK_EVERY = 40;
    private static final String SHRIEK = "minecraft:block.sculk_shrieker.shriek";
    private static final Vec3 SHRIEKER = new Vec3(1.5, 0, 25.5);
    /** How far ahead of the camera, in blocks, a section's middle stands wholly in view; within it the view cuts through. */
    private static final int HALF_SECTION = SectionPos.SECTION_SIZE / 2;

    private final SculkerEchoWorld world = new SculkerEchoWorld();
    private final SculkerEchoes echoes = new SculkerEchoes();
    private final EchoQuads quads = new EchoQuads();
    private final SculkerEchoTick echoTick = new SculkerEchoTick(
            new SculkerNoises(SculkerSoundRanks.shipped(), echoes::hear), echoes,
            new EchoCells(SculkerEchoWorld::lights, SculkerEchoWorld::passesSoundAt), quads);
    private final CountingVertexConsumer consumer = new CountingVertexConsumer();
    private final LevelFrame frame = new LevelFrame(new Matrix4f(), new PoseStack(), new LevelRenderState());
    private final TranslucentVisual echoVisual = (levelFrame, draws) -> draws.accept(new SortedDraw(0, this::drawEchoes));
    private long gameTime = START;
    private int held;
    private int drawn;

    SculkerEchoHold() {
        ShroomVision.forget();
    }

    /** Runs one tick: hears the sounds due on it, then advances the echo state. */
    void tick() {
        held++;
        gameTime++;
        hearDue();
        echoTick.advance(gameTime);
    }

    /** Hears the sounds due this tick, each as the game plays it and the server forwards its vibration. */
    void hearDue() {
        if (held % STEP_EVERY == 0) {
            echoTick.noises().sound(STEP, PLAYER, gameTime);
            echoTick.noises().vibration(PLAYER, gameTime, STEP_FREQUENCY);
        }
        if (held % GROAN_EVERY == 0) {
            echoTick.noises().sound(GROAN, MOBS.get(held / GROAN_EVERY % MOBS.size()), gameTime);
        }
        if (held % SHRIEK_EVERY == 0) {
            echoTick.noises().sound(SHRIEK, SHRIEKER, gameTime);
        }
    }

    /** Draws one frame: the path gathers and sorts the echo's draw, then runs it. */
    void frame() {
        TranslucentVisualPathRegistrations.orderedDraws(List.of(echoVisual), frame)
                .forEach(draw -> draw.draw().run());
    }

    private void drawEchoes() {
        Vec3 camera = PLAYER.add(0, EYE, 0);
        Map<BlockPos, Float> lit = SculkerEchoRenderer.drawnThisFrame(echoes, new AheadView(camera),
                new SculkerEchoRenderer.Sight(PLAYER, gameTime, SIGHT_REACH, 1f), world, camera, world::inNetwork);
        drawn = lit.size();
        SculkerEchoRenderer.drawLit(world, quads, new PoseStack(), consumer, camera, lit);
    }

    /**
     * Runs ticks, each with its frames.
     *
     * @param ticks the ticks to run
     */
    void hold(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tick();
            for (int each = 0; each < FRAMES_A_TICK; each++) {
                frame();
            }
        }
    }

    /** @return the faces the echoes remember */
    int remembered() {
        return echoes.remembered().size();
    }

    /** @return the blocks the last frame drew */
    int drawn() {
        return drawn;
    }

    /** @return the quads every frame so far submitted */
    long quads() {
        return consumer.quads();
    }

    /**
     * The camera's view along +x: a section whose middle stands half a
     * section or more ahead of the camera wholly in view, one as far behind
     * out of it, and one between cut through at the camera's own x.
     *
     * @param camera the camera's place in the world
     */
    private record AheadView(Vec3 camera) implements SculkerEchoRenderer.EchoView {

        @Override
        public SculkerEchoRenderer.Sighting section(long section) {
            double ahead = SectionPos.sectionToBlockCoord(SectionPos.x(section)) + HALF_SECTION - camera.x;
            if (ahead >= HALF_SECTION) {
                return SculkerEchoRenderer.Sighting.WHOLLY;
            }
            return ahead <= -HALF_SECTION ? SculkerEchoRenderer.Sighting.OUT : SculkerEchoRenderer.Sighting.PARTLY;
        }

        @Override
        public boolean holds(BlockPos pos) {
            return pos.getX() + 1 >= camera.x;
        }
    }
}
