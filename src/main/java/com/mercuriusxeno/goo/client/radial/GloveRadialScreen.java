package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.ClientKnownItems;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.ClientAbilities;
import com.mercuriusxeno.goo.client.network.OfferedAbility;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GloveSelectPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * The glove's one radial wheel, open while the glove menu key is held:
 * hovering or scrolling to a type replaces its petal with its abilities,
 * releasing the key over an ability writes it to the glove and closes, and
 * releasing it over nothing, over a locked ability, while the petals still
 * move, or pressing Escape, closes with the glove unchanged. A mouse click
 * does nothing here.
 * decision abilities-replace-the-hovered-type
 * decision radial-selects-on-g-release
 * decision mid-animation-input-does-nothing
 * decision locked-petal-stays-on-the-wheel
 */
public final class GloveRadialScreen extends Screen {

    private static final int HALF = 2;

    private final List<ResourceKey<GooTypeDefinition>> types;
    private final List<List<OfferedAbility>> abilities;
    private final Map<ResourceKey<GooTypeDefinition>, Integer> available;
    private final RadialWheel wheel;

    private GloveRadialScreen(Map<ResourceKey<GooTypeDefinition>, Integer> available) {
        super(Component.empty());
        this.types = GooTypes.order();
        ClientAbilities synced = ClientAbilities.current();
        KnownItems known = ClientKnownItems.current();
        this.abilities = types.stream().map(type -> synced.offeredForType(type, known)).toList();
        this.available = available;
        this.wheel = new RadialWheel(types.size(), type -> abilities.get(type).size());
    }

    /**
     * Opens the wheel, snapshotting the goo the player holds.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || GooTypes.order().isEmpty()) {
            return;
        }
        mc.setScreen(new GloveRadialScreen(GooSourceScanner.aggregateAvailable(player)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int radius() {
        return (int) RadialWheel.outerRadius(width, height);
    }

    @Override
    public void mouseMoved(double x, double y) {
        wheel.moveCursor(x - width / (double) HALF, y - height / (double) HALF, radius());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        wheel.scroll(scrollY);
        return true;
    }

    /**
     * Advances the petals' ease on the client tick.
     * decision petal-moves-animate
     */
    @Override
    public void tick() {
        super.tick();
        wheel.tick();
    }

    /**
     * Draws the wheel at the frame's true partial tick: the float a screen's
     * render receives is the frame's delta in ticks, not how far into the
     * tick the frame falls, and easing on it steps once a tick. A cursor on
     * a locked petal's item icon names that item beside it.
     * decision petal-moves-animate
     * decision locked-petal-lists-the-unlearned-items
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float frameDelta) {
        super.extractBackground(graphics, mouseX, mouseY, frameDelta);
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<RadialWheelRenderer.ItemIcon> sacrifice = RadialWheelRenderer.render(graphics, font,
                new RadialWheelRenderer.Frame(wheel, types, abilities, available,
                        width / HALF, height / HALF, radius(), PetalLook.LIVE, partialTick));
        RadialWheelRenderer.ItemIcon hovered = RadialWheelRenderer.itemUnder(sacrifice, mouseX, mouseY);
        if (hovered != null) {
            graphics.setTooltipForNextFrame(font, hovered.stack().getHoverName(), mouseX, mouseY);
        }
    }

    /**
     * Reads the glove menu key's release here, since an open screen takes
     * the keyboard from the mapping.
     * decision radial-selects-on-g-release
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
        GloveRadialKeyGate.release(pick, new GloveRadialKeyGate.ReleaseActions() {
            @Override
            public boolean isLocked(RadialWheel.Outcome hovered) {
                return abilities.get(hovered.type()).get(hovered.ability()).locked();
            }

            @Override
            public void selectHovered(RadialWheel.Outcome hovered) {
                selectAbility(types.get(hovered.type()), abilities.get(hovered.type()).get(hovered.ability()).ability());
            }

            @Override
            public void close() {
                onClose();
            }
        });
        return true;
    }

    private static void selectAbility(ResourceKey<GooTypeDefinition> type, ClientAbility ability) {
        ItemStack glove = findGloveStack();
        if (glove == null) {
            return;
        }
        GloveSelection selection = GloveSelection.ofAbility(type, ability.id());
        GooGloveItem.setSelection(glove, selection);
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(
                    new GloveSelectPayload(selection.gooTypeId(), selection.abilityId())));
        }
    }

    private static @Nullable ItemStack findGloveStack() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof GooGloveItem) {
                return held;
            }
        }
        return null;
    }
}
