package com.comp1786.coursework.eventhub.admin;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Makes an event's QR code, which a phone camera reads as text, with no app needed.
 */
public final class EventQrCode {

    private EventQrCode() {
    }

    /** Returns the event and its sessions as lines of text, in the order a reader needs. */
    public static String textOf(Resources res, Event event, List<Session> sessions) {
        List<String> lines = new ArrayList<>();
        lines.add(event.getName());
        // A printed code never changes, so of the statuses only Cancelled stays true.
        String status = event.getStatus() == EventStatus.CANCELLED
                ? res.getString(event.getStatus().getLabelRes()) : "";
        lines.add(joinFilled(res, res.getString(event.getType().getLabelRes()), status));

        lines.add("");
        lines.add(res.getString(R.string.qr_when, EventDates.formatLong(event.getDate()),
                timeRange(res, event, event.getStartTime(), event.getEnd())));
        lines.add(res.getString(R.string.qr_where, event.getLocation()));

        if (!event.getDescription().isEmpty()) {
            lines.add("");
            lines.add(event.getDescription());
        }

        if (!sessions.isEmpty()) {
            lines.add("");
            lines.add(res.getString(R.string.qr_sessions, sessions.size()));
            for (Session session : sessions) {
                lines.add(res.getString(R.string.qr_session, timeRange(res, event,
                        session.getStartTime(), session.getEnd(event)), session.getName()));
                lines.add(res.getString(R.string.qr_session_details, joinFilled(res,
                        res.getString(session.getType().getLabelRes()), session.getRoom(),
                        session.getSpeaker())));
            }
        }

        lines.add("");
        lines.add(res.getString(R.string.qr_organizer, event.getOrganizer()));
        addIfFilled(lines, res, R.string.qr_email, event.getContactEmail());
        addIfFilled(lines, res, R.string.qr_phone, event.getContactPhone());
        return String.join("\n", lines);
    }

    /** Draws the text as a QR code, size pixels square, or returns null if it is too long. */
    @Nullable
    public static Bitmap encode(String text, int size) {
        BitMatrix grid;
        try {
            // ZXing reads text as ISO-8859-1 unless told, which garbles accented names.
            grid = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
        } catch (WriterException e) {
            return null;
        }
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = grid.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    /** Returns a range such as "09:00–17:30", marked when it ends after the event's date. */
    private static String timeRange(Resources res, Event event, LocalTime start,
            LocalDateTime end) {
        String range = res.getString(R.string.time_range,
                EventTimes.format(start), EventTimes.format(end.toLocalTime()));
        return end.toLocalDate().isAfter(event.getDate())
                ? res.getString(R.string.qr_next_day, range) : range;
    }

    /** Joins the details that are filled in, such as "Workshop · Room 010". */
    private static String joinFilled(Resources res, String... details) {
        List<String> filled = new ArrayList<>();
        for (String detail : details) {
            if (!detail.isEmpty()) {
                filled.add(detail);
            }
        }
        return String.join(res.getString(R.string.qr_separator), filled);
    }

    private static void addIfFilled(List<String> lines, Resources res, @StringRes int lineRes,
            String value) {
        if (!value.isEmpty()) {
            lines.add(res.getString(lineRes, value));
        }
    }
}
