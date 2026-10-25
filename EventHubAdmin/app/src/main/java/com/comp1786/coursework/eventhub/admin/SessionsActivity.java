package com.comp1786.coursework.eventhub.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lists an event's sessions in the order they take place, to add one or open one in full.
 */
public class SessionsActivity extends AppCompatActivity
        implements SessionAdapter.OnSessionClickListener {

    /** Extra with the ID of the event whose sessions to show. */
    public static final String EXTRA_EVENT_ID = "event_id";

    private MaterialToolbar toolbar;
    private RecyclerView sessionList;
    private View emptyView;
    private ExtendedFloatingActionButton addButton;

    private EventDatabase database;
    private SessionAdapter adapter;
    private long eventId;
    private Event event;
    // Set while another screen opens, so a quick second tap does not open it twice.
    private boolean opening;

    // onStart has already shown the list as saved when these results arrive.
    private final ActivityResultLauncher<Intent> addSessionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    showSavedMessage(R.string.message_session_added);
                }
            });
    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == SessionDetailsActivity.RESULT_DELETED
                        && data != null) {
                    showDeletedSession(data.getSerializableExtra(
                            SessionDetailsActivity.EXTRA_SESSION, Session.class));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sessions);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            // The app bar adds the status bar height itself.
            v.setPadding(bars.left, 0, bars.right, 0);
            // Padding the screen itself would throw off where CoordinatorLayout moves the button.
            showAboveNavigationBar(bars.bottom);
            return insets;
        });

        bindViews();
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        database = new EventDatabase(this);
        eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, Event.NO_ID);
        adapter = new SessionAdapter(this);
        sessionList.setAdapter(adapter);

        // Shrinks to the icon while scrolling down, so it covers less of the list.
        sessionList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    addButton.shrink();
                } else if (dy < 0) {
                    addButton.extend();
                }
            }
        });
        addButton.setOnClickListener(v -> openAddSession());
    }

    @Override
    protected void onStart() {
        super.onStart();
        opening = false;
        // Reloads the sessions and the event, whose times may have changed since.
        showSessions();
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }

    @Override
    public void onSessionClick(Session session) {
        if (opening) {
            return;
        }
        opening = true;
        detailsLauncher.launch(new Intent(SessionsActivity.this, SessionDetailsActivity.class)
                .putExtra(SessionDetailsActivity.EXTRA_SESSION_ID, session.getId()));
    }

    /** Keeps the list end and the Add session button above the navigation bar. */
    private void showAboveNavigationBar(int barHeight) {
        sessionList.setPadding(sessionList.getPaddingLeft(), sessionList.getPaddingTop(),
                sessionList.getPaddingRight(),
                getResources().getDimensionPixelSize(R.dimen.list_padding_bottom) + barHeight);
        ViewGroup.MarginLayoutParams button =
                (ViewGroup.MarginLayoutParams) addButton.getLayoutParams();
        button.bottomMargin =
                getResources().getDimensionPixelSize(R.dimen.screen_padding) + barHeight;
        addButton.setLayoutParams(button);
        ViewGroup.MarginLayoutParams empty =
                (ViewGroup.MarginLayoutParams) emptyView.getLayoutParams();
        empty.bottomMargin = barHeight;
        emptyView.setLayoutParams(empty);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        sessionList = findViewById(R.id.session_list);
        emptyView = findViewById(R.id.empty_view);
        addButton = findViewById(R.id.add_button);
    }

    private void openAddSession() {
        if (opening) {
            return;
        }
        opening = true;
        addSessionLauncher.launch(new Intent(SessionsActivity.this, AddEditSessionActivity.class)
                .putExtra(AddEditSessionActivity.EXTRA_EVENT, event));
    }

    /** Confirms that a session was deleted, with an action that saves it again. */
    private void showDeletedSession(Session session) {
        Snackbar snackbar = IconSnackbar.make(sessionList,
                getString(R.string.message_session_deleted), R.drawable.ic_delete,
                Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.button_undo, v -> {
            // A second tap before the bar closes would insert the same ID again and crash.
            v.setEnabled(false);
            database.restoreSession(session);
            showSessions();
        });
        snackbar.show();
    }

    private void showSavedMessage(@StringRes int messageRes) {
        IconSnackbar.make(sessionList, getString(messageRes), R.drawable.ic_check_circle,
                Snackbar.LENGTH_SHORT).show();
    }

    /** Shows the event's sessions, or the empty state if it has none. */
    private void showSessions() {
        event = database.getEvent(eventId);
        if (event == null) {
            finish();
            return;
        }
        toolbar.setSubtitle(event.getName());
        List<Session> sessions = database.getSessions(event);
        adapter.setSessions(event, sessions, LocalDateTime.now());
        emptyView.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        if (sessions.isEmpty()) {
            // The button may still be shrunk from scrolling, and the empty state needs its label.
            addButton.extend();
        }
    }
}
