package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooGloveItem;
import net.minecraft.client.Camera;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the held glove says about aiming: the goo type it throws, the
 * targeting mode its selected ability asks for, and where the hand holding
 * it sits (decision render-context-is-the-one-emitter).
 */
public final class GloveAim {

    private GloveAim() {
    }

    /**
     * Resolves the targeting hint from the player's glove ability selection.
     *
     * @param player the local player
     * @return the targeting hint
     */
    public static TargetingHint targetingHint(Player player) {
        GloveSelection sel = readGloveSelection(player);
        if (sel == null || !sel.hasAbility()) {
            return TargetingHint.NONE;
        }
        ResourceKey<GooTypeDefinition> type = sel.getGooType();
        if (type == null) {
            return TargetingHint.NONE;
        }
        return hintFromAbility(type, sel);
    }

    /**
     * Reads the selection off the glove in either hand, main hand first.
     *
     * @param player the local player
     * @return the selection, or null when neither hand holds a glove
     */
    private static @Nullable GloveSelection readGloveSelection(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelection(main);
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelection(off);
        }
        return null;
    }

    /**
     * The hint the selected ability's tags name.
     *
     * @param type the selected goo type
     * @param sel  the glove selection
     * @return ENTITY for an entity-tagged ability, BLOCK for any other, NONE when unsynced
     */
    private static TargetingHint hintFromAbility(ResourceKey<GooTypeDefinition> type, GloveSelection sel) {
        for (ClientAbility ca : AbilitySyncHandler.getAbilitiesForType(type)) {
            if (ca.id() != null && ca.id().toString().equals(sel.abilityId())) {
                return ca.hasTag(AbilityTags.ENTITY) ? TargetingHint.ENTITY : TargetingHint.BLOCK;
            }
        }
        return TargetingHint.NONE;
    }

    /**
     * The goo type the held glove throws, main hand first, or null when no
     * glove holds a selection or the player carries none of that goo.
     *
     * @param player the local player
     * @return the selected type, or null
     */
    public static @Nullable ResourceKey<GooTypeDefinition> selectedGooType(Player player) {
        ResourceKey<GooTypeDefinition> type = selectedTypeIn(player.getMainHandItem());
        if (type == null) {
            type = selectedTypeIn(player.getOffhandItem());
        }
        return type != null && GloveUseTracker.isSelectedTypeAvailable() ? type : null;
    }

    /**
     * The selected type if the stack is a glove with a selection.
     *
     * @param stack the item stack
     * @return the selected type, or null
     */
    private static @Nullable ResourceKey<GooTypeDefinition> selectedTypeIn(ItemStack stack) {
        if (stack.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(stack);
        }
        return null;
    }

    /**
     * Returns the world-space arc origin from the blob center captured
     * during item rendering, the last capture unconditionally. Before the
     * first capture, the camera position stands in until the next frame.
     *
     * @param camera the render camera
     * @return the world-space hand position
     */
    public static Vec3 handPosition(Camera camera) {
        Vec3 captured = GloveSpecialRenderer.getLastBlobCenterCamRel();
        if (captured == null) {
            return camera.position();
        }
        return camera.position().add(captured);
    }
}
