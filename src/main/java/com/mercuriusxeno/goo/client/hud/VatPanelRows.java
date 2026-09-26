package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplies the vat HUD panel's rows: label, stack size, upgrade level, then
 * the stack's goo rows (decision one-panel-painter-takes-rows), then its water row.
 */
final class VatPanelRows {

    /** Stack size header color (gray). */
    private static final int STACK_COLOR = 0xFFAAAAAA;
    /** Stack size display prefix. */
    private static final String STACK_PREFIX = "Stack: ";

    private VatPanelRows() {
    }

    /**
     * Returns the rows for an aggregated vat stack.
     *
     * @param data the vat stack data
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(VatStackData data) {
        List<PanelRow> headers = new ArrayList<>();
        if (data.hasLabel()) {
            headers.add(PanelPainter.labelRow(data.label()));
        }
        if (data.stackSize() > 1) {
            headers.add(PanelRow.header(STACK_PREFIX + data.stackSize(), STACK_COLOR));
        }
        if (data.compression() > 0) {
            headers.add(PanelPainter.upgradeRow(data.compression()));
        }
        List<PanelRow> rows = PanelPainter.rows(headers, data.contents());
        // A vat holds water beside its goo (decision diagnose-then-fix-vat-hud-water-row).
        if (data.water() > 0) {
            rows.add(PanelPainter.waterRow(data.water()));
        }
        return rows;
    }
}
