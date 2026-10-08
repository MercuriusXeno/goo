package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SoupBall;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.ClientGooValues;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.ber.DissolveGlow;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.network.SoupPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws every Unmake soup: the ball of goo hovering before its player, each
 * block on the face dissolving as the crucible dissolves an item, and each
 * block's goo streaming into the ball in a snaking vortex. The ball grows as
 * the goo arrives and, held, rides the player's look, moving in or
 * compressing where something stands in its way.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SoupRenderer {

    /** The ball's alpha, nearly opaque goo. */
    static final int BALL_ALPHA = 0xEE;
    private static final long MODEL_SEED = 42L;
    private static final float WHOLE = 1f;
    /** The look a ball waiting where it hung is drawn along; it is round, so any will do. */
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);

    private SoupRenderer() {
    }

    /**
     * Submits every soup this frame.
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
        for (ClientSoups.Soup soup : ClientSoups.CLIENT.live(level.getGameTime())) {
            submitSoup(event, level, soup, partialTick);
        }
    }

    /**
     * One block streaming in, as this frame draws it.
     *
     * @param pos      the block
     * @param state    the block it stands in for
     * @param value    its goo value
     * @param progress how far its siphon has run, 0 to 1
     */
    private record Streaming(BlockPos pos, BlockState state, GooValue value, float progress) {
    }

    private static void submitSoup(SubmitCustomGeometryEvent event, ClientLevel level, ClientSoups.Soup soup,
                                   float partialTick) {
        double ticks = level.getGameTime() + partialTick;
        List<Streaming> streaming = streamingIn(level, soup, ticks);
        GooContents held = heldGoo(soup.drunk(), streaming);
        double radius = SoupBall.radius(held.totalVolume());
        Entity player = soup.held() ? level.getEntity(soup.playerId()) : null;
        Vec3 look = player == null ? FORWARD : player.getViewVector(partialTick);
        SoupBall.Placement placement = player == null ? new SoupBall.Placement(soup.ball(), 1)
                : SoupBall.place(level, player.getEyePosition(partialTick), look, radius);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        submitBall(event, placement, look, radius, held, camera);
        Vec3 ball = placement.center().subtract(camera);
        for (Streaming block : streaming) {
            submitDissolve(event, level, block, camera);
            SiphonStream.submit(event.getPoseStack(), event.getSubmitNodeCollector(),
                    Vec3.atCenterOf(block.pos()).subtract(camera), ball, block.progress(), ticks,
                    block.value().toGooContents());
        }
    }

    /**
     * The blocks streaming into a soup that this client can draw.
     *
     * @param level the client level
     * @param soup  the soup
     * @param ticks the game time including the partial tick
     * @return the blocks, each with the block it stands in for and how far it has run
     */
    private static List<Streaming> streamingIn(ClientLevel level, ClientSoups.Soup soup, double ticks) {
        List<Streaming> streaming = new ArrayList<>();
        for (SoupPayload.Streaming block : soup.streaming()) {
            Streaming drawn = streamingOf(level, block, ticks);
            if (drawn != null) {
                streaming.add(drawn);
            }
        }
        return streaming;
    }

    private static @Nullable Streaming streamingOf(ClientLevel level, SoupPayload.Streaming block, double ticks) {
        if (!(level.getBlockEntity(block.pos()) instanceof MeltingBlockEntity melting)) {
            return null;
        }
        BlockState state = melting.original();
        GooValue value = ClientGooValues.current().lookup(BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()));
        if (value == null || value.isEmpty()) {
            return null;
        }
        float progress = (float) Math.clamp((ticks - block.start()) / Math.max(1, block.end() - block.start()), 0, 1);
        return new Streaming(block.pos(), state, value, progress);
    }

    /**
     * The goo the ball holds this frame: what it has drunk, and each
     * streaming block's share as far as its siphon has run.
     *
     * @param drunk     the goo the soup has drunk
     * @param streaming the blocks streaming in
     * @return the goo the ball shows
     */
    private static GooContents heldGoo(GooContents drunk, List<Streaming> streaming) {
        Map<ResourceKey<GooTypeDefinition>, Integer> held = new HashMap<>(drunk.getAll());
        for (Streaming block : streaming) {
            block.value().getAll().forEach((type, amount) ->
                    held.merge(type, Math.round(amount * block.progress()), Integer::sum));
        }
        return new GooContents(held);
    }

    private static void submitBall(SubmitCustomGeometryEvent event, SoupBall.Placement placement, Vec3 look,
                                   double radius, GooContents goo, Vec3 camera) {
        if (goo.isEmpty()) {
            return;
        }
        Vec3 center = placement.center().subtract(camera);
        float depth = (float) placement.depth();
        float side = (float) (1 / Math.sqrt(depth));
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        poseStack.mulPose(new Quaternionf().rotationTo(0f, 0f, 1f, (float) look.x, (float) look.y, (float) look.z));
        poseStack.scale((float) radius * side, (float) radius * side, (float) radius * depth);
        GooBall.submit(poseStack, event.getSubmitNodeCollector(), goo, BALL_ALPHA);
        poseStack.popPose();
    }

    /**
     * Submits a streaming block dissolving through the crucible's dissolve,
     * its edge glowing in its goo's colors.
     *
     * @param event  the custom geometry submit event
     * @param level  the client level
     * @param block  the streaming block
     * @param camera the camera's world position
     */
    private static void submitDissolve(SubmitCustomGeometryEvent event, ClientLevel level, Streaming block,
                                       Vec3 camera) {
        List<BakedQuad> quads = quadsOf(level, block.pos(), block.state());
        if (quads.isEmpty()) {
            return;
        }
        DissolveGlow glow = DissolveGlow.of(block.progress(), block.value(), ClientGooTypes::color)
                .withSeed(Math.floorMod(block.pos().hashCode(), DissolveGlow.SEED_UNITS));
        int light = LevelRenderer.getLightCoords(level, block.pos());
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(block.pos().getX() - camera.x, block.pos().getY() - camera.y,
                block.pos().getZ() - camera.z);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, GooRenderTypes.crucibleDissolve(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location()),
                (pose, consumer) -> emitDissolve(new Dissolve(level, block, quads, glow, light), pose, consumer));
        poseStack.popPose();
    }

    /**
     * One block's dissolve to emit.
     *
     * @param level the client level
     * @param block the streaming block
     * @param quads its model's quads
     * @param glow  how far it has dissolved and the goo its edge glows in
     * @param light its light
     */
    private record Dissolve(ClientLevel level, Streaming block, List<BakedQuad> quads, DissolveGlow glow, int light) {
    }

    /**
     * Emits a block's quads once per glow layer, each layer carrying its share and color.
     *
     * @param dissolve the dissolve
     * @param pose     the pose
     * @param consumer the dissolve buffer
     */
    private static void emitDissolve(Dissolve dissolve, PoseStack.Pose pose, VertexConsumer consumer) {
        QuadInstance instance = new QuadInstance();
        for (DissolveGlow.Layer layer : dissolve.glow().layers()) {
            instance.setOverlayCoords(dissolve.glow().overlayCoords(layer));
            instance.setLightCoords(dissolve.glow().lightCoords(dissolve.light(), layer));
            for (BakedQuad quad : dissolve.quads()) {
                instance.setColor(tintOf(dissolve.level(), dissolve.block(), quad));
                consumer.putBakedQuad(pose, quad, instance);
            }
        }
    }

    private static List<BakedQuad> quadsOf(ClientLevel level, BlockPos pos, BlockState state) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)
                .collectParts(level, pos, state, RandomSource.create(MODEL_SEED), parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                quads.addAll(part.getQuads(side));
            }
            quads.addAll(part.getQuads(null));
        }
        return quads;
    }

    private static int tintOf(ClientLevel level, Streaming block, BakedQuad quad) {
        int index = quad.materialInfo().tintIndex();
        if (index < 0) {
            return GooRenderUtil.OPAQUE_WHITE;
        }
        @Nullable BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(block.state(), index);
        return source == null ? GooRenderUtil.OPAQUE_WHITE
                : ARGB.opaque(source.colorInWorld(block.state(), level, block.pos()));
    }
}
