package com.comp1786.coursework.eventhub.admin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists an event's sessions as cards like the event cards, with the times in the side panel.
 */
public class SessionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final OnSessionClickListener listener;
    private List<Row> rows = new ArrayList<>();
    private Event event;

    public SessionAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    /** Shows the sessions of an event in the order they take place, colored as of now. */
    public void setSessions(Event event, List<Session> sessions, LocalDateTime now) {
        List<Row> newRows = new ArrayList<>();
        LocalDateTime nextStart = EventCardState.nextStart(event, sessions, now);
        for (Session session : sessions) {
            newRows.add(new Row(session, EventCardState.at(event, session, now, nextStart)));
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new RowDiff(rows, newRows));
        // Rows mark times against the event's, so they all redraw if those changed.
        boolean timesChanged = this.event != null
                && (!this.event.getStartTime().equals(event.getStartTime())
                        || !this.event.getEndTime().equals(event.getEndTime()));
        this.event = event;
        rows = newRows;
        diff.dispatchUpdatesTo(this);
        if (timesChanged) {
            notifyItemRangeChanged(0, rows.size());
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
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
        Row row = rows.get(position);
        Session session = row.session;
        SessionViewHolder card = (SessionViewHolder) holder;
        Context context = card.itemView.getContext();
        card.background.setDatePanelColor(
                MaterialColors.getColor(card.itemView, row.state.getDatePanelColorAttr()));
        int panelTextColor =
                MaterialColors.getColor(card.itemView, row.state.getDatePanelTextColorAttr());
        String start = showTime(card.startText, card.startNextDay, session.getStartTime(),
                panelTextColor);
        String end = showTime(card.endText, card.endNextDay, session.getEndTime(),
                panelTextColor);
        String type = context.getString(session.getType().getLabelRes());
        String speaker = orElse(session.getSpeaker(), context.getString(R.string.no_speaker));
        String room = orElse(session.getRoom(), context.getString(R.string.no_room));
        card.nameText.setText(session.getName());
        card.typeLabel.setText(type);
        card.speakerText.setText(speaker);
        card.roomText.setText(room);
        // TalkBack reads the card as one item, as on the event cards.
        card.itemView.setContentDescription(context.getString(R.string.description_session,
                start, end, type, session.getName(), speaker, room));
        card.itemView.setOnClickListener(v -> listener.onSessionClick(session));
    }

    /** Shows a session time, with +1 on the next day, and returns how TalkBack reads it. */
    private String showTime(TextView timeText, TextView nextDayMarker, LocalTime time,
            int color) {
        String text = EventTimes.format(time);
        timeText.setText(text);
        timeText.setTextColor(color);
        nextDayMarker.setTextColor(color);
        // Within the event, only a time after midnight comes before the event's start.
        boolean nextDay = time.isBefore(event.getStartTime());
        // Kept as a space in an event past midnight, so the times line up above one another.
        nextDayMarker.setVisibility(nextDay ? View.VISIBLE
                : event.endsNextDay() ? View.INVISIBLE : View.GONE);
        return nextDay ? timeText.getContext().getString(R.string.time_next_day, text) : text;
    }

    private static String orElse(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    /** A session and how its card is colored. */
    private static final class Row {

        private final Session session;
        private final EventCardState state;

        Row(Session session, EventCardState state) {
            this.session = session;
            this.state = state;
        }
    }

    /** Matches rows by session ID, so an edited session is redrawn in place. */
    private static final class RowDiff extends DiffUtil.Callback {

        private final List<Row> oldRows;
        private final List<Row> newRows;

        RowDiff(List<Row> oldRows, List<Row> newRows) {
            this.oldRows = oldRows;
            this.newRows = newRows;
        }

        @Override
        public int getOldListSize() {
            return oldRows.size();
        }

        @Override
        public int getNewListSize() {
            return newRows.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPosition, int newPosition) {
            return oldRows.get(oldPosition).session.getId()
                    == newRows.get(newPosition).session.getId();
        }

        @Override
        public boolean areContentsTheSame(int oldPosition, int newPosition) {
            Row oldRow = oldRows.get(oldPosition);
            Row newRow = newRows.get(newPosition);
            // A card changes color as time passes, and its details when it is edited.
            return oldRow.state == newRow.state && oldRow.session.equals(newRow.session);
        }
    }

    /** Called when a session is tapped. */
    public interface OnSessionClickListener {
        void onSessionClick(Session session);
    }

    /** The views of one session card. */
    private static final class SessionViewHolder extends RecyclerView.ViewHolder {

        private final EventCardDrawable background;
        private final TextView startText;
        private final TextView startNextDay;
        private final TextView endText;
        private final TextView endNextDay;
        private final TextView nameText;
        private final TextView typeLabel;
        private final TextView speakerText;
        private final TextView roomText;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            background = new EventCardDrawable(itemView.getContext());
            int rippleColor = MaterialColors.getColor(
                    itemView, androidx.appcompat.R.attr.colorControlHighlight);
            // Without a mask, the ripple is clipped to the card shape.
            itemView.setBackground(
                    new RippleDrawable(ColorStateList.valueOf(rippleColor), background, null));
            startText = itemView.findViewById(R.id.session_start);
            startNextDay = itemView.findViewById(R.id.session_start_next_day);
            endText = itemView.findViewById(R.id.session_end);
            endNextDay = itemView.findViewById(R.id.session_end_next_day);
            nameText = itemView.findViewById(R.id.session_name);
            typeLabel = itemView.findViewById(R.id.session_type);
            speakerText = itemView.findViewById(R.id.session_speaker);
            roomText = itemView.findViewById(R.id.session_room);
        }
    }
}
