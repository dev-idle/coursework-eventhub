package com.comp1786.coursework.eventhub.admin;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * What the event list is narrowed to: a date, an organizer and a status, each left out when null.
 */
public class EventFilter implements Serializable {

    @Nullable
    private LocalDate date;
    @Nullable
    private String organizer;
    @Nullable
    private EventStatus status;

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
        return date == null && organizer == null && status == null;
    }
}
