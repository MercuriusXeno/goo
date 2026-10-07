package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.ClientGooValues;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ability.DissolvingBlocks;
import com.mercuriusxeno.goo.data.GooValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.Map;

/**
 * Draws the crucible's dissolve over each block an unmake is working: the
 * block's own model, a hair larger than the block, eaten away to the share
 * dissolved with its goo types glowing at the edge, as an item melts in
 * the crucible (decision dissolve-shader-on-item).
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DissolvingBlockRenderer {

    /** How far the dissolving shell stands beyond the block, so it never fights the block's faces. */
    private static final float SHELL_SCALE = 1.01f;
    private static final int WHITE_GLOW = 0xFFFFFF;

    private DissolvingBlockRenderer() {
    }

    /**
     * Submits the dissolve over every block an unmake is working.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Map<BlockPos, Float> dissolving = DissolvingBlocks.CLIENT.live(mc.level.getGameTime());
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        dissolving.forEach((pos, fraction) -> submitBlock(mc, mc.level, pos, fraction, camera, event));
    }

    /**
     * Submits one block's dissolving shell, when the block still stands and has an item model.
     *
     * @param mc       the client
     * @param level    the client level
     * @param pos      the dissolving block
     * @param fraction the share dissolved
     * @param camera   the frame's camera
     * @param event    the custom geometry submit event
     */
    private static void submitBlock(Minecraft mc, Level level, BlockPos pos, float fraction, CameraRenderState camera,
                                    SubmitCustomGeometryEvent event) {
        BlockState state = level.getBlockState(pos);
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (state.isAir() || stack.is(Items.AIR)) {
            return;
        }
        ItemStackRenderState model = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(model, stack, ItemDisplayContext.NONE, level, null, 0);
        if (model.isEmpty()) {
            return;
        }
        AABB box = model.getModelBoundingBox();
        float scale = SHELL_SCALE / (float) Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
        Vec3 fromCamera = Vec3.atCenterOf(pos).subtract(camera.pos);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(fromCamera.x, fromCamera.y, fromCamera.z);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-box.getCenter().x, -box.getCenter().y, -box.getCenter().z);
        DissolveGlow glow = glowOf(BuiltInRegistries.ITEM.getKey(stack.getItem()), fraction);
        model.submit(poseStack, new DissolvingItemCollector(event.getSubmitNodeCollector(), glow, null, 0,
                poseStack.last().pose()), GooSubmitter.fullbrightLight(), OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * The dissolving block's glow: one layer per goo type its item yields, or
     * white when the client holds no value for it.
     *
     * @param item     the block's item
     * @param fraction the share dissolved
     * @return the glow
     */
    private static DissolveGlow glowOf(Identifier item, float fraction) {
        GooValue value = ClientGooValues.current().lookup(item);
        if (value == null || value.isEmpty()) {
            return DissolveGlow.single(fraction, WHITE_GLOW);
        }
        return DissolveGlow.of(fraction, value, ClientGooTypes::color);
    }
}
