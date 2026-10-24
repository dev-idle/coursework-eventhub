package com.comp1786.coursework.eventhub.admin;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A session within an event, Serializable so activities can pass it in an Intent.
 */
public final class Session implements Serializable {

    /** The ID of a session that has not been saved yet. */
    public static final long NO_ID = 0;

    private static final long serialVersionUID = 1L;

    private final long id;
    private final long eventId;
    private final String name;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final SessionType type;
    // Optional details, empty when not given.
    private final String speaker;
    private final String room;
    private final String description;

    public Session(long id, long eventId, String name, LocalTime startTime, LocalTime endTime,
            SessionType type, String speaker, String room, String description) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
        this.speaker = speaker;
        this.room = room;
        this.description = description;
    }

    public long getId() {
        return id;
    }

    public long getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    /** Returns when the session starts, on the day after the event's date if past midnight. */
    public LocalDateTime getStart(Event event) {
        LocalDateTime start = event.getDate().atTime(startTime);
        // Within the event, only a time after midnight comes before the event's start.
        return startTime.isBefore(event.getStartTime()) ? start.plusDays(1) : start;
    }

    public SessionType getType() {
        return type;
    }

    public String getSpeaker() {
        return speaker;
    }

    public String getRoom() {
        return room;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Session)) {
            return false;
        }
        Session other = (Session) o;
        return id == other.id && eventId == other.eventId && name.equals(other.name)
                && startTime.equals(other.startTime) && endTime.equals(other.endTime)
                && type == other.type && speaker.equals(other.speaker)
                && room.equals(other.room) && description.equals(other.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, eventId, name, startTime, endTime, type, speaker, room,
                description);
    }
}
