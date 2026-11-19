package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.network.ClientAbilities;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.item.SprayerSetting;
import com.mercuriusxeno.goo.network.SprayerSelectPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/**
 * The sprayer's wheel, open while the glove menu key is held with the sprayer in hand: one petal for each goo
 * type of the fitted canister that has a residual, then one for each nozzle. Hovering a type fans the flow steps
 * out from it, and releasing the key over a step keeps that type and flow on the sprayer; hovering a nozzle fans
 * out the one petal that fits it, and releasing over that keeps the nozzle. Releasing over nothing or while the
 * petals still move closes with the sprayer unchanged. The petals ease and paint as the glove's do.
 * decision spray-item-takes-different-nozzles-doing-different-things
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision radial-selects-on-g-release
 * decision mid-animation-input-does-nothing
 */
public final class SprayerRadialScreen extends EasedWheelScreen {

    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int HOVERED_LABEL_COLOR = 0xFFFFFF00;
    /** The type id a pick naming no type sends, leaving the canister's most held sprayable type to spray. */
    private static final String NO_TYPE_PICKED = "";

    private final SprayerWheel model;
    private final RadialWheel wheel;

    private SprayerRadialScreen(SprayerWheel model) {
        this.model = model;
        this.wheel = new RadialWheel(model.types().size() + model.nozzles().size(),
                inner -> inner < model.types().size() ? model.flows().size() : 1);
    }

    /**
     * Opens the wheel over the held sprayer's canister; a canister holding no goo type that has a residual, or none,
     * opens nothing, as nothing could be picked.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        ClientAbilities synced = ClientAbilities.current();
        SprayerWheel model = SprayerWheel.of(CanisterItem.getFluidContent(
                SprayerItem.fittedCanister(player.getMainHandItem())),
                type -> synced.forType(type).stream().anyMatch(ability -> ability.badge() == AbilityBadge.RESIDUAL));
        if (!model.types().isEmpty()) {
            mc.setScreen(new SprayerRadialScreen(model));
        }
    }

    @Override
    RadialWheel wheel() {
        return wheel;
    }


    /**
     * Paints every petal at the frame's true partial tick, each in its goo type's fluid, the type's name on a type
     * petal and the step's name on a flow petal.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float frameDelta) {
        super.extractBackground(graphics, mouseX, mouseY, frameDelta);
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Canvas canvas = new Canvas(width / HALF, height / HALF, radius());
        for (RadialWheel.PetalArc petal : wheel.displayedLayout(partialTick)) {
            boolean hovered = petal.isAbility() ? petal.ability() == wheel.hoveredAbility()
                    : petal.type() == wheel.selectedType();
            PetalPainter.paint(graphics, canvas, PetalLook.LIVE.fluidFace(faceTypeOf(petal)), petal,
                    RadialWheelRenderer.faded(RadialWheelRenderer.computeOverlayTint(hovered, false),
                            petal.presence()));
            Component label = labelOf(petal);
            int[] tip = tipOf(canvas, petal);
            RadialWheelRenderer.outlinedText(graphics, font, label, tip[0], tip[1] - font.lineHeight / HALF,
                    RadialWheelRenderer.faded(hovered ? HOVERED_LABEL_COLOR : LABEL_COLOR, petal.presence()));
        }
    }

    private boolean isNozzle(int inner) {
        return inner >= model.types().size();
    }

    /**
     * The goo type a petal's fluid shows: its own type, or for a nozzle the canister's most held sprayable type.
     *
     * @param petal the petal
     * @return the type whose fluid fills it
     */
    private ResourceKey<GooTypeDefinition> faceTypeOf(RadialWheel.PetalArc petal) {
        return model.types().get(isNozzle(petal.type()) ? 0 : petal.type());
    }

    /**
     * The words a petal shows: a type's name, a flow step's, or a nozzle's.
     *
     * @param petal the petal
     * @return the words
     */
    private Component labelOf(RadialWheel.PetalArc petal) {
        if (isNozzle(petal.type())) {
            return Component.translatable(model.nozzles().get(petal.type() - model.types().size()).langKey());
        }
        return petal.isAbility() ? Component.translatable(model.flows().get(petal.ability()).langKey())
                : GooTypeNames.name(model.types().get(petal.type()));
    }

    private static int[] tipOf(Canvas canvas, RadialWheel.PetalArc petal) {
        PetalMask.Point tip = petal.shape().tipCenter();
        return new int[]{canvas.centerX() + (int) Math.round(tip.x() * canvas.radius()),
                canvas.centerY() + (int) Math.round(tip.y() * canvas.radius())};
    }

    /**
     * Reads the glove menu key's release, since an open screen takes the keyboard from the mapping.
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
        if (pick.selects()) {
            keep(pick);
        }
        onClose();
        return true;
    }

    /**
     * Keeps a pick on the held sprayer here and on the server: a type and flow step, keeping the nozzle, or a
     * nozzle, keeping the type and flow.
     *
     * @param pick the wheel's outcome
     */
    private void keep(RadialWheel.Outcome pick) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack sprayer = mc.player == null ? ItemStack.EMPTY : SprayerItem.heldSprayer(mc.player);
        if (sprayer.isEmpty()) {
            return;
        }
        SprayerSetting kept = SprayerItem.settingOf(sprayer);
        SprayerSetting picked = isNozzle(pick.type())
                ? new SprayerSetting(kept.pickedType(), kept.flow(),
                        model.nozzles().get(pick.type() - model.types().size()))
                : new SprayerSetting(Optional.of(model.types().get(pick.type())), model.flows().get(pick.ability()),
                        kept.nozzle());
        SprayerItem.keepSetting(sprayer, picked);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new SprayerSelectPayload(
                    picked.pickedType().map(GooTypes::id).orElse(NO_TYPE_PICKED), picked.flow(), picked.nozzle())));
        }
    }

    /**
     * Where the sprayer's wheel draws.
     *
     * @param centerX the wheel's center, x
     * @param centerY the wheel's center, y
     * @param radius  the wheel's outer radius
     */
    private record Canvas(int centerX, int centerY, int radius) implements PetalCanvas {
    }
}
