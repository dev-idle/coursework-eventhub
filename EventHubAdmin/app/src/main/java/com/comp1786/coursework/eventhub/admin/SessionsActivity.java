package com.comp1786.coursework.eventhub.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
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

    private static final String STATE_QUERY = "query";

    private MaterialToolbar toolbar;
    private RecyclerView sessionList;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private ExtendedFloatingActionButton addButton;

    private EventDatabase database;
    private SessionAdapter adapter;
    private long eventId;
    private Event event;
    // The text the sessions are searched for, kept across rotation.
    private String query = "";
    @Nullable
    private MenuItem searchItem;
    // Set while another screen opens, so a quick second tap does not open it twice.
    private boolean opening;

    // onStart has already shown the list as saved when these results arrive.
    private final ActivityResultLauncher<Intent> addSessionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // The new session may not match the search, so every session is shown.
                    if (searchItem != null) {
                        searchItem.collapseActionView();
                    }
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
        query = savedInstanceState == null ? "" : savedInstanceState.getString(STATE_QUERY, "");
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

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                // Dragging the list puts the keyboard away, keeping the search and its results.
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searchItem != null) {
                    searchItem.getActionView().clearFocus();
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, query);
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sessions, menu);
        searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.hint_search_sessions));
        searchView.setMaxWidth(Integer.MAX_VALUE);
        // After rotation, the field shows the text searched for.
        if (!query.isEmpty()) {
            searchItem.expandActionView();
            searchView.setQuery(query, false);
            searchView.clearFocus();
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String text) {
                searchView.clearFocus();
                return true;
            }

            // Also called with no text as the field closes.
            @Override
            public boolean onQueryTextChange(String text) {
                query = text.trim();
                showSessions();
                return true;
            }
        });
        showSearchButton();
        return true;
    }

    /** Hides search while the event has no sessions to search. */
    private void showSearchButton() {
        if (searchItem != null) {
            searchItem.setVisible(!query.isEmpty() || adapter.getItemCount() > 0);
        }
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
        emptyTitle = findViewById(R.id.empty_title);
        emptyMessage = findViewById(R.id.empty_message);
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

    /** Shows the event's sessions that match the search, or the empty state if none do. */
    private void showSessions() {
        event = database.getEvent(eventId);
        if (event == null) {
            finish();
            return;
        }
        toolbar.setSubtitle(event.getName());
        List<Session> sessions = database.getSessions(event, query);
        adapter.setSessions(event, sessions, LocalDateTime.now());
        showSearchButton();
        emptyView.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        emptyTitle.setText(
                query.isEmpty() ? R.string.sessions_empty : R.string.sessions_empty_search);
        emptyMessage.setText(
                query.isEmpty() ? R.string.sessions_empty_message : R.string.empty_search_message);
        if (sessions.isEmpty()) {
            // The button may still be shrunk from scrolling, and the empty state needs its label.
            addButton.extend();
        }
    }
}
