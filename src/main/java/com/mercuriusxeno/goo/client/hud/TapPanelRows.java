package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.tap.TapDripGrade;
import java.util.List;
import java.util.Optional;

/**
 * Supplies the tap valve panel's one row: the rate the valve runs at, or off
 * when it is closed (decision valve-panel-reads-rate).
 */
final class TapPanelRows {

    /** Rate text color while the valve runs (white). */
    private static final int OPEN_COLOR = 0xFFFFFFFF;
    /** Rate text color while the valve is closed (gray). */
    private static final int CLOSED_COLOR = 0xFFAAAAAA;

    private TapPanelRows() {
    }

    /**
     * @param grade the grade the valve runs at, or empty when it is closed
     * @return the panel's one row
     */
    static List<PanelRow> rows(Optional<TapDripGrade> grade) {
        return List.of(grade.map(open -> PanelRow.header(open.rateLabel(), OPEN_COLOR))
                .orElseGet(() -> PanelRow.header(TapDripGrade.CLOSED_LABEL, CLOSED_COLOR)));
    }
}
