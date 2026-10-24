package com.comp1786.coursework.eventhub.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.time.LocalTime;

/**
 * Shows a session in full, and returns Edit or Delete as a fragment result.
 */
public class SessionDetailsSheet extends BottomSheetDialogFragment {

    /** Fragment result key for the action the user chose. */
    public static final String REQUEST_KEY = "session_details";
    /** Result extra holding ACTION_EDIT or ACTION_DELETE. */
    public static final String RESULT_ACTION = "action";
    /** Result extra holding the session the action is for. */
    public static final String RESULT_SESSION = "session";
    public static final String ACTION_EDIT = "edit";
    public static final String ACTION_DELETE = "delete";

    private static final String ARG_EVENT = "event";
    private static final String ARG_SESSION = "session";

    /** Creates a sheet for a session of this event. */
    public static SessionDetailsSheet newInstance(Event event, Session session) {
        // Arguments, unlike constructor parameters, survive when Android recreates the fragment.
        Bundle args = new Bundle();
        args.putSerializable(ARG_EVENT, event);
        args.putSerializable(ARG_SESSION, session);
        SessionDetailsSheet sheet = new SessionDetailsSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_session_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Event event = requireArguments().getSerializable(ARG_EVENT, Event.class);
        Session session = requireArguments().getSerializable(ARG_SESSION, Session.class);
        LocalTime start = session.getStartTime();
        LocalTime end = session.getEndTime();
        TextView descriptionText = view.findViewById(R.id.session_description);
        ((TextView) view.findViewById(R.id.session_name)).setText(session.getName());
        ((TextView) view.findViewById(R.id.session_type)).setText(session.getType().getLabelRes());
        // The date as well as the times, as a session after midnight is on the next day.
        ((TextView) view.findViewById(R.id.session_when)).setText(getString(R.string.session_when,
                EventDates.formatLong(session.getStart(event).toLocalDate()),
                EventTimes.format(start), EventTimes.format(end),
                EventTimes.formatLength(getResources(), start, end)));
        showOptional(view.findViewById(R.id.speaker_row),
                view.findViewById(R.id.session_speaker), session.getSpeaker());
        showOptional(view.findViewById(R.id.room_row),
                view.findViewById(R.id.session_room), session.getRoom());
        showOptional(descriptionText, descriptionText, session.getDescription());
        view.findViewById(R.id.edit_button).setOnClickListener(v -> choose(ACTION_EDIT, session));
        view.findViewById(R.id.delete_button)
                .setOnClickListener(v -> choose(ACTION_DELETE, session));
    }

    @Override
    public void onStart() {
        super.onStart();
        // Opens in full, as half open in landscape it would hide the buttons.
        BottomSheetBehavior<?> behavior = ((BottomSheetDialog) requireDialog()).getBehavior();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }

    /** Returns the chosen action to the screen that opened the sheet, then closes it. */
    private void choose(String action, Session session) {
        Bundle result = new Bundle();
        result.putString(RESULT_ACTION, action);
        result.putSerializable(RESULT_SESSION, session);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }

    /** Shows an optional detail, or hides its row when it was left empty. */
    private static void showOptional(View row, TextView valueText, String value) {
        valueText.setText(value);
        row.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
