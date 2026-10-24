package com.comp1786.coursework.eventhub.admin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists an event's sessions as rows of times, name, type, speaker and room.
 */
public class SessionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final OnSessionClickListener listener;
    private List<Session> sessions = new ArrayList<>();
    private Event event;

    public SessionAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    /** Shows the sessions of an event, in the order they take place. */
    public void setSessions(Event event, List<Session> newSessions) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new SessionDiff(sessions, newSessions));
        // Rows mark times against the event's, so they all redraw if those changed.
        boolean timesChanged = this.event != null
                && (!this.event.getStartTime().equals(event.getStartTime())
                        || !this.event.getEndTime().equals(event.getEndTime()));
        this.event = event;
        sessions = newSessions;
        diff.dispatchUpdatesTo(this);
        if (timesChanged) {
            notifyItemRangeChanged(0, sessions.size());
        }
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Session session = sessions.get(position);
        SessionViewHolder row = (SessionViewHolder) holder;
        Context context = row.itemView.getContext();
        String start = showTime(row.startText, row.startNextDay, session.getStartTime());
        String end = showTime(row.endText, row.endNextDay, session.getEndTime());
        String type = context.getString(session.getType().getLabelRes());
        String details = details(context, session);
        row.nameText.setText(session.getName());
        row.typeLabel.setText(type);
        row.detailsText.setText(details);
        row.detailsText.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
        // TalkBack reads the row as one item, as on the cards.
        row.itemView.setContentDescription(details.isEmpty()
                ? context.getString(R.string.description_session, start, end, type,
                        session.getName())
                : context.getString(R.string.description_session_details, start, end, type,
                        session.getName(), details));
        row.itemView.setOnClickListener(v -> listener.onSessionClick(session));
    }

    /** Shows a session time, with +1 on the next day, and returns how TalkBack reads it. */
    private String showTime(TextView timeText, View nextDayMarker, LocalTime time) {
        String text = EventTimes.format(time);
        timeText.setText(text);
        // Within the event, only a time after midnight comes before the event's start.
        boolean nextDay = time.isBefore(event.getStartTime());
        // Kept as a space in an event past midnight, so the names line up below a +1.
        nextDayMarker.setVisibility(nextDay ? View.VISIBLE
                : event.endsNextDay() ? View.INVISIBLE : View.GONE);
        return nextDay ? timeText.getContext().getString(R.string.time_next_day, text) : text;
    }

    /** Joins a session's speaker and room, leaving out either one that was not given. */
    private static String details(Context context, Session session) {
        StringBuilder details = new StringBuilder(session.getSpeaker());
        if (!session.getRoom().isEmpty()) {
            if (details.length() > 0) {
                details.append(context.getString(R.string.session_details_separator));
            }
            details.append(session.getRoom());
        }
        return details.toString();
    }

    /** Matches sessions by ID, so an edited one is redrawn in place rather than replaced. */
    private static final class SessionDiff extends DiffUtil.Callback {

        private final List<Session> oldSessions;
        private final List<Session> newSessions;

        SessionDiff(List<Session> oldSessions, List<Session> newSessions) {
            this.oldSessions = oldSessions;
            this.newSessions = newSessions;
        }

        @Override
        public int getOldListSize() {
            return oldSessions.size();
        }

        @Override
        public int getNewListSize() {
            return newSessions.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPosition, int newPosition) {
            return oldSessions.get(oldPosition).getId() == newSessions.get(newPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldPosition, int newPosition) {
            return oldSessions.get(oldPosition).equals(newSessions.get(newPosition));
        }
    }

    /** Called when a session is tapped. */
    public interface OnSessionClickListener {
        void onSessionClick(Session session);
    }

    /** The views of one session row. */
    private static final class SessionViewHolder extends RecyclerView.ViewHolder {

        private final TextView startText;
        private final View startNextDay;
        private final TextView endText;
        private final View endNextDay;
        private final TextView nameText;
        private final TextView typeLabel;
        private final TextView detailsText;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            startText = itemView.findViewById(R.id.session_start);
            startNextDay = itemView.findViewById(R.id.session_start_next_day);
            endText = itemView.findViewById(R.id.session_end);
            endNextDay = itemView.findViewById(R.id.session_end_next_day);
            nameText = itemView.findViewById(R.id.session_name);
            typeLabel = itemView.findViewById(R.id.session_type);
            detailsText = itemView.findViewById(R.id.session_details);
        }
    }
}
