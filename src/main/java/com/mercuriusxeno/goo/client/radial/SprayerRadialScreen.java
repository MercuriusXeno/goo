package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.client.ClientKnownItems;
import com.mercuriusxeno.goo.client.network.ClientAbilities;
import com.mercuriusxeno.goo.client.network.OfferedAbility;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.item.SprayerSetting;
import com.mercuriusxeno.goo.network.SprayerSelectPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The sprayer's wheel, open while the glove menu key is held with the sprayer in hand: the glove's wheel over the
 * canister's sprayable goo types, each type petal opening onto its ability petals as the glove's do, and in the
 * hub's inert zone a three by three grid of small bordered buttons, the six nozzles over a divider and the three flow
 * steps under it. Letting go of the key over an ability petal sprays that goo and closes; clicking a button sets that
 * nozzle or flow and leaves the wheel open; letting go anywhere else, a button included, closes with nothing picked.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 * decision radial-selects-on-g-release
 * operator ruling 2026-11-19: the sprayer is another glove whose type petals open onto its abilities, with the
 * nozzle and flow buttons in the inert zone: three by three, aligned, a tight divider, small bordered buttons that
 * take a click alone
 */
public final class SprayerRadialScreen extends EasedWheelScreen {

    /** The screen gap between neighbouring buttons' centers, across and down. */
    private static final int STEP = 16;
    /** A button's side, its border included, and the icon drawn inside it. */
    private static final int BUTTON = 14;
    private static final int ICON = 12;
    /** How near the cursor must come to a button's center to rest on it: half its side. */
    private static final int BUTTON_REACH = BUTTON / HALF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BORDER = 0xC0D8D8D8;
    private static final int HOVERED_BORDER = 0xFFFFF29A;
    private static final int FACE = 0x80303030;
    /** The face of the button holding the current nozzle or flow. */
    private static final int CURRENT_FACE = 0xA0707070;
    private static final int DIVIDER = 0x80FFFFFF;
    /** The step down the hovered button's name sits at, under the flow steps. */
    private static final double NAME_ROW = 2.2;
    private static final String ICON_FOLDER = "textures/gui/sprayer/";
    private static final String FLOW_ICON = "flow_";
    private static final String NOZZLE_ICON = "nozzle_";
    private static final String PNG = ".png";
    /** The type id a pick naming no type sends, leaving the canister's most held sprayable type to spray. */
    private static final String NO_TYPE_PICKED = "";

    private final SprayerWheel model;
    private final List<List<OfferedAbility>> abilities;
    private final Map<ResourceKey<GooTypeDefinition>, Integer> available;
    private final RadialWheel wheel;
    private SprayerSetting kept;
    private SprayerWheel.@Nullable RingPetal hoveredButton;

    private SprayerRadialScreen(SprayerWheel model, List<List<OfferedAbility>> abilities,
                                Map<ResourceKey<GooTypeDefinition>, Integer> available, SprayerSetting kept) {
        this.model = model;
        this.abilities = abilities;
        this.available = available;
        this.kept = kept;
        this.wheel = new RadialWheel(model.types().size(), type -> abilities.get(type).size());
    }

    /**
     * Opens the wheel over the held sprayer: its canister's goo types that have a residual, each opening onto its
     * residual, beside the nozzle and flow buttons, which an empty sprayer sets too.
     * operator report 2026-11-18: an empty sprayer opened no wheel
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack sprayer = SprayerItem.heldSprayer(player);
        ClientAbilities synced = ClientAbilities.current();
        CanisterFluidContent content = CanisterItem.getFluidContent(SprayerItem.fittedCanister(sprayer));
        SprayerWheel model = SprayerWheel.of(content,
                type -> synced.forType(type).stream().anyMatch(ability -> ability.badge() == AbilityBadge.RESIDUAL));
        List<List<OfferedAbility>> abilities = model.types().stream()
                .map(type -> synced.offeredForType(type, ClientKnownItems.current()).stream()
                        .filter(offered -> offered.ability().badge() == AbilityBadge.RESIDUAL).toList())
                .toList();
        mc.gui.setScreen(new SprayerRadialScreen(model, abilities, content.gooVolumes(), SprayerItem.settingOf(sprayer)));
    }

    @Override
    RadialWheel wheel() {
        return wheel;
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x, y);
        hoveredButton = buttonAt(x, y);
    }

    private SprayerWheel.@Nullable RingPetal buttonAt(double x, double y) {
        for (SprayerWheel.RingPetal button : model.ring()) {
            int[] at = buttonCenter(button);
            if (button.group() != SprayerWheel.Group.GOO && Math.abs(x - at[0]) <= BUTTON_REACH
                    && Math.abs(y - at[1]) <= BUTTON_REACH) {
                return button;
            }
        }
        return null;
    }

    /**
     * Draws the glove's wheel over the sprayer's goo types and their abilities at the frame's true partial tick, then
     * the nozzle and flow buttons in the hub over it.
     * decision petal-moves-animate
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float frameDelta) {
        super.extractBackground(graphics, mouseX, mouseY, frameDelta);
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = minecraft.level == null ? 0f : minecraft.level.getGameTime() + partialTick;
        RadialWheelRenderer.Hoverables drawn = RadialWheelRenderer.render(graphics, font,
                new RadialWheelRenderer.Frame(wheel, model.types(), abilities, available, width / HALF, height / HALF,
                        radius(), PetalLook.LIVE, partialTick, Map.of(), gameTime,
                        reagents -> Integer.MAX_VALUE, HeldEffects.NONE, OptionalDouble.empty()));
        RadialWheelRenderer.nameWhatTheCursorRestsOn(graphics, font, drawn, mouseX, mouseY);
        paintButtons(graphics);
    }

    private void paintButtons(GuiGraphicsExtractor graphics) {
        for (SprayerWheel.RingPetal at : model.ring()) {
            if (at.group() != SprayerWheel.Group.GOO) {
                paintButton(graphics, at);
            }
        }
        int centerX = width / HALF;
        int dividerY = height / HALF + (int) Math.round(model.dividerRow() * STEP);
        int dividerHalf = model.flows().size() * STEP / HALF;
        graphics.fill(centerX - dividerHalf, dividerY, centerX + dividerHalf, dividerY + 1, DIVIDER);
        if (hoveredButton != null) {
            RadialWheelRenderer.outlinedText(graphics, font, nameOf(hoveredButton), centerX,
                    height / HALF + (int) Math.round(NAME_ROW * STEP) - font.lineHeight / HALF, WHITE);
        }
    }

    private void paintButton(GuiGraphicsExtractor graphics, SprayerWheel.RingPetal at) {
        int[] center = buttonCenter(at);
        int left = center[0] - BUTTON / HALF;
        int top = center[1] - BUTTON / HALF;
        graphics.fill(left, top, left + BUTTON, top + BUTTON, at.equals(hoveredButton) ? HOVERED_BORDER : BORDER);
        graphics.fill(left + 1, top + 1, left + BUTTON - 1, top + BUTTON - 1, isCurrent(at) ? CURRENT_FACE : FACE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, iconOf(at), center[0] - ICON / HALF, center[1] - ICON / HALF,
                0.0f, 0.0f, ICON, ICON, ICON, ICON, WHITE);
    }

    /**
     * Where a button's center sits on screen: its place on the hub's grid.
     *
     * @param button a nozzle or flow button
     * @return the button's center
     */
    private int[] buttonCenter(SprayerWheel.RingPetal button) {
        double[] offset = model.hubOffsetOf(button);
        return new int[]{width / HALF + (int) Math.round(offset[0] * STEP),
                height / HALF + (int) Math.round(offset[1] * STEP)};
    }

    private boolean isCurrent(SprayerWheel.RingPetal at) {
        return at.group() == SprayerWheel.Group.FLOW ? kept.flow() == model.flows().get(at.index())
                : kept.nozzle() == model.nozzles().get(at.index());
    }

    private Identifier iconOf(SprayerWheel.RingPetal at) {
        String name = at.group() == SprayerWheel.Group.FLOW
                ? FLOW_ICON + model.flows().get(at.index()).getSerializedName()
                : NOZZLE_ICON + model.nozzles().get(at.index()).getSerializedName();
        return Identifier.fromNamespaceAndPath(Goo.MODID, ICON_FOLDER + name + PNG);
    }

    private Component nameOf(SprayerWheel.RingPetal at) {
        return Component.translatable(at.group() == SprayerWheel.Group.FLOW
                ? model.flows().get(at.index()).langKey() : model.nozzles().get(at.index()).langKey());
    }

    /**
     * Sets the clicked button's nozzle or flow and leaves the wheel open for the next.
     *
     * @param event       the click
     * @param doubleClick whether it is a double click
     * @return true where a button was clicked
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (hoveredButton == null) {
            return super.mouseClicked(event, doubleClick);
        }
        keep(model.picked(kept, hoveredButton));
        return true;
    }

    /**
     * Reads the glove menu key's release, since an open screen takes the keyboard from the mapping: over an ability
     * petal, once the petals have landed, the sprayer sprays that goo; anywhere else nothing is picked.
     * decision mid-animation-input-does-nothing
     *
     * @param event the key release event
     * @return true if the event was handled
     */
    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!GloveRadialKey.MAPPING.matches(event)) {
            return super.keyReleased(event);
        }
        RadialWheel.Outcome pick = GloveRadialKeyGate.settledPick(wheel.isAnimating(), wheel.click());
        if (pick.selects() && !abilities.get(pick.type()).get(pick.ability()).refused()) {
            keep(new SprayerSetting(Optional.of(model.types().get(pick.type())), kept.flow(), kept.nozzle()));
        }
        onClose();
        return true;
    }

    /**
     * Keeps a pick on the held sprayer here and on the server.
     *
     * @param picked the new setting
     */
    private void keep(SprayerSetting picked) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack sprayer = mc.player == null ? ItemStack.EMPTY : SprayerItem.heldSprayer(mc.player);
        if (sprayer.isEmpty()) {
            return;
        }
        kept = picked;
        SprayerItem.keepSetting(sprayer, picked);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new SprayerSelectPayload(
                    picked.pickedType().map(GooTypes::id).orElse(NO_TYPE_PICKED), picked.flow(), picked.nozzle())));
        }
    }
}
