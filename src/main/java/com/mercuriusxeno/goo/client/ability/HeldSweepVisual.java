package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import com.mercuriusxeno.goo.ability.program.HeldSweepSections;
import com.mercuriusxeno.goo.client.BufferedPass;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.network.HeldSweepPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 * The caster's side of every held sweep, Scry's, Glimmer's and Magnet's, as
 * a sonar return: each live pulse's front leaves the caster at the steady
 * pace as a faint skin in the casting goo type's color, every wall open to
 * air that a front hits lights through walls at the hit, holds for three
 * seconds and fades over three more, so the cave hangs lit behind the fronts; fronts overlap, and a
 * front already out when the hold lets go runs on to its range.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
public final class HeldSweepVisual {

    /** The most walls one pulse holds lit at once; past it that front lights no more. */
    static final int MOST_BLOCKS = 12_288;
    /** The front's skin alpha, low since it adds onto the whole view. */
    private static final int SPHERE_ALPHA = 20;
    /** A wall's alpha on the tick it is hit; it fades from there. */
    private static final int FACE_ALPHA = 110;
    /** How far a face quad stands off its block, so it never sinks into the face it marks. */
    private static final float FACE_LIFT = 0.002f;

    private static final LiveSweeps SWEEPS = new LiveSweeps();
    private static final Map<Integer, HeldSweepPulse> PULSES = SWEEPS.pulses();
    private static ClientLevel heardIn;

    private HeldSweepVisual() {
    }

    /**
     * Handles one live pulse's tick on the client thread: a pulse not seen
     * before starts from its origin, and the front takes its new radius.
     *
     * @param payload the sweep payload
     * @param context the network context
     */
    public static void onPayload(HeldSweepPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                hear(mc.level, payload);
            }
        });
    }

    private static void hear(ClientLevel level, HeldSweepPayload payload) {
        if (level != heardIn) {
            clear();
            heardIn = level;
        }
        SWEEPS.hear(payload, level.getGameTime(), ClientGooTypes::color);
    }

    /** Drops every pulse, every lit wall and every painted mob, as a disconnect or a change of dimension does. */
    public static void clear() {
        SWEEPS.clear();
        ScryPaintedMobs.CLIENT.clear();
        heardIn = null;
    }

    /**
     * The client's game time with its partial tick, the clock the painted
     * mobs' outlines read.
     *
     * @return the game time, 0 with no level
     */
    static double clientTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /**
     * How brightly a wall shows a while after a front hit it: whole from the
     * hit for three seconds, then falling slowly to nothing over the three
     * seconds left of the linger.
     *
     * @param sinceHit ticks since the front hit it
     * @return the glow in [0, 1]
     */
    static float glowAt(double sinceHit) {
        double fading = sinceHit - HeldSweepPace.FULL_TICKS;
        if (fading <= 0) {
            return 1f;
        }
        return (float) Math.clamp(1 - fading / (HeldSweepPace.LINGER_TICKS - HeldSweepPace.FULL_TICKS), 0, 1);
    }

    /**
     * The color the fronts and the walls draw in: the casting goo type's
     * color, so each sweep reads in its own type's colors and a datapack
     * recoloring the type recolors the sweep.
     *
     * @param gooType   the goo type the pulse names
     * @param typeColor answers a goo type's color
     * @return 0xRRGGBB
     */
    static int tint(ResourceKey<GooTypeDefinition> gooType, ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
        return typeColor.applyAsInt(gooType);
    }

    /**
     * Moves every front on, drops the finished pulses and the walls no pulse
     * leaves from any more, and hands the shared path the lit walls, which
     * show through walls (decision held-sweeps-share-one-fast-shell-visual),
     * sorted by the nearest lit wall. The frame's upkeep runs here rather
     * than in {@link #collect}: the path draws THROUGH_BLOCKS before
     * FAR_TO_NEAR, so this runs first each frame, and the fronts' shell then
     * draws from the state it left.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the walls' draw goes
     */
    public static void collectFaces(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || PULSES.isEmpty()) {
            return;
        }
        if (level != heardIn) {
            clear();
            return;
        }
        double now = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        upkeepFronts(new Upkeep(SWEEPS, at -> level.getBlockState(at).isAir(),
                at -> level.getBlockState(at).getShape(level, at).toAabbs()), now, level.entitiesForRendering(),
                mc.player);
        List<WallGlow> walls = wallsToDraw(SWEEPS.walls(), now);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        draws.accept(sortedByNearest(walls.stream().map(wall -> wall.face().middle()), camera,
                () -> drawWalls(frame.poseStack().last(), BufferedPass.of(buffers, GooRenderTypes.SCRY_FACES_TYPE),
                        camera, walls)));
    }

    /**
     * Hands the shared path the travelling fronts' shell, sorted by the
     * nearest front's center, from the state {@link #collectFaces} moved on
     * this frame.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the shell's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || PULSES.isEmpty()) {
            return;
        }
        double now = clientTime();
        List<HeldSweepPulse> travelling = PULSES.values().stream().filter(pulse -> pulse.travelling(now)).toList();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        draws.accept(sortedByNearest(travelling.stream().map(pulse -> pulse.field().center()), camera,
                () -> drawFronts(frame.poseStack().last(), BufferedPass.of(buffers, GooRenderTypes.GLOW_SHELL_TYPE),
                        camera, travelling, now)));
    }

    /**
     * What a frame's upkeep moves on: the live sweeps and the world the fronts walk.
     *
     * @param sweeps  the live pulses, the walls around each spot they leave from and the walls lit
     * @param isAir   whether a position holds air
     * @param shapeOf a block's voxel shape boxes, block-local
     */
    record Upkeep(LiveSweeps sweeps, Predicate<BlockPos> isAir, Function<BlockPos, List<AABB>> shapeOf) {
    }

    /**
     * A frame's upkeep: moves every front on, drops the walls around a spot
     * no live pulse leaves from any more, and paints the mobs the fronts
     * crossed.
     *
     * @param upkeep   the pulses, fields and world the upkeep moves on
     * @param now      the game time with its partial tick
     * @param entities the entities the client draws
     * @param caster   the caster, never painted
     */
    static void upkeepFronts(Upkeep upkeep, double now, Iterable<Entity> entities, @Nullable Entity caster) {
        HeldSweepSections.profiled(HeldSweepSections.FRONT_UPKEEP, () -> {
            Map<Integer, HeldSweepPulse> pulses = upkeep.sweeps().pulses();
            advanceAll(pulses, now, upkeep.isAir(), upkeep.shapeOf());
            upkeep.sweeps().fields().values().removeIf(field -> pulses.values().stream()
                    .noneMatch(pulse -> pulse.field() == field));
            paintCrossedMobs(pulses.values(), entities, caster, now);
        });
    }

    /**
     * A batch sorted by the nearest of its shapes.
     *
     * @param shapes each shape's place in the world
     * @param camera the camera's place in the world
     * @param draw   draws the batch, ending its buffers
     * @return the draw
     */
    static SortedDraw sortedByNearest(Stream<Vec3> shapes, Vec3 camera, Runnable draw) {
        return HeldSweepSections.profiled(HeldSweepSections.SORT_KEY, () -> SortedDraw.nearestOf(shapes, camera, draw));
    }

    /**
     * Moves every live pulse's front on to a moment and drops the pulses
     * that have finished.
     *
     * @param pulses  the live pulses by id
     * @param now     the game time with its partial tick
     * @param isAir   whether a position holds air
     * @param shapeOf a block's voxel shape boxes, block-local
     */
    static void advanceAll(Map<Integer, HeldSweepPulse> pulses, double now, Predicate<BlockPos> isAir,
                           Function<BlockPos, List<AABB>> shapeOf) {
        for (HeldSweepPulse pulse : pulses.values()) {
            // each front gets its own room, so an old front lit far out never starves a new one near the caster
            pulse.advance(now, isAir, shapeOf, MOST_BLOCKS - pulse.lit().size());
        }
        pulses.values().removeIf(pulse -> pulse.finished(now));
    }

    /**
     * Emits every travelling front's sphere and submits the batch.
     *
     * @param pose       the pose
     * @param pass       the batch the spheres fill
     * @param camera     the camera's position
     * @param travelling the fronts still travelling
     * @param now        the game time with its partial tick
     */
    static void drawFronts(PoseStack.Pose pose, BufferedPass pass, Vec3 camera, List<HeldSweepPulse> travelling,
                           double now) {
        HeldSweepSections.profiled(HeldSweepSections.FRONT_DRAW, () -> {
            VertexConsumer consumer = pass.consumer();
            for (HeldSweepPulse pulse : travelling) {
                ColorSphere.emit(pose, consumer, pulse.field().center().subtract(camera),
                        (float) pulse.drawnRadius(now), ARGB.color(SPHERE_ALPHA, pulse.tintRgb()));
            }
            pass.end().run();
        });
    }

    /**
     * Emits every lit wall's quads, each side open to air on each box of its
     * shape, and submits the batch.
     *
     * @param pose   the pose
     * @param pass   the batch the walls fill
     * @param camera the camera's position
     * @param walls  the walls to draw
     */
    static void drawWalls(PoseStack.Pose pose, BufferedPass pass, Vec3 camera, List<WallGlow> walls) {
        HeldSweepSections.profiled(HeldSweepSections.WALL_DRAW, () -> {
            VertexConsumer consumer = pass.consumer();
            for (WallGlow wall : walls) {
                int color = ARGB.color(Math.round(FACE_ALPHA * wall.glow()), wall.tintRgb());
                Vec3 corner = Vec3.atLowerCornerOf(wall.face().pos()).subtract(camera);
                for (AABB box : wall.face().boxes()) {
                    for (Direction side : wall.face().sides()) {
                        emitBoxFace(pose, consumer, box.move(corner), side, color);
                    }
                }
            }
            pass.end().run();
        });
    }

    /**
     * The walls a frame draws and how brightly: each wall once, at the
     * brightest glow any live front left on it, so a wall a later front hits
     * again lights afresh rather than stacking brighter with every pulse,
     * since the walls draw added onto the world.
     *
     * @param walls the walls the live fronts hold lit
     * @param now   the game time with its partial tick
     * @return each wall to draw with its glow and color
     */
    static List<WallGlow> wallsToDraw(LitWalls walls, double now) {
        return HeldSweepSections.profiled(HeldSweepSections.WALL_LIST, () -> walls.glowingAt(now));
    }

    /**
     * A wall a frame draws.
     *
     * @param face    the wall
     * @param glow    how brightly, 0 to 1
     * @param tintRgb its color, 0xRRGGBB
     */
    record WallGlow(EchoField.EchoFace face, float glow, int tintRgb) {
    }

    /**
     * Paints every living mob, the caster aside, that a mob-painting front
     * crossed this frame, and lets go the mobs whose linger has run out.
     *
     * @param pulses   the live pulses
     * @param entities the entities the client draws
     * @param caster   the caster, never painted
     * @param now      the game time with its partial tick
     */
    private static void paintCrossedMobs(Collection<HeldSweepPulse> pulses, Iterable<Entity> entities,
                                         @Nullable Entity caster, double now) {
        for (HeldSweepPulse pulse : pulses) {
            if (pulse.paintsMobs() && pulse.front() > pulse.frontBefore()) {
                paintCrossedBy(pulse, entities, caster, now);
            }
        }
        ScryPaintedMobs.CLIENT.forgetFaded(now);
    }

    /**
     * Paints the living mobs, the caster aside, that one front crossed this frame.
     *
     * @param pulse    the pulse whose front moved
     * @param entities the entities the client draws
     * @param caster   the caster, never painted
     * @param now      the game time with its partial tick
     */
    private static void paintCrossedBy(HeldSweepPulse pulse, Iterable<Entity> entities, @Nullable Entity caster,
                                       double now) {
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity && entity != caster && ScryPaintedMobs.crossedBy(
                    pulse.field().center(), pulse.frontBefore(), pulse.front(), entity.getBoundingBox().getCenter())) {
                ScryPaintedMobs.CLIENT.paint(entity.getId(), now, pulse.tintRgb());
            }
        }
    }

    /**
     * Emits one side of a box as a quad standing just off it.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param box      the box, camera-relative
     * @param side     the side to draw
     * @param color    the packed ARGB color
     */
    static void emitBoxFace(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction side, int color) {
        Direction.Axis axis = side.getAxis();
        double plane = side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? box.max(axis) + FACE_LIFT : box.min(axis) - FACE_LIFT;
        Direction.Axis first = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis second = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        double[][] corners = {{box.min(first), box.min(second)}, {box.max(first), box.min(second)},
                {box.max(first), box.max(second)}, {box.min(first), box.max(second)}};
        for (double[] corner : corners) {
            double[] xyz = new double[Direction.Axis.values().length];
            xyz[axis.ordinal()] = plane;
            xyz[first.ordinal()] = corner[0];
            xyz[second.ordinal()] = corner[1];
            consumer.addVertex(pose, (float) xyz[Direction.Axis.X.ordinal()], (float) xyz[Direction.Axis.Y.ordinal()],
                    (float) xyz[Direction.Axis.Z.ordinal()]).setColor(color);
        }
    }
}
