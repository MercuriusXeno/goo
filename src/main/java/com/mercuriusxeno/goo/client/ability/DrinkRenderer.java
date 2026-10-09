package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.ber.MeltMesh;
import com.mercuriusxeno.goo.client.ber.MeltMeshGoo;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws every Unmake drink: each block's own cube turning into its stream
 * where it stands, and the drink's streams flowing languidly down their tree
 * into the drinker's glove, each path skinned in its block's own texture
 * riding the flow and warped molten, tinting toward its goo's colour and
 * growing patches of its goo types by the mingle noise along the block's
 * whole route, mingled by their shares, until it is all goo as it enters.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrinkRenderer {

    /** The goo's alpha where a patch has fully formed over the stream. */
    static final int GOO_ALPHA = 0xEE;
    /** Sides about one ring of the stream's skin. */
    static final int SIDES = 10;
    /** How much further out each goo layer's skin stands than the one under it, so they never fight. */
    static final double LAYER_STEP = 0.008;
    /** How the liquid's place along the stream reads into the patches' field, in field blocks a block; slow, so they ride the flow. */
    static final float PATCH_ALONG = 0.2f;
    /** The most goo types layered over one stream; past three the patches read as noise. */
    private static final int MAX_LAYERS = 3;
    private static final float HALF = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;
    /** Where another player's glove hangs before their eyes, in blocks. */
    private static final double GLOVE_AHEAD = 0.5;
    /** How far right of another player's look their glove hangs, in blocks. */
    private static final double GLOVE_RIGHT = 0.35;
    /** How far below another player's eyes their glove hangs, in blocks. */
    private static final double GLOVE_BELOW = 0.45;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DrinkRenderer() {
    }

    /**
     * Submits every drink this frame.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double ticks = level.getGameTime() + partialTick;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        for (ClientDrinks.Drink drink : ClientDrinks.CLIENT.live(ticks)) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker != null) {
                submitDrink(event, level, drink, new Frame(gloveOf(mc, drinker, partialTick), camera, ticks));
            }
        }
    }

    /**
     * What a frame draws every block of a drink against.
     *
     * @param glove  the drinker's glove, in the world
     * @param camera the camera's world position
     * @param ticks  the game time including the partial tick
     */
    private record Frame(Vec3 glove, Vec3 camera, double ticks) {
    }

    /**
     * One path's skin to emit.
     *
     * @param rings the path's rings, start to end
     * @param light the light where the block stands
     * @param frame the frame
     * @param seed  the block's seed
     */
    private record Skin(List<DrinkStream.Ring> rings, int light, Frame frame, long seed) {
    }

    /**
     * The colour of a point of a skin.
     */
    @FunctionalInterface
    private interface Coloring {
        /**
         * @param ring  the ring the point is on
         * @param angle the point's angle about the ring
         * @return the point's ARGB colour
         */
        int colorAt(DrinkStream.Ring ring, double angle);
    }

    /**
     * Where the drinker's goo blob sits: the local player's as the glove
     * renderer last captured it in first person, and another's at their hand.
     *
     * @param mc          the client
     * @param drinker     the drinking player
     * @param partialTick the partial tick
     * @return the glove's world position
     */
    private static Vec3 gloveOf(Minecraft mc, Entity drinker, float partialTick) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (drinker == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return GloveAim.handPosition(camera);
        }
        Vec3 look = drinker.getViewVector(partialTick);
        Vec3 right = look.cross(UP);
        right = right.lengthSqr() > 0 ? right.normalize() : Vec3.ZERO;
        return drinker.getEyePosition(partialTick).add(look.scale(GLOVE_AHEAD)).add(right.scale(GLOVE_RIGHT))
                .subtract(0, GLOVE_BELOW, 0);
    }

    /**
     * Submits one drink: its blocks' tree of streams, each stream's block
     * turning while it drains and its own path skinned.
     *
     * @param event the custom geometry submit event
     * @param level the client level
     * @param drink the drink
     * @param frame the frame
     */
    private static void submitDrink(SubmitCustomGeometryEvent event, ClientLevel level, ClientDrinks.Drink drink,
                                    Frame frame) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        List<DrinkTree.Block> blocks = new ArrayList<>();
        for (DrinkPayload.Streaming streaming : drink.streaming()) {
            BlockState seen = level.getBlockEntity(streaming.pos()) instanceof MeltingBlockEntity melting
                    ? melting.original() : null;
            BlockState state = ClientDrinks.CLIENT.blockOf(streaming.pos(), seen);
            if (state != null) {
                states.put(streaming.pos(), state);
                blocks.add(new DrinkTree.Block(streaming.pos(), Vec3.atCenterOf(streaming.pos()),
                        DrinkTree.scaleOf(MeltMeshGoo.volumeOf(state)), streaming.start(), streaming.end()));
            }
        }
        for (DrinkTree.Stream stream : DrinkTree.build(blocks, frame.glove(), frame.ticks())) {
            submitStream(event, level, stream, states.get(stream.block().pos()), frame);
        }
    }

    /**
     * Submits one stream: while its block drains, the cube turning into the
     * stream over its own span of the path, and the path from there on; once
     * drained, the path alone, its tail following in.
     *
     * @param event  the custom geometry submit event
     * @param level  the client level
     * @param stream the stream
     * @param state  the block it is
     * @param frame  the frame
     */
    private static void submitStream(SubmitCustomGeometryEvent event, ClientLevel level, DrinkTree.Stream stream,
                                     BlockState state, Frame frame) {
        DrinkTree.Block block = stream.block();
        MingledGoo goo = MeltMeshGoo.of(state);
        boolean turning = frame.ticks() < block.end();
        if (turning) {
            DrinkMorphRenderer.submit(event, level, state, goo, new DrinkMorphRenderer.Turning(stream,
                    progressOf(block, frame.ticks()), frame.ticks(), frame.camera()));
        }
        List<DrinkStream.Ring> rings = DrinkTree.rings(stream,
                turning ? DrinkStream.BLOCK_SPAN / stream.path().length() : 0, frame.ticks());
        if (rings.stream().anyMatch(ring -> ring.radius() > 0)) {
            submitSkins(event, new Skin(rings, LevelRenderer.getLightCoords(level, block.pos()), frame,
                    block.seed()), level, block.pos(), state, goo);
        }
    }

    private static double progressOf(DrinkTree.Block block, double ticks) {
        return Math.clamp((ticks - block.start()) / Math.max(1, block.end() - block.start()), 0, 1);
    }

    /**
     * Submits a path's skins: the block's own, then each goo type's over it.
     *
     * @param event the custom geometry submit event
     * @param skin  the skin
     * @param level the client level
     * @param pos   the block
     * @param state the block the stream is
     * @param goo   the goo it becomes
     */
    private static void submitSkins(SubmitCustomGeometryEvent event, Skin skin, ClientLevel level, BlockPos pos,
                                    BlockState state, MingledGoo goo) {
        submitBlockSkin(event, skin, level, pos, state, goo);
        for (int layer = 0; layer < Math.min(goo.types().size(), MAX_LAYERS); layer++) {
            submitGooSkin(event, skin, new MeltMesh.GooLayer(goo.types().get(layer), layer, goo.share(layer)));
        }
    }

    /**
     * Submits the path's own skin: the block's texture riding the flow,
     * warped molten, tinting toward the goo's colour where the mingle has formed.
     *
     * @param event the custom geometry submit event
     * @param skin  the skin
     * @param level the client level
     * @param pos   the block
     * @param state the block the stream is
     * @param goo   the goo it becomes
     */
    private static void submitBlockSkin(SubmitCustomGeometryEvent event, Skin skin, ClientLevel level, BlockPos pos,
                                        BlockState state, MingledGoo goo) {
        BakedQuad quad = faceQuad(level, pos, state);
        if (quad == null) {
            return;
        }
        GooRenderUtil.UvRect sprite = MeltMesh.spriteOf(quad);
        int tint = MeltMesh.tintOf(state, level, pos, quad);
        MeltMesh.GooLayer base = goo.types().isEmpty() ? null
                : new MeltMesh.GooLayer(goo.types().getFirst(), 0, goo.share(0));
        int gooColor = base == null ? tint : ARGB.opaque(ClientGooTypes.color(base.type()));
        GooSubmitter.submitBody(event.getPoseStack(), event.getSubmitNodeCollector(), skin.light(),
                ctx -> emitSkin(ctx, skin, sprite, 0, (ring, angle) -> base == null ? tint
                        : ARGB.srgbLerp(DrinkMorphRenderer.TINT_BLEND * formed(base, ring, angle), tint, gooColor)));
    }

    /**
     * Submits one goo type's skin over the path: its sprite on the goo
     * surface shader, in patches that form along the route.
     *
     * @param event the custom geometry submit event
     * @param skin  the skin
     * @param layer the goo layer
     */
    private static void submitGooSkin(SubmitCustomGeometryEvent event, Skin skin, MeltMesh.GooLayer layer) {
        GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(layer.type()));
        int tint = GooSubmitter.fluidTint(layer.type());
        TypeBand whole = new TypeBand(layer.type(), 0f, 1f, layer.index());
        double lift = LAYER_STEP * (layer.index() + 1);
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location()),
                (pose, consumer) -> emitSkin(RenderContext.banded(pose, consumer, tint, whole), skin, sprite, lift,
                        (ring, angle) -> ARGB.color(Math.round(GOO_ALPHA * formed(layer, ring, angle)), tint)));
    }

    /**
     * How formed a goo layer is at a point of the stream: its patches form by
     * the share of the block's route the point stands at, in a field that
     * rides the flow.
     *
     * @param layer the goo layer
     * @param ring  the ring the point is on
     * @param angle the point's angle about the ring
     * @return how formed, 0 to 1
     */
    private static float formed(MeltMesh.GooLayer layer, DrinkStream.Ring ring, double angle) {
        float around = (float) Math.cos(angle) * HALF + HALF;
        float over = (float) Math.sin(angle) * HALF + HALF;
        return layer.opacityAt((float) ring.material() * PATCH_ALONG, around, over, (float) ring.share());
    }

    /**
     * Emits a skin over the path's rings: a quad between each pair of rings
     * for each side, wound to face outward, its texture riding the flow.
     *
     * @param ctx      the render context
     * @param skin     the skin
     * @param sprite   the sprite laid along the liquid
     * @param lift     how far the skin stands off the stream's radius
     * @param coloring the colour of each point
     */
    private static void emitSkin(RenderContext ctx, Skin skin, GooRenderUtil.UvRect sprite, double lift,
                                 Coloring coloring) {
        List<DrinkStream.Ring> rings = skin.rings();
        for (int index = 0; index + 1 < rings.size(); index++) {
            DrinkStream.Ring near = rings.get(index);
            DrinkStream.Ring far = rings.get(index + 1);
            if (near.radius() <= 0 && far.radius() <= 0) {
                continue;
            }
            for (int side = 0; side < SIDES; side++) {
                double angle0 = TWO_PI * side / SIDES;
                double angle1 = TWO_PI * (side + 1) / SIDES;
                emitPoint(ctx, skin, near, angle0, sprite, lift, coloring);
                emitPoint(ctx, skin, near, angle1, sprite, lift, coloring);
                emitPoint(ctx, skin, far, angle1, sprite, lift, coloring);
                emitPoint(ctx, skin, far, angle0, sprite, lift, coloring);
            }
        }
    }

    private static void emitPoint(RenderContext ctx, Skin skin, DrinkStream.Ring ring, double angle,
                                  GooRenderUtil.UvRect sprite, double lift, Coloring coloring) {
        Vec3 out = ring.outAt(angle);
        double radius = ring.radius() > 0 ? ring.radius() + lift : 0;
        Vec3 point = ring.center().add(out.scale(radius)).subtract(skin.frame().camera());
        float u = DrinkStream.moltenU(ring.material(), angle, skin.frame().ticks(), skin.seed());
        float v = DrinkStream.moltenV(ring.material(), angle, skin.frame().ticks(), skin.seed());
        ctx.vertexColored(coloring.colorAt(ring, angle), (float) point.x, (float) point.y, (float) point.z,
                sprite.u0() + (sprite.u1() - sprite.u0()) * u, sprite.v0() + (sprite.v1() - sprite.v0()) * v,
                (float) out.x, (float) out.y, (float) out.z);
    }

    /**
     * The quad the stream takes its texture from: one of the block's top, or
     * failing that any of its model.
     *
     * @param level the client level
     * @param pos   the block
     * @param state the block
     * @return the quad, or null for a block with no model
     */
    private static @Nullable BakedQuad faceQuad(ClientLevel level, BlockPos pos, BlockState state) {
        List<BakedQuad> quads = MeltMesh.quadsOf(state, level, pos);
        for (BakedQuad quad : quads) {
            if (quad.direction() == Direction.UP) {
                return quad;
            }
        }
        return quads.isEmpty() ? null : quads.getFirst();
    }
}
