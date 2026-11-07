package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The caster's side of every held sweep, Scry's, Glitter's and Magnet's, as
 * a sonar return: each live pulse's front leaves the caster at the steady
 * pace as a faint skin in the casting goo type's color, every wall open to
 * air that a front hits lights through walls at the hit and fades over two
 * seconds, so the cave hangs lit behind the fronts; fronts overlap, and a
 * front already out when the hold lets go runs on to its range.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeldSweepVisual {

    /** The most walls one pulse holds lit at once; past it that front lights no more. */
    static final int MOST_BLOCKS = 12_288;
    /** The front's skin alpha, low since it adds onto the whole view. */
    private static final int SPHERE_ALPHA = 20;
    /** A wall's alpha on the tick it is hit; it fades from there. */
    private static final int FACE_ALPHA = 110;
    /** How far a face quad stands off its block, so it never sinks into the face it marks. */
    private static final float FACE_LIFT = 0.002f;
    /** A pulse leaves from the caster's body, this far above where the caster stood. */
    private static final double BODY_CENTER = 0.9;

    private static final Map<Integer, HeldSweepPulse> PULSES = new LinkedHashMap<>();
    private static final Map<Vec3, EchoField> FIELDS = new HashMap<>();
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
        Vec3 center = payload.origin().add(0, BODY_CENTER, 0);
        HeldSweepPulse pulse = PULSES.computeIfAbsent(payload.pulse(), id -> new HeldSweepPulse(
                FIELDS.computeIfAbsent(center, EchoField::new), payload.range(),
                tint(payload.gooType(), ClientGooTypes::color)));
        pulse.heard(payload.radius(), level.getGameTime());
    }

    /** Drops every pulse, as a disconnect or a change of dimension does. */
    public static void clear() {
        PULSES.clear();
        FIELDS.clear();
        heardIn = null;
    }

    /**
     * How brightly a wall shows a while after a front hit it: whole at the
     * hit, falling to nothing over the linger.
     *
     * @param sinceHit ticks since the front hit it
     * @return the glow in [0, 1]
     */
    static float glowAt(double sinceHit) {
        return (float) Math.clamp(1 - sinceHit / HeldSweepPace.LINGER_TICKS, 0, 1);
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
     * Moves every front on, drops the finished pulses and the walls no
     * pulse leaves from any more, and draws what still shows.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
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
        advanceAll(PULSES, now, at -> level.getBlockState(at).isAir(),
                at -> level.getBlockState(at).getShape(level, at).toAabbs());
        FIELDS.values().removeIf(field -> PULSES.values().stream().noneMatch(pulse -> pulse.field() == field));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawFronts(pose, buffers, camera, now);
        drawWalls(pose, buffers, camera, now);
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

    private static void drawFronts(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                                   double now) {
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE);
        for (HeldSweepPulse pulse : PULSES.values()) {
            if (pulse.travelling(now)) {
                ColorSphere.emit(pose, consumer, pulse.field().center().subtract(camera),
                        (float) pulse.drawnRadius(now), ARGB.color(SPHERE_ALPHA, pulse.tintRgb()));
            }
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    private static void drawWalls(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                                  double now) {
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.SCRY_FACES_TYPE);
        for (WallGlow wall : wallsToDraw(PULSES.values(), now)) {
            int color = ARGB.color(Math.round(FACE_ALPHA * wall.glow()), wall.tintRgb());
            Vec3 corner = Vec3.atLowerCornerOf(wall.face().pos()).subtract(camera);
            for (AABB box : wall.face().boxes()) {
                for (Direction side : wall.face().sides()) {
                    emitBoxFace(pose, consumer, box.move(corner), side, color);
                }
            }
        }
        buffers.endBatch(GooRenderTypes.SCRY_FACES_TYPE);
    }

    /**
     * The walls a frame draws and how brightly: each wall once, at the
     * brightest glow any live front left on it, so a wall a later front hits
     * again lights afresh rather than stacking brighter with every pulse,
     * since the walls draw added onto the world.
     *
     * @param pulses the live pulses
     * @param now    the game time with its partial tick
     * @return each wall to draw with its glow and color
     */
    static List<WallGlow> wallsToDraw(Collection<HeldSweepPulse> pulses, double now) {
        Map<BlockPos, WallGlow> walls = new LinkedHashMap<>();
        for (HeldSweepPulse pulse : pulses) {
            for (HeldSweepPulse.LitFace lit : pulse.lit()) {
                walls.merge(lit.face().pos(), new WallGlow(lit.face(), glowAt(now - lit.hitAt()), pulse.tintRgb()),
                        (held, next) -> next.glow() > held.glow() ? next : held);
            }
        }
        return new ArrayList<>(walls.values());
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
