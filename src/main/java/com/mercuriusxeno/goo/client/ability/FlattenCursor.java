package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.FlattenStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Flatten's cursor: while right click holds a flatten channel, rock's dust
 * disc lies on the block face the crosshair rests on, the block the next
 * held tick breaks.
 * decision flatten-disc-cursor-breaks-above-the-plane
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FlattenCursor {

    private FlattenCursor() {
    }

    /**
     * Draws the disc after the translucent blocks while a flatten hold runs
     * and the crosshair rests on a block.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !showsCursor(selectedAbility(player), GloveUseTracker.showsArea())) {
            return;
        }
        BlockHitResult hit = aimedFace(mc);
        if (hit == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BurnoutFrame frame = new BurnoutFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera().position(), mc.level.getGameTime() + partialTick);
        RockExplosionVisual.INSTANCE.renderCursor(frame, hit.getBlockPos().relative(hit.getDirection()),
                hit.getDirection());
    }

    /**
     * The block face the crosshair rests on.
     *
     * @param mc the client
     * @return the hit, or null where the crosshair rests on no block
     */
    private static @Nullable BlockHitResult aimedFace(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /**
     * The synced copy of the ability the player's glove has selected.
     *
     * @param player the local player
     * @return the ability, or null with no glove or no synced copy
     */
    private static @Nullable ClientAbility selectedAbility(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    /**
     * Whether the cursor shows: the selected ability flattens and right click holds it.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the cursor shows
     */
    static boolean showsCursor(@Nullable ClientAbility ability, boolean useHeld) {
        return useHeld && ability != null && ability.behaviors().stream().anyMatch(FlattenStep.class::isInstance);
    }
}
