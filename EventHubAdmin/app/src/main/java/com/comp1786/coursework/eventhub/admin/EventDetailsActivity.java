package com.comp1786.coursework.eventhub.admin;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Shows an event in full: a saved one with a menu to edit or delete it, or an unsaved one to
 * review before it is saved.
 */
public class EventDetailsActivity extends AppCompatActivity {

    /** Extra with the ID of the event to show. */
    public static final String EXTRA_EVENT_ID = "event_id";
    /** Extra with an unsaved event to review, returned with RESULT_OK once the user confirms. */
    public static final String EXTRA_REVIEW_EVENT = "review_event";
    /** Result extra holding the deleted event, so MainActivity can save it again. */
    public static final String EXTRA_EVENT = "event";
    /** Result extra holding the deleted event's sessions, as a Session array. */
    public static final String EXTRA_SESSIONS = "sessions";
    /** Result code when the event was deleted. */
    public static final int RESULT_DELETED = RESULT_FIRST_USER;

    private static final String STATE_DELETE_SHOWN = "delete_shown";

    private MaterialToolbar toolbar;
    private TextView nameText;
    private TextView typeLabel;
    private TextView statusText;
    private TextView descriptionText;
    private View timeBlock;
    private TextView dayText;
    private TextView dayOfWeekText;
    private TextView monthText;
    private TextView startTimeText;
    private TextView lengthText;
    private TextView endTimeText;
    private TextView nextDayText;
    private TextView endDateText;
    private TextView locationText;
    private TextView organizerText;
    private View contactEmailRow;
    private TextView contactEmailText;
    private View contactPhoneRow;
    private TextView contactPhoneText;
    private View sheet;
    private View reviewBar;
    private Button editButton;
    private Button confirmButton;
    private View sessionsRow;
    private TextView sessionCountText;
    private TextView nextSessionText;

    private EventDatabase database;
    private long eventId;
    // Null unless an unsaved event is being reviewed.
    @Nullable
    private Event reviewEvent;
    private Event event;
    private AlertDialog deleteDialog;
    private ListPopupWindow moreMenu;
    // Set while another screen opens, so a quick second tap does not open it twice.
    private boolean opening;

    private final ActivityResultLauncher<Intent> editEventLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // onStart has already shown the saved changes.
                if (result.getResultCode() == RESULT_OK) {
                    IconSnackbar.make(findViewById(R.id.main),
                            getString(R.string.message_event_updated),
                            R.drawable.ic_check_circle, Snackbar.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_event_details);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            // The sheet runs under the navigation bar, and only what ends the screen moves up.
            v.setPadding(bars.left, bars.top, bars.right, 0);
            View last = reviewBar.getVisibility() == View.VISIBLE ? reviewBar : sheet;
            last.setPadding(last.getPaddingLeft(), last.getPaddingTop(), last.getPaddingRight(),
                    getResources().getDimensionPixelSize(R.dimen.screen_padding) + bars.bottom);
            return insets;
        });

        bindViews();
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        database = new EventDatabase(this);
        eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, Event.NO_ID);
        reviewEvent = getIntent().getSerializableExtra(EXTRA_REVIEW_EVENT, Event.class);
        if (reviewEvent != null) {
            showReviewBar(reviewEvent.getId() == Event.NO_ID);
        } else {
            enableContactRows();
            sessionsRow.setOnClickListener(v -> openSessions());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        opening = false;
        // Reloads the event, which may have been edited or have ended since it was shown.
        showEvent();
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
        // An unsaved event has nothing to edit or delete yet.
        if (reviewEvent != null) {
            return false;
        }
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
                    new MoreMenu.Item(R.layout.item_menu_edit, R.string.menu_edit,
                            this::openEditEvent),
                    new MoreMenu.Item(R.layout.item_menu_delete, R.string.menu_delete,
                            this::showDeleteDialog));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        nameText = findViewById(R.id.event_name);
        typeLabel = findViewById(R.id.type_label);
        statusText = findViewById(R.id.event_status);
        descriptionText = findViewById(R.id.event_description);
        timeBlock = findViewById(R.id.time_block);
        dayText = findViewById(R.id.event_day);
        dayOfWeekText = findViewById(R.id.event_day_of_week);
        monthText = findViewById(R.id.event_month);
        startTimeText = findViewById(R.id.start_time);
        lengthText = findViewById(R.id.event_length);
        endTimeText = findViewById(R.id.end_time);
        nextDayText = findViewById(R.id.next_day);
        endDateText = findViewById(R.id.end_date);
        locationText = findViewById(R.id.event_location);
        organizerText = findViewById(R.id.event_organizer);
        contactEmailRow = findViewById(R.id.contact_email_row);
        contactEmailText = findViewById(R.id.event_contact_email);
        contactPhoneRow = findViewById(R.id.contact_phone_row);
        contactPhoneText = findViewById(R.id.event_contact_phone);
        sheet = findViewById(R.id.sheet);
        reviewBar = findViewById(R.id.review_bar);
        editButton = findViewById(R.id.edit_button);
        confirmButton = findViewById(R.id.confirm_button);
        sessionsRow = findViewById(R.id.sessions_row);
        sessionCountText = findViewById(R.id.session_count);
        nextSessionText = findViewById(R.id.next_session);
    }

    /** Shows Edit, which goes back to the form, and a button that confirms the event. */
    private void showReviewBar(boolean adding) {
        setTitle(adding ? R.string.title_review_event : R.string.title_review_changes);
        confirmButton.setText(adding ? R.string.button_save_event : R.string.button_save_changes);
        reviewBar.setVisibility(View.VISIBLE);
        // An unsaved event has no sessions yet.
        sessionsRow.setVisibility(View.GONE);
        editButton.setOnClickListener(v -> finish());
        confirmButton.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REVIEW_EVENT, reviewEvent));
            finish();
        });
    }

    /** Lets the contact rows of a saved event open an email or the dialer, colored as links. */
    private void enableContactRows() {
        int linkColor = MaterialColors.getColor(contactEmailText,
                androidx.appcompat.R.attr.colorPrimary);
        contactEmailText.setTextColor(linkColor);
        contactPhoneText.setTextColor(linkColor);
        contactEmailRow.setOnClickListener(v -> openContact(new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", event.getContactEmail(), null))));
        contactPhoneRow.setOnClickListener(v -> openContact(new Intent(Intent.ACTION_DIAL,
                Uri.fromParts("tel", event.getContactPhone(), null))));
        // TalkBack says "Double-tap to send email" rather than "to activate".
        ViewCompat.replaceAccessibilityAction(contactEmailRow,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.action_send_email), null);
        ViewCompat.replaceAccessibilityAction(contactPhoneRow,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.action_call), null);
    }

    private void openContact(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void openEditEvent() {
        editEventLauncher.launch(new Intent(EventDetailsActivity.this, AddEditEventActivity.class)
                .putExtra(AddEditEventActivity.EXTRA_EVENT, event));
    }

    /** Opens the event's sessions, to add, view, edit or delete them. */
    private void openSessions() {
        if (opening) {
            return;
        }
        opening = true;
        startActivity(new Intent(EventDetailsActivity.this, SessionsActivity.class)
                .putExtra(SessionsActivity.EXTRA_EVENT_ID, event.getId()));
    }

    /** Asks before deleting the event and its sessions, which then returns RESULT_DELETED. */
    private void showDeleteDialog() {
        Event deleted = event;
        // Read before deleting, as the sessions go with the event, so Undo can save them again.
        List<Session> sessions = database.getSessions(deleted);
        String name = deleted.getName();
        String date = EventDates.format(deleted.getDate());
        deleteDialog = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_EventHubAdmin_DestructiveDialog)
                .setTitle(R.string.delete_title)
                .setMessage(sessions.isEmpty()
                        ? getString(R.string.delete_message, name, date)
                        : getResources().getQuantityString(R.plurals.delete_message_with_sessions,
                                sessions.size(), name, date, sessions.size()))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_delete, (dialog, which) -> {
                    database.deleteEvent(deleted.getId());
                    setResult(RESULT_DELETED, new Intent().putExtra(EXTRA_EVENT, deleted)
                            .putExtra(EXTRA_SESSIONS, sessions.toArray(new Session[0])));
                    finish();
                })
                .show();
    }

    /** Shows the event as its card would, with the same status text and colors. */
    private void showEvent() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> events;
        if (reviewEvent != null) {
            // Placed among the saved events, as it will be once saved.
            event = reviewEvent;
            events = database.getAllEvents();
            events.removeIf(saved -> saved.getId() == event.getId());
            events.add(event);
        } else {
            // The same steps and time as the list, so the status matches the card.
            database.completeEndedEvents(now);
            event = database.getEvent(eventId);
            if (event == null) {
                finish();
                return;
            }
            events = database.getAllEvents();
        }
        EventCardState state = EventCardState.at(
                event, now, EventCardState.nextStart(events, now));
        EventType type = event.getType();
        nameText.setText(event.getName());
        typeLabel.setText(type.getLabelRes());
        state.showStatus(statusText, event, state.daysAway(event, now.toLocalDate()));
        showOptional(descriptionText, descriptionText, event.getDescription());
        showTimes(state);
        locationText.setText(event.getLocation());
        organizerText.setText(event.getOrganizer());
        showOptional(contactEmailRow, contactEmailText, event.getContactEmail());
        showOptional(contactPhoneRow, contactPhoneText, event.getContactPhone());
        state.showCancelled(nameText, typeLabel, type, startTimeText, endTimeText);
        if (reviewEvent == null) {
            showSessionsRow(state, now);
        }
    }

    /** Shows how many sessions the event has and, unless it is cancelled, the next to start. */
    private void showSessionsRow(EventCardState state, LocalDateTime now) {
        List<Session> sessions = database.getSessions(event);
        int count = sessions.size();
        sessionCountText.setText(count == 0 ? getString(R.string.no_sessions)
                : getResources().getQuantityString(R.plurals.session_count, count, count));
        Session next = null;
        if (state != EventCardState.CANCELLED) {
            for (Session session : sessions) {
                if (session.getStart(event).isAfter(now)) {
                    next = session;
                    break;
                }
            }
        }
        nextSessionText.setVisibility(next == null ? View.GONE : View.VISIBLE);
        if (next != null) {
            nextSessionText.setText(getString(R.string.next_session,
                    EventTimes.format(next.getStartTime()), next.getName()));
        }
    }

    /** Shows an optional detail, or hides its row when it was left empty. */
    private static void showOptional(View row, TextView valueText, String value) {
        valueText.setText(value);
        row.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Shows the date and a timeline from start to end, with the end date if it is the next day. */
    private void showTimes(EventCardState state) {
        LocalDate startDate = event.getDate();
        LocalDate endDate = event.getEnd().toLocalDate();
        String start = EventTimes.format(event.getStartTime());
        String end = EventTimes.format(event.getEndTime());
        String length = EventTimes.formatLength(
                getResources(), event.getStartTime(), event.getEndTime());
        dayText.setText(EventDates.formatDayOfMonth(startDate));
        boolean over = state == EventCardState.PAST || state == EventCardState.CANCELLED;
        // Blue like a colored date panel, or gray like a past or cancelled one.
        dayText.setTextColor(MaterialColors.getColor(dayText, over
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : androidx.appcompat.R.attr.colorPrimary));
        dayOfWeekText.setText(EventDates.formatDayOfWeek(startDate));
        monthText.setText(EventDates.formatMonth(YearMonth.from(startDate)));
        startTimeText.setText(start);
        lengthText.setText(length);
        endTimeText.setText(end);
        nextDayText.setVisibility(event.endsNextDay() ? View.VISIBLE : View.GONE);
        endDateText.setVisibility(event.endsNextDay() ? View.VISIBLE : View.GONE);
        endDateText.setText(EventDates.formatLong(endDate));
        timeBlock.setContentDescription(getString(R.string.description_starts_ends,
                start, EventDates.formatLong(startDate), end, EventDates.formatLong(endDate),
                length));
    }
}
