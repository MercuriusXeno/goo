package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.root.RootEvents;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.List;

/**
 * Draws the tendrils the vines root a mob by: a few strands coming out of
 * its legs and lower body, down to the ground beside its feet and along it
 * into a knot of vine on the root; as the mob strains off the root they lift
 * off the ground, straighten and thin toward the knot. They reach out as the
 * blob unpacks and withdraw as the vines fall once released.
 * vines-unpack-root-and-thorn
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class VineTendrils {

    /** Strands one rooted mob is held by. */
    static final int TENDRILS = 4;
    /** Where on the mob's height the strands come out of its body. */
    private static final double BODY_HEIGHT_SHARE = 0.3;
    /** How far out from the mob's middle the strands come out, as a share of its half-width: inside the model. */
    private static final double BODY_DEPTH_SHARE = 0.35;
    /** How far round the knot's middle the strands meet it. */
    static final double KNOT_RIM = 0.3;
    /** The knot's half-width at full size. */
    private static final double KNOT_HALF = 0.14;
    /** How far a strand lies off the ground, clear of the block beneath. */
    private static final double LIFT = 0.02;
    /** Half a slack strand's width, and the share it thins by at the leash. */
    private static final double HALF_WIDTH = 0.07;
    private static final double THIN_AT_LEASH = 0.5;
    /** A turn of the strands around the body, so they leave between the mob's legs. */
    private static final double FIRST_ANGLE = Math.PI / TENDRILS;
    private static final double HALF = 0.5;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);

    private VineTendrils() {
    }

    /**
     * Submits the tendrils of every rooted mob the level draws.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = mc.level.getGameTime() + partialTick;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        GooRenderUtil.UvRect uv = VineRibbon.spriteUv();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && living.hasData(GooAttachments.ROOTED)) {
                Rooted rooted = living.getData(GooAttachments.ROOTED);
                submitTendrils(event, living, rooted, new Strands(rooted.coverAt(time), partialTick, camera, uv));
            }
        }
    }

    /**
     * What every strand of one frame shares.
     *
     * @param grown       how far the strands have reached, 0 to 1
     * @param partialTick the partial tick
     * @param camera      the camera's world position
     * @param uv          the vine sprite's UV rect
     */
    private record Strands(float grown, float partialTick, Vec3 camera, GooRenderUtil.UvRect uv) {
    }

    /**
     * Where a rooted mob stands as its strands are drawn.
     *
     * @param feet   its feet, this frame
     * @param root   the root, lifted clear of the ground
     * @param radius how far out of its middle the strands come out
     * @param height how far up its body the strands come out
     */
    record Stance(Vec3 feet, Vec3 root, double radius, double height) {
    }

    /**
     * Submits one rooted mob's knot and strands, reached out as far as its vines cover it.
     *
     * @param event   the custom geometry submit event
     * @param living  the rooted mob
     * @param rooted  its vines
     * @param strands what every strand of the frame shares
     */
    private static void submitTendrils(SubmitCustomGeometryEvent event, LivingEntity living, Rooted rooted,
                                       Strands strands) {
        if (strands.grown() <= 0f) {
            return;
        }
        Stance stance = new Stance(living.getPosition(strands.partialTick()), rooted.anchor().add(0, LIFT, 0),
                living.getBbWidth() * HALF * BODY_DEPTH_SHARE, living.getBbHeight() * BODY_HEIGHT_SHARE);
        double strain = strainOf(stance.root(), stance.feet());
        double halfWidth = HALF_WIDTH * (1 - THIN_AT_LEASH * strain);
        int light = Minecraft.getInstance().getEntityRenderDispatcher()
                .getPackedLightCoords(living, strands.partialTick());
        int color = ARGB.opaque(VineTangleLayer.VINE_GREEN);
        Vec3 camera = strands.camera();
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooSubmitter.renderType(),
                (pose, consumer) -> {
                    RenderContext ctx = new RenderContext(pose, consumer, light, color);
                    VineRibbon.emitKnot(ctx, stance.root().subtract(camera), UP, EAST, SOUTH,
                            KNOT_HALF * strands.grown(), strands.uv());
                    for (int i = 0; i < TENDRILS; i++) {
                        List<Vec3> spine = strandSpine(stance, i, strain, strands.grown()).stream()
                                .map(point -> point.subtract(camera)).toList();
                        VineRibbon.emitCrossed(ctx, spine, halfWidth, strands.uv());
                    }
                });
    }

    /**
     * How hard a mob strains at its root: nothing standing on it, whole at the leash.
     *
     * @param root the root
     * @param feet where the mob stands
     * @return the strain, 0 to 1
     */
    static double strainOf(Vec3 root, Vec3 feet) {
        double dx = feet.x - root.x;
        double dz = feet.z - root.z;
        return Math.min(1, Math.sqrt(dx * dx + dz * dz) / RootEvents.LEASH_BLOCKS);
    }

    /**
     * The points one strand runs through: out of the mob's body, bending
     * down to the ground beside its feet and along it into the knot; as the
     * mob strains, the bend rises off the ground toward the straight line
     * from body to knot, whole at the leash.
     *
     * @param stance where the mob and its root stand
     * @param index  which strand
     * @param strain how hard the mob strains, 0 to 1
     * @param grown  how far the strand has reached out of the body, 0 to 1
     * @return the strand's world points, the first in the body
     */
    static List<Vec3> strandSpine(Stance stance, int index, double strain, float grown) {
        double angle = FIRST_ANGLE + index * Math.TAU / TENDRILS;
        Vec3 around = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        Vec3 body = stance.feet().add(around.scale(stance.radius())).add(0, stance.height(), 0);
        Vec3 knot = stance.root().add(around.scale(KNOT_RIM));
        Vec3 besideFeet = new Vec3(stance.feet().x, stance.root().y, stance.feet().z).add(around.scale(KNOT_RIM));
        Vec3 straight = body.add(knot).scale(HALF);
        return VineRibbon.curve(body, besideFeet.lerp(straight, strain), knot, grown);
    }
}
