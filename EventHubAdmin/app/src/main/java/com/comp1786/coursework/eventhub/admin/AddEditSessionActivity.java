package com.comp1786.coursework.eventhub.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * The form for adding or editing a session, with times only within its event.
 */
public class AddEditSessionActivity extends AppCompatActivity {

    /** Extra with the event the session belongs to. */
    public static final String EXTRA_EVENT = "event";
    /** Extra with the session to edit. */
    public static final String EXTRA_SESSION = "session";

    private static final String STATE_TYPE = "type";
    private static final String STATE_START_TIME = "start_time";
    private static final String STATE_END_TIME = "end_time";
    private static final String STATE_ORIGINAL_TEXTS = "original_texts";
    private static final String STATE_DISCARD_SHOWN = "discard_shown";

    // A new session lasts an hour, or until the event ends if that comes first.
    private static final Duration DEFAULT_LENGTH = Duration.ofHours(1);

    private MaterialToolbar toolbar;
    private View sheet;
    private TextView eventText;
    private TextInputLayout nameLayout;
    private TextInputLayout descriptionLayout;
    private TextInputLayout startTimeLayout;
    private TextInputLayout endTimeLayout;
    private TextInputLayout speakerLayout;
    private TextInputLayout roomLayout;
    private TextInputLayout typeLayout;
    private EditText nameInput;
    private EditText descriptionInput;
    private DropdownField startTimeInput;
    private DropdownField endTimeInput;
    private EditText speakerInput;
    private EditText roomInput;
    private DropdownField typeInput;
    private Button saveButton;

    private EventDatabase database;
    private Event event;
    @Nullable
    private Session sessionToEdit;
    @Nullable
    private SessionType selectedType;
    @Nullable
    private LocalTime selectedStartTime;
    // What each field showed when the form opened, to tell whether anything changed.
    private String[] originalTexts;
    private AlertDialog discardDialog;

    // Enabled only once a field changes, so an unchanged form keeps the system back animation.
    private final OnBackPressedCallback discardCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            showDiscardDialog();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_edit_session);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            // Above the keyboard when it shows, otherwise the sheet runs under the navigation bar.
            v.setPadding(bars.left, bars.top, bars.right, keyboard);
            sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(),
                    sheet.getPaddingRight(), getResources().getDimensionPixelSize(
                            R.dimen.screen_padding) + (keyboard > 0 ? 0 : bars.bottom));
            return insets;
        });

        bindViews();
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, discardCallback);
        database = new EventDatabase(this);
        event = getIntent().getSerializableExtra(EXTRA_EVENT, Event.class);
        sessionToEdit = getIntent().getSerializableExtra(EXTRA_SESSION, Session.class);
        eventText.setText(getString(R.string.session_event, event.getName(),
                EventTimes.format(event.getStartTime()), EventTimes.format(event.getEndTime())));
        buildTypeMenu();
        buildTimeMenus();
        if (sessionToEdit != null) {
            setTitle(R.string.title_edit_session);
            saveButton.setText(R.string.button_save_changes);
            if (savedInstanceState == null) {
                fillForm(sessionToEdit);
            }
        }
        // The fields restore their text after onCreate, so the first texts are saved instead.
        originalTexts = savedInstanceState == null
                ? formTexts() : savedInstanceState.getStringArray(STATE_ORIGINAL_TEXTS);
        saveButton.setOnClickListener(v -> save());
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Added after the views restore their text, so restoring is not treated as an edit.
        for (TextInputLayout layout : allLayouts()) {
            layout.getEditText().addTextChangedListener(new InputWatcher(layout));
        }
        discardCallback.setEnabled(hasChanges());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedType != null) {
            outState.putString(STATE_TYPE, selectedType.name());
        }
        outState.putString(STATE_START_TIME, textOf(startTimeInput));
        outState.putString(STATE_END_TIME, textOf(endTimeInput));
        outState.putStringArray(STATE_ORIGINAL_TEXTS, originalTexts);
        outState.putBoolean(STATE_DISCARD_SHOWN,
                discardDialog != null && discardDialog.isShowing());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String type = savedInstanceState.getString(STATE_TYPE);
        if (type != null) {
            selectedType = SessionType.valueOf(type);
            // Set without filtering, which would leave only this type in the menu.
            typeInput.setText(getString(selectedType.getLabelRes()), false);
        }
        // The times are set the same way, after the end list is rebuilt for the start.
        String start = savedInstanceState.getString(STATE_START_TIME, "");
        startTimeInput.setText(start, false);
        selectedStartTime = EventTimes.parse(start);
        buildEndMenu(selectedStartTime);
        endTimeInput.setText(savedInstanceState.getString(STATE_END_TIME, ""), false);
        if (savedInstanceState.getBoolean(STATE_DISCARD_SHOWN)) {
            showDiscardDialog();
        }
    }

    @Override
    protected void onDestroy() {
        if (discardDialog != null) {
            discardDialog.dismiss();
        }
        database.close();
        super.onDestroy();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        sheet = findViewById(R.id.sheet);
        eventText = findViewById(R.id.event_text);
        nameLayout = findViewById(R.id.name_layout);
        descriptionLayout = findViewById(R.id.description_layout);
        startTimeLayout = findViewById(R.id.start_time_layout);
        endTimeLayout = findViewById(R.id.end_time_layout);
        speakerLayout = findViewById(R.id.speaker_layout);
        roomLayout = findViewById(R.id.room_layout);
        typeLayout = findViewById(R.id.type_layout);
        nameInput = findViewById(R.id.name_input);
        descriptionInput = findViewById(R.id.description_input);
        startTimeInput = findViewById(R.id.start_time_input);
        endTimeInput = findViewById(R.id.end_time_input);
        speakerInput = findViewById(R.id.speaker_input);
        roomInput = findViewById(R.id.room_input);
        typeInput = findViewById(R.id.type_input);
        saveButton = findViewById(R.id.save_button);
    }

    /** Returns every field in the order of the form, so focus goes to the first error. */
    private TextInputLayout[] allLayouts() {
        return new TextInputLayout[] {nameLayout, descriptionLayout, startTimeLayout,
                endTimeLayout, speakerLayout, roomLayout, typeLayout};
    }

    private void buildTypeMenu() {
        SessionType[] types = SessionType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = getString(types[i].getLabelRes());
        }
        // Simple items keep the full list after a type is picked.
        typeInput.setSimpleItems(labels);
        typeInput.setOnItemClickListener((parent, view, position, id) ->
                selectedType = types[position]);
    }

    /** Offers start times from the event's start to a slot before its end. */
    private void buildTimeMenus() {
        List<LocalTime> startTimes = EventTimes.slotsBetween(
                event.getStartTime(), EventTimes.previousSlot(event.getEndTime()));
        startTimeInput.setSimpleItems(EventTimes.format(startTimes));
        startTimeInput.setOnItemClickListener((parent, view, position, id) ->
                changeStartTime(startTimes.get(position)));
        buildEndMenu(null);
    }

    /** Offers end times after the start up to the event's end, or after its start until then. */
    private void buildEndMenu(@Nullable LocalTime start) {
        LocalTime from = start != null ? start : event.getStartTime();
        endTimeInput.setSimpleItems(EventTimes.format(
                EventTimes.slotsBetween(EventTimes.nextSlot(from), event.getEndTime())));
        endTimeInput.setPreferredItem(EventTimes.format(defaultEnd(from)));
    }

    /** Moves the end with a new start so the length stays, if the session still fits. */
    private void changeStartTime(LocalTime start) {
        LocalTime end = EventTimes.parse(textOf(endTimeInput));
        if (end != null && selectedStartTime != null) {
            end = start.plusMinutes(EventTimes.minutesBetween(selectedStartTime, end));
        }
        if (end == null || end.equals(start) || !EventTimes.isWithin(
                start, end, event.getStartTime(), event.getEndTime())) {
            end = defaultEnd(start);
        }
        selectedStartTime = start;
        buildEndMenu(start);
        endTimeInput.setText(EventTimes.format(end), false);
    }

    /** Returns an hour after the start, or the event's end if that comes first. */
    private LocalTime defaultEnd(LocalTime start) {
        LocalTime hourLater = start.plus(DEFAULT_LENGTH);
        return EventTimes.isWithin(start, hourLater, event.getStartTime(), event.getEndTime())
                ? hourLater : event.getEndTime();
    }

    /** Fills the form with a session's values, so they can be edited. */
    private void fillForm(Session session) {
        nameInput.setText(session.getName());
        descriptionInput.setText(session.getDescription());
        selectedStartTime = session.getStartTime();
        startTimeInput.setText(EventTimes.format(selectedStartTime), false);
        buildEndMenu(selectedStartTime);
        endTimeInput.setText(EventTimes.format(session.getEndTime()), false);
        speakerInput.setText(session.getSpeaker());
        roomInput.setText(session.getRoom());
        selectedType = session.getType();
        typeInput.setText(getString(selectedType.getLabelRes()), false);
    }

    /** Returns the trimmed text of each field, in the order of allLayouts(). */
    private String[] formTexts() {
        TextInputLayout[] layouts = allLayouts();
        String[] texts = new String[layouts.length];
        for (int i = 0; i < layouts.length; i++) {
            texts[i] = textOf(layouts[i].getEditText()).trim();
        }
        return texts;
    }

    private boolean hasChanges() {
        return !Arrays.equals(formTexts(), originalTexts);
    }

    private void showDiscardDialog() {
        boolean editing = sessionToEdit != null;
        discardDialog = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_EventHubAdmin_DestructiveDialog)
                .setTitle(editing ? R.string.discard_changes_title : R.string.discard_session_title)
                .setMessage(editing ? R.string.discard_changes_message : R.string.discard_message)
                .setNegativeButton(R.string.button_keep_editing, null)
                .setPositiveButton(R.string.button_discard, (dialog, which) -> finish())
                .show();
    }

    private void save() {
        EventValidator.Result<String> name = EventValidator.validateText(textOf(nameInput));
        EventValidator.Result<LocalTime> startTime =
                EventValidator.validateTime(textOf(startTimeInput));
        EventValidator.Result<LocalTime> endTime =
                EventValidator.validateEndTime(textOf(endTimeInput), startTime.getValue());
        EventValidator.Result<SessionType> type = EventValidator.validateType(selectedType);

        nameLayout.setError(
                name.isValid() ? null : getString(R.string.error_session_name_required));
        startTimeLayout.setError(
                startTime.isValid() ? null : getString(R.string.error_start_time_required));
        endTimeLayout.setError(
                endTime.isValid() ? null : getString(R.string.error_end_time_required));
        typeLayout.setError(
                type.isValid() ? null : getString(R.string.error_session_type_required));
        for (TextInputLayout layout : allLayouts()) {
            if (layout.getError() != null) {
                layout.getEditText().requestFocus();
                return;
            }
        }
        long id = sessionToEdit == null ? Session.NO_ID : sessionToEdit.getId();
        Session session = new Session(id, event.getId(), name.getValue(), startTime.getValue(),
                endTime.getValue(), type.getValue(), textOf(speakerInput).trim(),
                textOf(roomInput).trim(), textOf(descriptionInput).trim());
        if (session.equals(sessionToEdit)) {
            // Nothing changed, so there is nothing to save or confirm.
            finish();
            return;
        }
        if (id == Session.NO_ID) {
            database.insertSession(session);
        } else {
            database.updateSession(session);
        }
        setResult(RESULT_OK);
        finish();
    }

    private static String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    /** Clears a field's error once the user edits it. */
    private final class InputWatcher implements TextWatcher {

        private final TextInputLayout layout;
        private String textBefore = "";

        InputWatcher(TextInputLayout layout) {
            this.layout = layout;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            textBefore = s.toString();
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Some keyboards report changes that leave the text as it was.
            if (s.toString().equals(textBefore)) {
                return;
            }
            layout.setError(null);
            discardCallback.setEnabled(hasChanges());
        }
    }
}
