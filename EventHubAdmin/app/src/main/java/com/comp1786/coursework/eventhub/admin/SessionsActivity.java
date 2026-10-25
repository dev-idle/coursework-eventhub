package com.comp1786.coursework.eventhub.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lists an event's sessions in the order they take place, to add, view, edit or delete them.
 */
public class SessionsActivity extends AppCompatActivity
        implements SessionAdapter.OnSessionClickListener {

    /** Extra with the ID of the event whose sessions to show. */
    public static final String EXTRA_EVENT_ID = "event_id";

    private static final String STATE_SESSION_TO_DELETE = "session_to_delete";

    private MaterialToolbar toolbar;
    private RecyclerView sessionList;
    private View emptyView;
    private ExtendedFloatingActionButton addButton;

    private EventDatabase database;
    private SessionAdapter adapter;
    private long eventId;
    private Event event;
    private AlertDialog deleteDialog;
    // The session the delete dialog asks about, kept so the dialog survives rotation.
    @Nullable
    private Session sessionToDelete;
    // Set while another screen opens, so a quick second tap does not open it twice.
    private boolean opening;

    // onStart has already shown the saved session when these results arrive.
    private final ActivityResultLauncher<Intent> addSessionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    showSavedMessage(R.string.message_session_added);
                }
            });
    private final ActivityResultLauncher<Intent> editSessionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    showSavedMessage(R.string.message_session_updated);
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
        addButton.setOnClickListener(v -> openSession(null));
        getSupportFragmentManager().setFragmentResultListener(SessionDetailsSheet.REQUEST_KEY,
                this, (key, result) -> {
                    Session session = result.getSerializable(
                            SessionDetailsSheet.RESULT_SESSION, Session.class);
                    if (SessionDetailsSheet.ACTION_EDIT.equals(
                            result.getString(SessionDetailsSheet.RESULT_ACTION))) {
                        openSession(session);
                    } else {
                        showDeleteDialog(session);
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        opening = false;
        // Reloads the sessions and the event, whose times may have changed since.
        showSessions();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (deleteDialog != null && deleteDialog.isShowing()) {
            outState.putSerializable(STATE_SESSION_TO_DELETE, sessionToDelete);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Session session =
                savedInstanceState.getSerializable(STATE_SESSION_TO_DELETE, Session.class);
        if (session != null) {
            showDeleteDialog(session);
        }
    }

    @Override
    protected void onDestroy() {
        if (deleteDialog != null) {
            deleteDialog.dismiss();
        }
        database.close();
        super.onDestroy();
    }

    @Override
    public void onSessionClick(Session session) {
        // A quick second tap would otherwise open a second sheet.
        if (getSupportFragmentManager().findFragmentByTag(SessionDetailsSheet.REQUEST_KEY)
                != null) {
            return;
        }
        SessionDetailsSheet.newInstance(event, session)
                .show(getSupportFragmentManager(), SessionDetailsSheet.REQUEST_KEY);
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

    /** Opens the form for a new session, or for editing this one. */
    private void openSession(@Nullable Session session) {
        if (opening) {
            return;
        }
        opening = true;
        Intent intent = new Intent(SessionsActivity.this, AddEditSessionActivity.class)
                .putExtra(AddEditSessionActivity.EXTRA_EVENT, event);
        if (session == null) {
            addSessionLauncher.launch(intent);
        } else {
            editSessionLauncher.launch(
                    intent.putExtra(AddEditSessionActivity.EXTRA_SESSION, session));
        }
    }

    /** Asks before deleting the session, which Undo can then bring back. */
    private void showDeleteDialog(Session session) {
        sessionToDelete = session;
        deleteDialog = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_EventHubAdmin_DestructiveDialog)
                .setTitle(R.string.delete_session_title)
                .setMessage(getString(R.string.delete_session_message, session.getName()))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_delete, (dialog, which) -> {
                    database.deleteSession(session.getId());
                    showSessions();
                    showDeletedSession(session);
                })
                .show();
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
