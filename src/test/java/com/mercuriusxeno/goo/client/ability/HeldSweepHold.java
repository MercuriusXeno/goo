package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.DetectOreStep;
import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import com.mercuriusxeno.goo.ability.program.HeldSweepPulses;
import com.mercuriusxeno.goo.ability.program.HeldSweepStep;
import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import com.mercuriusxeno.goo.ability.program.ScryStep;
import com.mercuriusxeno.goo.client.CountingVertexConsumer;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.overlay.OreSightings;
import com.mercuriusxeno.goo.client.overlay.OreXray;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.mockito.Mockito.mock;

/**
 * One held sweep run headless over the {@link HeldSweepWorld}: each tick
 * runs the server's part, Scry's strike or the ore sense's walk, and sends
 * the caster every live pulse; each frame runs the client's part through
 * the same seams the game draws through, into a counting consumer. The
 * counts and the timing run read one hold through it.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
final class HeldSweepHold {

    /** Frames the client draws each tick: sixty a second. */
    static final int FRAMES_A_TICK = 3;
    /** Ticks a ten second hold runs. */
    static final int TEN_SECONDS = 200;
    /** Scry's reach, as astral_scry.json sets it. */
    static final double SCRY_REACH = 96;
    /** The ore sense's radius, as crystal_glimmer.json and metal_magnet.json set it. */
    static final int ORE_RADIUS = 64;
    private static final int ORE_LIFE = 100;
    private static final int TINT = 0x8866FF;
    private static final long START = 1_000;
    private static final double EYE = 1.62;
    private static final UUID CASTER = new UUID(0, 1);

    private final HeldSweepStep step;
    private final HeldSweepWorld world;
    private final HeldSweepPulses server = new HeldSweepPulses();
    private final LiveSweeps sweeps = new LiveSweeps();
    private final CountingVertexConsumer consumer = new CountingVertexConsumer();
    private final List<BlockStateModelPart> cube = List.of(new CubePart(List.of(mock(BakedQuad.class))));
    /**
     * The mobs the client paints: none, since a mocked mob answers each call
     * through Mockito, which would time Mockito rather than the paint loop.
     */
    private final List<Entity> entities = List.of();
    private final Vec3 casterAt = HeldSweepWorld.CASTER;
    private List<HeldSweepPulses.Pulse> live = List.of();
    private long gameTime = START;
    private int held;
    private int scanned;
    private int struck;
    private int payloads;
    private int walls;

    private HeldSweepHold(HeldSweepStep step, HeldSweepWorld world) {
        this.step = step;
        this.world = world;
        ScryPaintedMobs.CLIENT.clear();
        OreSightings.CLIENT.clear();
    }

    /**
     * @param world the world the hold sweeps
     * @return a hold of Scry at its reach, striking with no child steps
     */
    static HeldSweepHold scry(HeldSweepWorld world) {
        return new HeldSweepHold(new ScryStep(GooTypes.ASTRAL, SCRY_REACH, List.of(), List.of()), world);
    }

    /**
     * @param world the world the hold sweeps
     * @return a hold of the ore sense at Glimmer's and Magnet's radius
     */
    static HeldSweepHold oreSense(HeldSweepWorld world) {
        TagKey<net.minecraft.world.level.block.Block> ores = TagKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("goo", "gem_ores"));
        return new HeldSweepHold(new DetectOreStep(ores, GooTypes.CRYSTAL, ORE_RADIUS, ORE_LIFE), world);
    }

    /** Runs one held tick on the server and hands the caster's client the pulses it sent. */
    void tick() {
        held++;
        gameTime++;
        live = server.advance(CASTER, casterAt, held, gameTime, step.pace());
        if (step instanceof ScryStep) {
            strike();
        } else if (step instanceof DetectOreStep ore) {
            walkOre(ore);
        }
        step.sendSweeps(live, gameTime, payload -> {
            payloads++;
            sweeps.hear(payload, gameTime, type -> TINT);
        });
    }

    private void strike() {
        HeldSweepPace pace = step.pace();
        HeldSweepWorld.CountingScan scan = world.scanAround(casterAt);
        for (HeldSweepPulses.Pulse pulse : live) {
            int age = pulse.ageAt(gameTime);
            ScryStep.strikeCrossed(new ScryStep.Strike(scan, Set.of(), casterAt, pulse.origin()),
                    pace.radiusAt(age - 1), pace.radiusAt(age), selected -> struck++);
        }
        scanned += scan.visited();
    }

    private void walkOre(DetectOreStep ore) {
        Set<Vec3> walked = new HashSet<>();
        for (HeldSweepPulses.Pulse pulse : live) {
            if (walked.add(pulse.origin())) {
                OreRevealPayload reveal = ore.revealAt(HeldSweepWorld.ORES, pulse.origin(), pulse.ageAt(gameTime));
                OreSightings.CLIENT.reveal(gameTime, reveal.veins(), reveal.life());
            }
        }
    }

    /**
     * Draws one frame on the caster's client: moves the fronts on, gathers
     * and sorts the walls and the fronts, draws both, then the ore showing.
     *
     * @param frame the frame within the tick, 0 to {@link #FRAMES_A_TICK} less one
     */
    void frame(int frame) {
        double now = gameTime + (double) frame / FRAMES_A_TICK;
        Vec3 camera = casterAt.add(0, EYE, 0);
        PoseStack poseStack = new PoseStack();
        HeldSweepVisual.upkeepFronts(new HeldSweepVisual.Upkeep(sweeps, HeldSweepWorld::isAir,
                HeldSweepWorld::shapeOf), now, entities, null);
        List<HeldSweepVisual.WallGlow> lit = HeldSweepVisual.wallsToDraw(sweeps.walls(), now);
        walls = lit.size();
        SortedDraw wallDraw = HeldSweepVisual.sortedByNearest(lit.stream()
                .map(wall -> wall.face().middle()), camera,
                () -> HeldSweepVisual.drawWalls(poseStack.last(), consumer.pass(), camera, lit));
        List<HeldSweepPulse> travelling = sweeps.pulses().values().stream().filter(pulse -> pulse.travelling(now)).toList();
        SortedDraw frontDraw = HeldSweepVisual.sortedByNearest(travelling.stream()
                .map(pulse -> pulse.field().center()), camera,
                () -> HeldSweepVisual.drawFronts(poseStack.last(), consumer.pass(), camera, travelling, now));
        wallDraw.draw().run();
        frontDraw.draw().run();
        drawOre(poseStack, camera, now);
    }

    private void drawOre(PoseStack poseStack, Vec3 camera, double now) {
        List<OreSightings.Sighting> showing = OreXray.showingAt(now);
        if (!showing.isEmpty()) {
            OreXray.sortedByNearest(showing, camera, () -> OreXray.drawOre(pos -> cube, poseStack, camera, showing,
                    pass -> consumer.pass())).draw().run();
        }
    }

    /**
     * Runs ticks, each with its frames.
     *
     * @param ticks the ticks to run
     */
    void hold(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tick();
            for (int frame = 0; frame < FRAMES_A_TICK; frame++) {
                frame(frame);
            }
        }
    }

    /**
     * Runs ticks with one frame's upkeep each and no draw, so a count
     * reaches the state a long hold leaves without paying for the frames
     * it does not count.
     *
     * @param ticks the ticks to run
     */
    void settle(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tick();
            HeldSweepVisual.upkeepFronts(new HeldSweepVisual.Upkeep(sweeps, HeldSweepWorld::isAir,
                    HeldSweepWorld::shapeOf), gameTime, entities, null);
        }
    }

    /** @return the lit hits the wall list weighs to find a frame's walls */
    int wallListReads() {
        return sweeps.walls().weighed();
    }

    /** @return the quads every frame so far submitted */
    long quads() {
        return consumer.quads();
    }

    /** @return the walls the last frame drew */
    int walls() {
        return walls;
    }

    /** @return the live pulses the client holds */
    int clientPulses() {
        return sweeps.pulses().size();
    }

    /** @return the entities every Scry strike so far visited */
    int scanned() {
        return scanned;
    }

    /** @return the entities every Scry strike so far struck */
    int struck() {
        return struck;
    }

    /** @return the pulse payloads the server sent so far */
    int payloads() {
        return payloads;
    }

    /** A whole block's model part: one quad on each side, none unculled, each side's list held as a model holds it. */
    private record CubePart(List<BakedQuad> side) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? List.of() : side;
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public Material.Baked particleMaterial() {
            throw new UnsupportedOperationException("a counted part draws no particle");
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }
}
