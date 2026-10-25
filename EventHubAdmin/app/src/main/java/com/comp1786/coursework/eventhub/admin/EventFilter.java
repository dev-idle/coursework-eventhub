package com.comp1786.coursework.eventhub.admin;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * What the event list is narrowed to: words to search for, a date, an organizer and a status.
 * Each is left out when empty or null.
 */
public class EventFilter implements Serializable {

    private String text = "";
    @Nullable
    private LocalDate date;
    @Nullable
    private String organizer;
    @Nullable
    private EventStatus status;

    public String getText() {
        return text;
    }

    /** Sets the text to search for, ignoring spaces at either end. */
    public void setText(String text) {
        this.text = text.trim();
    }

    @Nullable
    public LocalDate getDate() {
        return date;
    }

    public void setDate(@Nullable LocalDate date) {
        this.date = date;
    }

    @Nullable
    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(@Nullable String organizer) {
        this.organizer = organizer;
    }

    @Nullable
    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(@Nullable EventStatus status) {
        this.status = status;
    }

    /** Returns true when nothing is set, so every event is shown. */
    public boolean isEmpty() {
        return text.isEmpty() && date == null && organizer == null && status == null;
    }
}
