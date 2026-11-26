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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
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
    /** The reach the lag thread measured Scry at, where its two second crossing runs 48 blocks a second. */
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
    /** Each lit wall's quads, kept across frames as the game keeps them. */
    private final EchoQuads wallQuads = new EchoQuads();
    /** The fixed world as an echo draw reads it: every block a whole cube with one quad on each side. */
    private final SculkerEchoRenderer.EchoWorld cubeWorld = new CubeEchoWorld(cube);
    /**
     * The mobs the client paints: none unless a test hands it some, since a
     * mocked mob answers each call through Mockito, which would time Mockito
     * rather than the paint loop.
     */
    private List<Entity> entities = List.of();
    private Vec3 casterAt = HeldSweepWorld.CASTER;
    private IntFunction<Vec3> path = held -> HeldSweepWorld.CASTER;
    private int scannedThisTick;
    private long blockReads;
    private int payloadsThisTick;
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

    /**
     * Has the caster walk a path, so each pulse leaves from a new spot.
     *
     * @param where where the caster stands on each held tick
     * @return this hold
     */
    HeldSweepHold walking(IntFunction<Vec3> where) {
        path = where;
        return this;
    }

    /**
     * Hands the client mobs to paint as the fronts cross them.
     *
     * @param mobs the mobs the client draws
     * @return this hold
     */
    HeldSweepHold painting(List<Entity> mobs) {
        entities = List.copyOf(mobs);
        return this;
    }

    /** Runs one held tick on the server and hands the caster's client the pulses it sent. */
    void tick() {
        held++;
        gameTime++;
        casterAt = path.apply(held);
        scannedThisTick = 0;
        payloadsThisTick = 0;
        live = server.advance(CASTER, casterAt, held, gameTime, step.pace());
        if (step instanceof ScryStep) {
            strike();
        } else if (step instanceof DetectOreStep ore) {
            walkOre(ore);
        }
        step.sendSweeps(live, gameTime, payload -> {
            payloads++;
            payloadsThisTick++;
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
        scannedThisTick = scan.visited();
    }

    private void walkOre(DetectOreStep ore) {
        Set<Vec3> walked = new HashSet<>();
        for (HeldSweepPulses.Pulse pulse : live) {
            if (walked.add(pulse.origin())) {
                OreRevealPayload reveal = ore.revealAt(HeldSweepWorld.ORES, pulse.origin(), pulse.ageAt(gameTime));
                OreSightings.CLIENT.reveal(gameTime, reveal.veins(), reveal.life(), reveal.gooType());
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
        HeldSweepVisual.upkeepFronts(new HeldSweepVisual.Upkeep(sweeps, countedWorld()), now, entities, null);
        List<HeldSweepVisual.WallGlow> lit = HeldSweepVisual.wallsToDraw(sweeps.walls(), now);
        walls = lit.size();
        SortedDraw wallDraw = HeldSweepVisual.sortedByNearest(lit.stream()
                .map(wall -> wall.face().middle()), camera,
                () -> HeldSweepVisual.drawWalls(poseStack, consumer.pass(), camera, lit, pos -> wallQuads.at(cubeWorld, pos)));
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
                    gooType -> 0, pass -> consumer.pass())).draw().run();
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
            HeldSweepVisual.upkeepFronts(new HeldSweepVisual.Upkeep(sweeps, countedWorld()), gameTime, entities, null);
        }
    }

    /** @return the lit hits the wall list weighs to find a frame's walls */
    int wallListReads() {
        return sweeps.walls().weighed();
    }

    /**
     * Everything a long hold could pile up, as the last tick left it.
     *
     * @param serverPulses     the pulses the server holds out for the caster
     * @param clientPulses     the pulses the client holds
     * @param litWalls         the walls the client holds lit
     * @param fields           the fields of walls the client holds, one for each spot pulses left from
     * @param paintedMobs      the mobs the client holds painted
     * @param scannedThisTick  the entities the server's strikes scanned on the tick
     * @param payloadsThisTick the pulse payloads the server sent on the tick
     */
    record Quantities(int serverPulses, int clientPulses, int litWalls, int fields, int paintedMobs,
                      int scannedThisTick, int payloadsThisTick) {
    }

    /** @return the quantities a long hold could pile up, as the last tick left them */
    Quantities quantities() {
        return new Quantities(server.liveFor(CASTER), sweeps.pulses().size(), sweeps.walls().size(),
                sweeps.fields().size(), ScryPaintedMobs.CLIENT.count(), scannedThisTick, payloadsThisTick);
    }

    private boolean countedIsAir(BlockPos pos) {
        blockReads++;
        return HeldSweepWorld.isAir(pos);
    }

    /** The fixed world with each block read counted, whether the walk asks if it lights or lets the front by. */
    private WallWorld countedWorld() {
        return new WallWorld(pos -> !countedIsAir(pos), this::countedIsAir);
    }

    /** @return the block reads every walk of the client's fields so far made, lit and passed alike */
    long blockReads() {
        return blockReads;
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

    /**
     * The fixed world as an echo draw reads it: every block that is not air
     * stands as one state drawing the cube's parts.
     *
     * @param parts the cube's parts
     */
    record CubeEchoWorld(List<BlockStateModelPart> parts) implements SculkerEchoRenderer.EchoWorld {

        private static final BlockState SOLID = mock(BlockState.class);

        @Override
        public BlockState stateAt(BlockPos pos) {
            return SOLID;
        }

        @Override
        public void collectParts(BlockPos pos, BlockState state, List<BlockStateModelPart> collected) {
            collected.addAll(parts);
        }

        @Override
        public boolean passesSound(BlockPos pos) {
            return HeldSweepWorld.isAir(pos);
        }
    }
}
