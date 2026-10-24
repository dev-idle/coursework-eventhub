package com.comp1786.coursework.eventhub.admin;

import android.content.res.Resources;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes event times in the 24-hour HH:MM format.
 */
public final class EventTimes {

    private static final int MINUTES_PER_HOUR = 60;
    private static final int MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;
    // Times are offered every 15 minutes, so a day has 96 of them.
    private static final int SLOT_MINUTES = 15;
    private static final int SLOTS_PER_DAY = MINUTES_PER_DAY / SLOT_MINUTES;

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private EventTimes() {
    }

    /** Returns the time for text such as "09:30", or null if the text is not a time. */
    @Nullable
    public static LocalTime parse(String text) {
        try {
            return LocalTime.parse(text, FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Formats a time as HH:MM, such as "09:30". */
    public static String format(LocalTime time) {
        return time.format(FORMAT);
    }

    /** Returns every time on offer: 00:00, 00:15 and so on to 23:45. */
    public static List<LocalTime> allSlots() {
        return slotsFrom(LocalTime.MIDNIGHT, SLOTS_PER_DAY);
    }

    /** Returns the end times on offer, from 15 min to 23 hr 45 min after the start. */
    public static List<LocalTime> slotsAfter(LocalTime start) {
        return slotsFrom(start.plusMinutes(SLOT_MINUTES), SLOTS_PER_DAY - 1);
    }

    /** Returns the times on offer from first to last, both included, across midnight too. */
    public static List<LocalTime> slotsBetween(LocalTime first, LocalTime last) {
        long minutes = first.equals(last) ? 0 : minutesBetween(first, last);
        return slotsFrom(first, (int) (minutes / SLOT_MINUTES) + 1);
    }

    /** Returns the time a slot after this one, the earliest end for a start. */
    public static LocalTime nextSlot(LocalTime time) {
        return time.plusMinutes(SLOT_MINUTES);
    }

    /** Returns the time a slot before this one, the latest start for an end. */
    public static LocalTime previousSlot(LocalTime time) {
        return time.minusMinutes(SLOT_MINUTES);
    }

    /** Formats each time as HH:MM, for a list of times to choose from. */
    public static String[] format(List<LocalTime> times) {
        String[] labels = new String[times.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = format(times.get(i));
        }
        return labels;
    }

    /** Returns true when the end is at or before the start, so it falls on the next day. */
    public static boolean endsNextDay(LocalTime start, LocalTime end) {
        return !end.isAfter(start);
    }

    /** Returns when an event on this date ends, on the next day if it runs past midnight. */
    public static LocalDateTime endOn(LocalDate date, LocalTime start, LocalTime end) {
        LocalDateTime endOnDate = date.atTime(end);
        return endsNextDay(start, end) ? endOnDate.plusDays(1) : endOnDate;
    }

    /** Returns the minutes from start to end, counting an end on the next day. */
    public static long minutesBetween(LocalTime start, LocalTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        return endsNextDay(start, end) ? minutes + MINUTES_PER_DAY : minutes;
    }

    /** Returns true when start to end lies within from to to, both runs counted across midnight. */
    public static boolean isWithin(LocalTime start, LocalTime end, LocalTime from, LocalTime to) {
        long startOffset = start.equals(from) ? 0 : minutesBetween(from, start);
        return startOffset + minutesBetween(start, end) <= minutesBetween(from, to);
    }

    /** Formats the length of an event, such as "45 min", "4 hr" or "1 hr 30 min". */
    public static String formatLength(Resources res, LocalTime start, LocalTime end) {
        long minutes = minutesBetween(start, end);
        long hours = minutes / MINUTES_PER_HOUR;
        long rest = minutes % MINUTES_PER_HOUR;
        if (hours == 0) {
            return res.getString(R.string.event_length_minutes, rest);
        } else if (rest == 0) {
            return res.getString(R.string.event_length_hours, hours);
        }
        return res.getString(R.string.event_length_hours_minutes, hours, rest);
    }

    private static List<LocalTime> slotsFrom(LocalTime first, int count) {
        List<LocalTime> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // LocalTime wraps at midnight, so the list can run into the next day.
            slots.add(first.plusMinutes((long) i * SLOT_MINUTES));
        }
        return slots;
    }
}
