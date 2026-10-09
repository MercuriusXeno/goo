package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.ber.MeltMesh;
import com.mercuriusxeno.goo.client.ber.MeltMeshGoo;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.DrinkPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
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
 * Draws every Unmake drink: each block's lump flowing into its stream, and
 * the drink's streams flowing languidly down their fixed tree into the
 * drinker's glove, each path skinned solid in its block's own texture laid
 * at its own size and riding the flow, with the block's goo types roiling
 * over it through the vats' mingle shader in blotches that cover more of it
 * along the block's route but never all of it, the types sharing the
 * blotches by volume.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrinkRenderer {

    /** Sides about one ring of the stream's skin. */
    static final int SIDES = 10;
    /** How much further out each goo layer's skin stands than the one under it, so they never fight. */
    static final double LAYER_STEP = 0.008;
    /** The share of a stream's skin the goo covers by the hand, so the block's texture shows mingled the whole way. */
    static final float GOO_REACH = 0.45f;
    /** The most goo types layered over one stream; past three the blotches read as noise. */
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
     * @param light the light in front of the block
     * @param frame the frame
     * @param seed  the block's seed
     */
    private record Skin(List<DrinkStream.Ring> rings, int light, Frame frame, long seed) {
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
     * flowing while it drains and its own path skinned.
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
        drink.layout().place(states.keySet(), frame.glove());
        for (DrinkTree.Stream stream : DrinkTree.build(blocks, drink.layout(), frame.glove(), frame.ticks())) {
            submitStream(event, level, stream, states.get(stream.block().pos()), frame);
        }
    }

    /**
     * Submits one stream: while its block drains, its lump flowing into the
     * stream's entry, and the path from the entry on; once drained, the path
     * alone, its tail following in.
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
        int light = LevelRenderer.getLightCoords(level, BlockPos.containing(DrinkStream.pointAt(stream.path(),
                (DrinkStream.BLOCK_SPAN + HALF) / stream.path().length(), frame.ticks())));
        if (frame.ticks() < block.end()) {
            DrinkBody.Lump lump = new DrinkBody.Lump(stream.path(), progressOf(block, frame.ticks()),
                    DrinkStream.headAt(block.start(), frame.ticks()) - DrinkStream.BLOCK_SPAN);
            DrinkBodyRenderer.submit(event, level, state, goo, new DrinkBodyRenderer.Flowing(stream, lump,
                    frame.ticks(), frame.camera(), light));
        }
        List<DrinkStream.Ring> rings = DrinkTree.rings(stream, frame.ticks());
        if (rings.stream().anyMatch(ring -> ring.radius() > 0)) {
            submitSkins(event, new Skin(rings, light, frame, block.seed()), level, block.pos(), state, goo);
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
        submitBlockSkin(event, skin, level, pos, state);
        for (int layer = 0; layer < Math.min(goo.types().size(), MAX_LAYERS); layer++) {
            submitGooSkin(event, skin, goo, layer);
        }
    }

    /**
     * Submits the path's own skin, solid: the block's texture at its own
     * size, riding the flow, warped molten.
     *
     * @param event the custom geometry submit event
     * @param skin  the skin
     * @param level the client level
     * @param pos   the block
     * @param state the block the stream is
     */
    private static void submitBlockSkin(SubmitCustomGeometryEvent event, Skin skin, ClientLevel level, BlockPos pos,
                                        BlockState state) {
        BakedQuad quad = faceQuad(level, pos, state);
        if (quad == null) {
            return;
        }
        GooRenderUtil.UvRect sprite = MeltMesh.spriteOf(quad);
        int tint = MeltMesh.tintOf(state, level, pos, quad);
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooSubmitter.solidOnBlockAtlas(),
                (pose, consumer) -> {
                    RenderContext ctx = new RenderContext(pose, consumer, skin.light());
                    for (int index = 0; index + 1 < skin.rings().size(); index++) {
                        emitBand(ctx, skin, index, sprite, 0, tint);
                    }
                });
    }

    /**
     * Submits one goo type's skin over the path on the vats' mingle shader:
     * its sprite in roiling blotches whose share of the skin grows along the
     * block's route, each ring's band telling the shader how much.
     *
     * @param event the custom geometry submit event
     * @param skin  the skin
     * @param goo   the goo the block becomes
     * @param layer the goo type's index, largest first
     */
    private static void submitGooSkin(SubmitCustomGeometryEvent event, Skin skin, MingledGoo goo, int layer) {
        GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(goo.types().get(layer)));
        int tint = GooSubmitter.fluidTint(goo.types().get(layer));
        double lift = LAYER_STEP * (layer + 1);
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location()),
                (pose, consumer) -> emitGooBands(pose, consumer, skin, goo, layer, sprite, lift, tint));
    }

    private static void emitGooBands(PoseStack.Pose pose, VertexConsumer consumer, Skin skin, MingledGoo goo,
                                     int layer, GooRenderUtil.UvRect sprite, double lift, int tint) {
        List<DrinkStream.Ring> rings = skin.rings();
        for (int index = 0; index + 1 < rings.size(); index++) {
            float reach = (float) (rings.get(index).share() + rings.get(index + 1).share()) * HALF * GOO_REACH;
            RenderContext ctx = RenderContext.banded(pose, consumer, tint, bandOf(goo, layer, reach));
            emitBand(ctx, skin, index, sprite, lift, tint);
        }
    }

    /**
     * The band a goo type's layer draws with where the goo covers a share of
     * the skin: the layers stack over the block's texture so that each type
     * covers its volume ratio of that share.
     *
     * @param goo   the goo
     * @param layer the type's index, largest first
     * @param reach the share of the skin the goo covers there, 0 to 1
     * @return the band
     */
    static TypeBand bandOf(MingledGoo goo, int layer, float reach) {
        float before = layer == 0 ? 0f : goo.cumulative().get(layer - 1);
        float lo = 1f - reach * (1f - before);
        float hi = 1f - reach * (1f - goo.cumulative().get(layer));
        return new TypeBand(goo.types().get(layer), lo, hi, layer);
    }

    /**
     * Emits the band of skin between two rings: a quad for each side, wound
     * to face outward, its texture at its own size riding the flow.
     *
     * @param ctx    the render context
     * @param skin   the skin
     * @param index  the near ring's index
     * @param sprite the sprite laid on the skin
     * @param lift   how far the skin stands off the stream's radius
     * @param color  the colour of the band
     */
    private static void emitBand(RenderContext ctx, Skin skin, int index, GooRenderUtil.UvRect sprite, double lift,
                                 int color) {
        DrinkStream.Ring near = skin.rings().get(index);
        DrinkStream.Ring far = skin.rings().get(index + 1);
        if (near.radius() <= 0 && far.radius() <= 0) {
            return;
        }
        for (int side = 0; side < SIDES; side++) {
            double angle0 = TWO_PI * side / SIDES;
            double angle1 = TWO_PI * (side + 1) / SIDES;
            emitPoint(ctx, skin, near, angle0, sprite, lift, color);
            emitPoint(ctx, skin, near, angle1, sprite, lift, color);
            emitPoint(ctx, skin, far, angle1, sprite, lift, color);
            emitPoint(ctx, skin, far, angle0, sprite, lift, color);
        }
    }

    private static void emitPoint(RenderContext ctx, Skin skin, DrinkStream.Ring ring, double angle,
                                  GooRenderUtil.UvRect sprite, double lift, int color) {
        Vec3 out = ring.outAt(angle);
        double reach = ring.radius() * ring.reachAt(angle);
        Vec3 point = ring.center().add(out.scale(ring.radius() > 0 ? reach + lift : 0)).subtract(skin.frame().camera());
        float u = DrinkStream.moltenU(ring.material(), angle, skin.frame().ticks(), skin.seed());
        float v = DrinkStream.moltenV(ring.material(), angle, reach, skin.frame().ticks(), skin.seed());
        ctx.vertexColored(color, (float) point.x, (float) point.y, (float) point.z,
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
