package com.comp1786.coursework.eventhub.admin;

import androidx.annotation.StringRes;

/**
 * Session types listed in the coursework brief.
 */
public enum SessionType {

    WORKSHOP(R.string.session_type_workshop),
    PRESENTATION(R.string.session_type_presentation),
    PANEL_DISCUSSION(R.string.session_type_panel_discussion),
    NETWORKING(R.string.session_type_networking),
    KEYNOTE(R.string.session_type_keynote),
    OTHER(R.string.session_type_other);

    @StringRes
    private final int labelRes;

    SessionType(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }
}
