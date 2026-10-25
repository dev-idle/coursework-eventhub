package com.comp1786.coursework.eventhub.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/**
 * Shows one session in full, laid out as the event details screen, with a menu to edit or
 * delete it.
 */
public class SessionDetailsActivity extends AppCompatActivity {

    /** Extra with the ID of the session to show. */
    public static final String EXTRA_SESSION_ID = "session_id";
    /** Result extra holding the deleted session, so SessionsActivity can save it again. */
    public static final String EXTRA_SESSION = "session";
    /** Result code when the session was deleted. */
    public static final int RESULT_DELETED = RESULT_FIRST_USER;

    private static final String STATE_DELETE_SHOWN = "delete_shown";

    private MaterialToolbar toolbar;
    private TextView nameText;
    private TextView typeLabel;
    private TextView dateText;
    private View timeRow;
    private TextView timeText;
    private TextView nextDayText;
    private TextView lengthText;
    private View speakerRow;
    private TextView speakerText;
    private View roomRow;
    private TextView roomText;
    private View descriptionCard;
    private TextView descriptionText;
    private View content;

    private EventDatabase database;
    private long sessionId;
    private Session session;
    private Event event;
    private AlertDialog deleteDialog;
    private ListPopupWindow moreMenu;

    private final ActivityResultLauncher<Intent> editSessionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // onStart has already shown the saved changes.
                if (result.getResultCode() == RESULT_OK) {
                    IconSnackbar.make(findViewById(R.id.main),
                            getString(R.string.message_session_updated),
                            R.drawable.ic_check_circle, Snackbar.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_session_details);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            // The cards scroll under the navigation bar and end clear of it.
            v.setPadding(bars.left, bars.top, bars.right, 0);
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                    content.getPaddingRight(),
                    getResources().getDimensionPixelSize(R.dimen.screen_padding) + bars.bottom);
            return insets;
        });

        bindViews();
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        database = new EventDatabase(this);
        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, Session.NO_ID);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Reloads the session, which may have been edited since it was shown.
        showSession();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DELETE_SHOWN, deleteDialog != null && deleteDialog.isShowing());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(STATE_DELETE_SHOWN)) {
            showDeleteDialog();
        }
    }

    @Override
    protected void onDestroy() {
        if (deleteDialog != null) {
            deleteDialog.dismiss();
        }
        if (moreMenu != null) {
            moreMenu.dismiss();
        }
        database.close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_more, menu);
        MenuItem more = menu.findItem(R.id.action_more);
        // A button set as an action view does not call onOptionsItemSelected by itself.
        more.getActionView().setOnClickListener(v -> onOptionsItemSelected(more));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_more) {
            moreMenu = MoreMenu.show(this, item.getActionView(),
                    new MoreMenu.Item(R.layout.item_menu_edit, R.string.menu_edit_session,
                            this::openEditSession),
                    new MoreMenu.Item(R.layout.item_menu_delete, R.string.menu_delete_session,
                            this::showDeleteDialog));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        nameText = findViewById(R.id.session_name);
        typeLabel = findViewById(R.id.session_type);
        dateText = findViewById(R.id.session_date);
        timeRow = findViewById(R.id.time_row);
        timeText = findViewById(R.id.session_time);
        nextDayText = findViewById(R.id.next_day);
        lengthText = findViewById(R.id.session_length);
        speakerRow = findViewById(R.id.speaker_row);
        speakerText = findViewById(R.id.session_speaker);
        roomRow = findViewById(R.id.room_row);
        roomText = findViewById(R.id.session_room);
        descriptionCard = findViewById(R.id.description_card);
        descriptionText = findViewById(R.id.session_description);
        content = findViewById(R.id.content);
    }

    private void openEditSession() {
        editSessionLauncher.launch(new Intent(SessionDetailsActivity.this,
                AddEditSessionActivity.class)
                .putExtra(AddEditSessionActivity.EXTRA_EVENT, event)
                .putExtra(AddEditSessionActivity.EXTRA_SESSION, session));
    }

    /** Asks before deleting the session, which then returns RESULT_DELETED. */
    private void showDeleteDialog() {
        Session deleted = session;
        deleteDialog = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_EventHubAdmin_DestructiveDialog)
                .setTitle(R.string.delete_session_title)
                .setMessage(getString(R.string.delete_session_message, deleted.getName()))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_delete, (dialog, which) -> {
                    database.deleteSession(deleted.getId());
                    setResult(RESULT_DELETED, new Intent().putExtra(EXTRA_SESSION, deleted));
                    finish();
                })
                .show();
    }

    /** Shows the saved session, or closes if it is gone. */
    private void showSession() {
        session = database.getSession(sessionId);
        event = session == null ? null : database.getEvent(session.getEventId());
        if (event == null) {
            finish();
            return;
        }
        nameText.setText(session.getName());
        typeLabel.setText(session.getType().getLabelRes());
        showTimes();
        // Without labels on screen, screen readers hear what each row is.
        showOptional(speakerRow, speakerText, session.getSpeaker(), R.string.description_speaker);
        showOptional(roomRow, roomText, session.getRoom(), R.string.description_room);
        descriptionText.setText(session.getDescription());
        descriptionCard.setVisibility(
                session.getDescription().isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Shows the date, then the times and length, with a +1 when the session ends the next day. */
    private void showTimes() {
        String start = EventTimes.format(session.getStartTime());
        String end = EventTimes.format(session.getEndTime());
        String length = EventTimes.formatLength(
                getResources(), session.getStartTime(), session.getEndTime());
        boolean endsNextDay = EventTimes.endsNextDay(session.getStartTime(), session.getEndTime());
        dateText.setText(EventDates.formatLong(session.getStart(event).toLocalDate()));
        timeText.setText(getString(R.string.time_range, start, end));
        lengthText.setText(length);
        nextDayText.setVisibility(endsNextDay ? View.VISIBLE : View.GONE);
        timeRow.setContentDescription(getString(endsNextDay
                ? R.string.description_time_range_next_day : R.string.description_time_range,
                start, end, length));
    }

    /** Shows an optional detail, read out with what it is, or hides its row when empty. */
    private void showOptional(View row, TextView valueText, String value,
            @StringRes int descriptionRes) {
        valueText.setText(value);
        valueText.setContentDescription(getString(descriptionRes, value));
        row.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
