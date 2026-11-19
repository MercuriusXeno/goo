package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.network.ClientAbilities;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.SprayerFlow;
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
 * type of the fitted canister that has a residual, and hovering a type fans the flow steps out from it; releasing
 * the key over a flow step keeps that type and flow on the sprayer and closes, and releasing it over nothing or
 * while the petals still move closes with the sprayer unchanged. The petals ease and paint as the glove's do.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision radial-selects-on-g-release
 * decision mid-animation-input-does-nothing
 */
public final class SprayerRadialScreen extends EasedWheelScreen {

    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int HOVERED_LABEL_COLOR = 0xFFFFFF00;

    private final SprayerWheel model;
    private final RadialWheel wheel;

    private SprayerRadialScreen(SprayerWheel model) {
        this.model = model;
        this.wheel = new RadialWheel(model.types().size(), type -> model.flows().size());
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
            PetalPainter.paint(graphics, canvas, PetalLook.LIVE.fluidFace(model.types().get(petal.type())), petal,
                    RadialWheelRenderer.faded(RadialWheelRenderer.computeOverlayTint(hovered, false),
                            petal.presence()));
            Component label = petal.isAbility()
                    ? Component.translatable(model.flows().get(petal.ability()).langKey())
                    : GooTypeNames.name(model.types().get(petal.type()));
            int[] tip = tipOf(canvas, petal);
            RadialWheelRenderer.outlinedText(graphics, font, label, tip[0], tip[1] - font.lineHeight / HALF,
                    RadialWheelRenderer.faded(hovered ? HOVERED_LABEL_COLOR : LABEL_COLOR, petal.presence()));
        }
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
            keep(model.types().get(pick.type()), model.flows().get(pick.ability()));
        }
        onClose();
        return true;
    }

    /**
     * Keeps a pick on the held sprayer here and on the server.
     *
     * @param type the picked goo type
     * @param flow the picked flow
     */
    private static void keep(ResourceKey<GooTypeDefinition> type, SprayerFlow flow) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack sprayer = mc.player == null ? ItemStack.EMPTY : SprayerItem.heldSprayer(mc.player);
        if (sprayer.isEmpty()) {
            return;
        }
        SprayerItem.keepSetting(sprayer, new SprayerSetting(Optional.of(type), flow));
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundCustomPayloadPacket(
                    new SprayerSelectPayload(GooTypes.id(type), flow)));
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
