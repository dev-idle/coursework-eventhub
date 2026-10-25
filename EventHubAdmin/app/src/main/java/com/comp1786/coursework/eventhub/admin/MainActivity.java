package com.comp1786.coursework.eventhub.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lists the saved events as cards, with filters and a way to delete several at once.
 */
public class MainActivity extends AppCompatActivity implements EventAdapter.OnEventClickListener {

    private static final String STATE_FILTER = "filter";
    private static final String STATE_SELECTED_IDS = "selected_ids";
    private static final String STATE_DELETE_SHOWN = "delete_shown";

    private MaterialToolbar toolbar;
    private TextView eventCount;
    private View filterBar;
    private Chip dateFilter;
    private Chip organizerFilter;
    private Chip statusFilter;
    private RecyclerView eventList;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private ExtendedFloatingActionButton addButton;

    private EventDatabase database;
    private EventAdapter adapter;
    // Kept across rotation but not saved, so a new launch shows every saved event.
    private EventFilter filter;
    private AlertDialog deleteDialog;
    private ListPopupWindow moreMenu;
    @Nullable
    private MenuItem searchItem;
    @Nullable
    private MenuItem moreItem;
    // Set while another screen opens, so a quick second tap does not open it twice.
    private boolean opening;

    private final ActivityResultLauncher<Intent> addEventLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::showAddedEvent);
    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == EventDetailsActivity.RESULT_DELETED) {
                    showDeletedEvent(result);
                }
            });
    // Back leaves selecting before it leaves the app.
    private final OnBackPressedCallback selectionCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            stopSelection();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            // The app bar adds the status bar height itself, so its color fills that area.
            v.setPadding(bars.left, 0, bars.right, 0);
            // Padding the screen itself would throw off where CoordinatorLayout moves the button.
            showAboveNavigationBar(bars.bottom);
            return insets;
        });

        bindViews();
        setSupportActionBar(toolbar);
        // The close button only shows while selecting.
        toolbar.setNavigationOnClickListener(v -> stopSelection());
        getOnBackPressedDispatcher().addCallback(this, selectionCallback);
        database = new EventDatabase(this);
        adapter = new EventAdapter(this);
        GridLayoutManager layoutManager = new GridLayoutManager(this,
                getResources().getInteger(R.integer.event_list_columns));
        // Month headings span every column.
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isMonthHeading(position) ? layoutManager.getSpanCount() : 1;
            }
        });
        eventList.setLayoutManager(layoutManager);
        eventList.setAdapter(adapter);
        EventFilter savedFilter = savedInstanceState == null
                ? null : savedInstanceState.getSerializable(STATE_FILTER, EventFilter.class);
        filter = savedFilter == null ? new EventFilter() : savedFilter;
        setUpFilters();

        // Shrinks to the icon while scrolling down, so it covers less of the list.
        eventList.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
        addButton.setOnClickListener(v -> openAddEvent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        opening = false;
        // Reloads the saved events, whose states and countdowns change as time passes.
        showEvents();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_FILTER, filter);
        if (adapter.isSelecting()) {
            outState.putLongArray(STATE_SELECTED_IDS, adapter.getSelectedIds());
        }
        outState.putBoolean(STATE_DELETE_SHOWN, deleteDialog != null && deleteDialog.isShowing());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Called after onStart has shown the events, so the selected ones are in the list.
        long[] selectedIds = savedInstanceState.getLongArray(STATE_SELECTED_IDS);
        if (selectedIds != null) {
            adapter.select(selectedIds);
            startSelection();
        }
        // The filter may hide the selected events by now, such as once they have ended.
        if (savedInstanceState.getBoolean(STATE_DELETE_SHOWN) && adapter.getSelectedCount() > 0) {
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
        getMenuInflater().inflate(
                adapter.isSelecting() ? R.menu.menu_selection : R.menu.menu_main, menu);
        searchItem = menu.findItem(R.id.action_search);
        moreItem = menu.findItem(R.id.action_more);
        if (searchItem != null) {
            setUpSearch(searchItem);
        }
        if (moreItem != null) {
            // A button set as an action view does not call onOptionsItemSelected by itself.
            moreItem.getActionView().setOnClickListener(v -> onOptionsItemSelected(moreItem));
        }
        showAppBarButtons();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem selectAll = menu.findItem(R.id.action_select_all);
        if (selectAll != null) {
            // Like the check box over a table: empty, partly filled, or checked once all are.
            boolean all = adapter.isAllSelected();
            boolean some = adapter.getSelectedCount() > 0;
            selectAll.setIcon(all ? R.drawable.ic_check_box
                    : some ? R.drawable.ic_indeterminate_check_box
                    : R.drawable.ic_check_box_outline_blank);
            selectAll.setTitle(all ? R.string.menu_deselect_all : R.string.menu_select_all);
        }
        MenuItem delete = menu.findItem(R.id.action_delete);
        if (delete != null) {
            delete.setVisible(adapter.getSelectedCount() > 0);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_more) {
            moreMenu = MoreMenu.show(this, item.getActionView(), new MoreMenu.Item(
                    R.layout.item_menu_select, R.string.menu_select_events, this::startSelection));
            return true;
        } else if (id == R.id.action_select_all) {
            if (adapter.isAllSelected()) {
                adapter.clearSelection();
            } else {
                adapter.selectAll();
            }
            showSelection();
            return true;
        } else if (id == R.id.action_delete) {
            showDeleteDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onEventClick(Event event) {
        if (adapter.isSelecting()) {
            adapter.toggleSelected(event.getId());
            showSelection();
        } else {
            openEventDetails(event);
        }
    }

    /** Starts selecting with this card, or selects it like a tap while already selecting. */
    @Override
    public void onEventLongClick(Event event) {
        startSelection();
        onEventClick(event);
    }

    /** Ends the list above the navigation bar and lifts the button and empty state over it. */
    private void showAboveNavigationBar(int barHeight) {
        eventList.setPadding(eventList.getPaddingLeft(), eventList.getPaddingTop(),
                eventList.getPaddingRight(),
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
        eventCount = findViewById(R.id.event_count);
        filterBar = findViewById(R.id.filter_bar);
        dateFilter = findViewById(R.id.date_filter);
        organizerFilter = findViewById(R.id.organizer_filter);
        statusFilter = findViewById(R.id.status_filter);
        eventList = findViewById(R.id.event_list);
        emptyView = findViewById(R.id.empty_view);
        emptyTitle = findViewById(R.id.empty_title);
        emptyMessage = findViewById(R.id.empty_message);
        addButton = findViewById(R.id.add_button);
    }

    /** Shows search with the filters while events are saved, and More while cards are shown. */
    private void showAppBarButtons() {
        if (searchItem != null) {
            searchItem.setVisible(filterBar.getVisibility() == View.VISIBLE);
        }
        if (moreItem != null) {
            moreItem.setVisible(adapter.getItemCount() > 0);
        }
    }

    /** Searches as the user types, and reopens the search the list is still narrowed to. */
    private void setUpSearch(MenuItem item) {
        SearchView searchView = (SearchView) item.getActionView();
        searchView.setQueryHint(getString(R.string.hint_search_events));
        searchView.setMaxWidth(Integer.MAX_VALUE);
        // After rotation, or once selecting ends, the field shows the text searched for.
        if (!filter.getText().isEmpty()) {
            item.expandActionView();
            searchView.setQuery(filter.getText(), false);
            searchView.clearFocus();
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Called with no text as the field closes, which it also does when selecting
                // starts, and selecting keeps the list as it is.
                if (!adapter.isSelecting()) {
                    filter.setText(newText);
                    showEvents();
                }
                return true;
            }
        });
    }

    /** Opens each filter's choices, and clears a chosen one from its close button. */
    private void setUpFilters() {
        setUpFilter(dateFilter, this::showDatePicker, () -> filter.setDate(null));
        setUpFilter(organizerFilter, this::showOrganizerMenu, () -> filter.setOrganizer(null));
        setUpFilter(statusFilter, this::showStatusMenu, () -> filter.setStatus(null));
        getSupportFragmentManager().setFragmentResultListener(DatePickerFragment.REQUEST_KEY,
                this, (key, result) -> {
                    filter.setDate(result.getSerializable(
                            DatePickerFragment.RESULT_DATE, LocalDate.class));
                    showEvents();
                });
    }

    private void setUpFilter(Chip chip, Runnable showChoices, Runnable clear) {
        chip.setOnClickListener(v -> {
            // A tap also checks or unchecks the chip, which only shows whether one is chosen.
            showFilters();
            showChoices.run();
        });
        // The arrow opens the choices too, and a chosen filter's close button clears it.
        chip.setOnCloseIconClickListener(v -> {
            if (chip.isChecked()) {
                clear.run();
                showEvents();
            } else {
                showChoices.run();
            }
        });
    }

    private void showDatePicker() {
        // A quick second tap would otherwise open a second picker.
        if (getSupportFragmentManager().findFragmentByTag(DatePickerFragment.REQUEST_KEY) != null) {
            return;
        }
        LocalDate date = filter.getDate();
        DatePickerFragment.newInstance(date != null ? date : LocalDate.now())
                .show(getSupportFragmentManager(), DatePickerFragment.REQUEST_KEY);
    }

    /** Lists the organizers of the saved events, so one is chosen rather than typed. */
    private void showOrganizerMenu() {
        PopupMenu menu = new PopupMenu(this, organizerFilter);
        List<String> organizers = database.getOrganizers();
        for (String organizer : organizers) {
            menu.getMenu().add(organizer);
        }
        // One choice at a time, with the chosen one marked.
        menu.getMenu().setGroupCheckable(Menu.NONE, true, true);
        int chosen = organizers.indexOf(filter.getOrganizer());
        if (chosen >= 0) {
            menu.getMenu().getItem(chosen).setChecked(true);
        }
        menu.setOnMenuItemClickListener(item -> {
            filter.setOrganizer(item.getTitle().toString());
            showEvents();
            return true;
        });
        menu.show();
    }

    private void showStatusMenu() {
        PopupMenu menu = new PopupMenu(this, statusFilter);
        for (EventStatus status : EventStatus.values()) {
            menu.getMenu().add(Menu.NONE, status.ordinal(), Menu.NONE, status.getLabelRes());
        }
        menu.getMenu().setGroupCheckable(Menu.NONE, true, true);
        if (filter.getStatus() != null) {
            menu.getMenu().findItem(filter.getStatus().ordinal()).setChecked(true);
        }
        menu.setOnMenuItemClickListener(item -> {
            filter.setStatus(EventStatus.values()[item.getItemId()]);
            showEvents();
            return true;
        });
        menu.show();
    }

    /** Shows each filter's label, or its chosen value with a button that clears it. */
    private void showFilters() {
        LocalDate date = filter.getDate();
        EventStatus status = filter.getStatus();
        showFilter(dateFilter, R.string.filter_date,
                date == null ? null : EventDates.format(date), R.string.description_clear_date);
        showFilter(organizerFilter, R.string.filter_organizer, filter.getOrganizer(),
                R.string.description_clear_organizer);
        showFilter(statusFilter, R.string.filter_status,
                status == null ? null : getString(status.getLabelRes()),
                R.string.description_clear_status);
    }

    private void showFilter(Chip chip, @StringRes int labelRes, @Nullable String value,
            @StringRes int clearRes) {
        boolean chosen = value != null;
        chip.setText(chosen ? value : getString(labelRes));
        // Checked only to look chosen, as the chip itself opens the choices.
        chip.setChecked(chosen);
        chip.setCloseIconResource(chosen ? R.drawable.ic_close : R.drawable.ic_arrow_drop_down);
        chip.setCloseIconContentDescription(chosen ? getString(clearRes) : null);
    }

    private void openAddEvent() {
        if (opening) {
            return;
        }
        opening = true;
        addEventLauncher.launch(new Intent(MainActivity.this, AddEditEventActivity.class));
    }

    private void openEventDetails(Event event) {
        if (opening) {
            return;
        }
        opening = true;
        detailsLauncher.launch(new Intent(MainActivity.this, EventDetailsActivity.class)
                .putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getId()));
    }

    /** Scrolls to an event that was just added and confirms it with a message. */
    private void showAddedEvent(ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() != RESULT_OK || data == null) {
            return;
        }
        scrollToEvent(data.getLongExtra(AddEditEventActivity.EXTRA_EVENT_ID, Event.NO_ID));
        IconSnackbar.make(eventList, getString(R.string.message_event_added),
                R.drawable.ic_check_circle, Snackbar.LENGTH_SHORT).show();
    }

    /** Scrolls to an event, first showing every event if the filters hide it. */
    private void scrollToEvent(long id) {
        if (adapter.positionOf(id) == RecyclerView.NO_POSITION && !filter.isEmpty()) {
            filter = new EventFilter();
            if (searchItem != null) {
                searchItem.collapseActionView();
            }
            showEvents();
        }
        eventList.scrollToPosition(adapter.positionOf(id));
    }

    /** Confirms that the event deleted from its details screen is gone. */
    private void showDeletedEvent(ActivityResult result) {
        Intent data = result.getData();
        Event event = data == null
                ? null : data.getSerializableExtra(EventDetailsActivity.EXTRA_EVENT, Event.class);
        Session[] sessions = data == null ? null
                : data.getSerializableExtra(EventDetailsActivity.EXTRA_SESSIONS, Session[].class);
        if (event != null && sessions != null) {
            showDeletedEvents(Collections.singletonList(event), Arrays.asList(sessions));
        }
    }

    /** Confirms that events were deleted, with an action that saves them and their sessions. */
    private void showDeletedEvents(List<Event> events, List<Session> sessions) {
        int count = events.size();
        Snackbar snackbar = IconSnackbar.make(eventList,
                getResources().getQuantityString(R.plurals.message_events_deleted, count, count),
                R.drawable.ic_delete, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.button_undo, v -> {
            // A second tap before the bar closes would insert the same IDs again and crash.
            v.setEnabled(false);
            database.restoreEvents(events, sessions);
            showEvents();
            scrollToEvent(events.get(0).getId());
        });
        snackbar.show();
    }

    /** Turns the app bar into a selection bar, where tapping a card selects it. */
    private void startSelection() {
        if (adapter.isSelecting()) {
            return;
        }
        adapter.setSelecting(true);
        selectionCallback.setEnabled(true);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationContentDescription(R.string.description_close_selection);
        setFiltersEnabled(false);
        addButton.hide();
        showSelection();
    }

    /** Shows how many cards are selected, with the matching Select all and delete buttons. */
    private void showSelection() {
        int count = adapter.getSelectedCount();
        toolbar.setTitle(count == 0 ? getString(R.string.title_select_events)
                : getResources().getQuantityString(R.plurals.selected_count, count, count));
        invalidateOptionsMenu();
    }

    /** Turns the selection bar back into the app bar and deselects every card. */
    private void stopSelection() {
        adapter.setSelecting(false);
        selectionCallback.setEnabled(false);
        toolbar.setNavigationIcon(null);
        toolbar.setTitle(R.string.app_name);
        setFiltersEnabled(true);
        addButton.show();
        invalidateOptionsMenu();
    }

    /** Locks the filters while selecting, so the events to select stay the same. */
    private void setFiltersEnabled(boolean enabled) {
        dateFilter.setEnabled(enabled);
        organizerFilter.setEnabled(enabled);
        statusFilter.setEnabled(enabled);
    }

    /** Asks before deleting the selected events, which Undo can then bring back. */
    private void showDeleteDialog() {
        List<Event> events = adapter.getSelectedEvents();
        // Read before deleting, as the sessions go with their events.
        List<Session> sessions = database.getSessions(events);
        int count = events.size();
        deleteDialog = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_EventHubAdmin_DestructiveDialog)
                .setTitle(getResources().getQuantityString(
                        R.plurals.delete_events_title, count, count))
                .setMessage(getResources().getQuantityString(sessions.isEmpty()
                        ? R.plurals.delete_events_message
                        : R.plurals.delete_events_message_with_sessions, count))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_delete, (dialog, which) -> {
                    database.deleteEvents(events);
                    stopSelection();
                    showEvents();
                    showDeletedEvents(events, sessions);
                })
                .show();
    }

    /** Shows the events the filters keep and their number, or the empty state if there are none. */
    private void showEvents() {
        LocalDateTime now = LocalDateTime.now();
        database.completeEndedEvents(now);
        boolean nothingSaved = database.countEvents() == 0;
        if (nothingSaved) {
            // The filters hide while nothing is saved, so the list goes back to every event.
            filter = new EventFilter();
            if (searchItem != null) {
                searchItem.collapseActionView();
            }
        }
        showFilters();
        List<Event> events = database.getEvents(filter);
        // The same time, so every card shown as ended is also completed.
        adapter.setEvents(events, database.getSessionCounts(), now);
        filterBar.setVisibility(nothingSaved ? View.GONE : View.VISIBLE);
        boolean empty = events.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        emptyTitle.setText(filter.isEmpty() ? R.string.empty_title : R.string.empty_filtered_title);
        emptyMessage.setText(filter.isEmpty() ? R.string.empty_message
                : filter.getText().isEmpty() ? R.string.empty_filtered_message
                : R.string.empty_search_message);
        if (empty) {
            // The button may still be shrunk from scrolling, and the empty state needs its label.
            addButton.extend();
        }
        // Kept while the filters hide every event, so they stay where they were.
        eventCount.setVisibility(nothingSaved ? View.GONE : View.VISIBLE);
        eventCount.setText(getResources().getQuantityString(
                R.plurals.event_count, events.size(), events.size()));
        if (adapter.isSelecting()) {
            // Events no longer shown were deselected, so the number selected may have changed.
            showSelection();
        } else {
            // Set in place, as rebuilding the menu would close the search field.
            showAppBarButtons();
        }
    }
}
