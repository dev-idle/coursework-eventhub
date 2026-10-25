package com.comp1786.coursework.eventhub.admin;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves events in SQLite, as the module brief's database helper does.
 */
public class EventDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "events.db";
    private static final int DATABASE_VERSION = 4;

    // Dates are stored as YYYY-MM-DD and times as HH:MM, so text order is time order.
    // The optional details are empty strings when not given.
    private static final String CREATE_TABLE = "CREATE TABLE Event ("
            + "ID INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "Name TEXT NOT NULL, "
            + "Date TEXT NOT NULL, "
            + "StartTime TEXT NOT NULL, "
            + "EndTime TEXT NOT NULL, "
            + "Location TEXT NOT NULL, "
            + "Organizer TEXT NOT NULL, "
            + "Type TEXT NOT NULL, "
            + "Status TEXT NOT NULL, "
            + "Description TEXT NOT NULL, "
            + "ContactEmail TEXT NOT NULL, "
            + "ContactPhone TEXT NOT NULL)";
    private static final String INSERT = "INSERT INTO Event (Name, Date, StartTime, EndTime, "
            + "Location, Organizer, Type, Status, Description, ContactEmail, ContactPhone) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE Event SET Name = ?, Date = ?, StartTime = ?, "
            + "EndTime = ?, Location = ?, Organizer = ?, Type = ?, Status = ?, Description = ?, "
            + "ContactEmail = ?, ContactPhone = ? WHERE ID = ?";
    // The ID comes last, as in UPDATE, so the details bind the same way.
    private static final String RESTORE = "INSERT INTO Event (Name, Date, StartTime, EndTime, "
            + "Location, Organizer, Type, Status, Description, ContactEmail, ContactPhone, ID) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE = "DELETE FROM Event WHERE ID = ?";
    private static final String SELECT = "SELECT ID, Name, Date, StartTime, EndTime, Location, "
            + "Organizer, Type, Status, Description, ContactEmail, ContactPhone FROM Event ";
    private static final String START_ORDER = "ORDER BY Date, StartTime, ID";
    private static final String SELECT_BY_ID = SELECT + "WHERE ID = ?";
    // One of each organizer, however it was capitalized, in alphabetical order.
    private static final String SELECT_ORGANIZERS = "SELECT Organizer FROM Event "
            + "GROUP BY Organizer COLLATE NOCASE ORDER BY Organizer COLLATE NOCASE";
    // A session's times fall within its event, and the session is deleted with the event.
    private static final String CREATE_SESSION_TABLE = "CREATE TABLE Session ("
            + "ID INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "EventID INTEGER NOT NULL REFERENCES Event(ID) ON DELETE CASCADE, "
            + "Name TEXT NOT NULL, "
            + "StartTime TEXT NOT NULL, "
            + "EndTime TEXT NOT NULL, "
            + "Type TEXT NOT NULL, "
            + "Speaker TEXT NOT NULL, "
            + "Room TEXT NOT NULL, "
            + "Description TEXT NOT NULL)";
    // Finds an event's sessions without reading the whole table, also when it is deleted.
    private static final String CREATE_SESSION_INDEX =
            "CREATE INDEX SessionEventID ON Session (EventID)";
    private static final String INSERT_SESSION = "INSERT INTO Session (EventID, Name, "
            + "StartTime, EndTime, Type, Speaker, Room, Description) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SESSION = "UPDATE Session SET EventID = ?, Name = ?, "
            + "StartTime = ?, EndTime = ?, Type = ?, Speaker = ?, Room = ?, Description = ? "
            + "WHERE ID = ?";
    private static final String RESTORE_SESSION = "INSERT INTO Session (EventID, Name, "
            + "StartTime, EndTime, Type, Speaker, Room, Description, ID) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_SESSION = "DELETE FROM Session WHERE ID = ?";
    private static final String SELECT_SESSION = "SELECT ID, EventID, Name, StartTime, "
            + "EndTime, Type, Speaker, Room, Description FROM Session ";
    // Sessions that start before the event's start time are on the next day, so they go last.
    private static final String SELECT_SESSIONS = SELECT_SESSION
            + "WHERE EventID = ? ORDER BY StartTime < ?, StartTime, ID";
    private static final String SELECT_SESSION_BY_ID = SELECT_SESSION + "WHERE ID = ?";
    private static final String COUNT_SESSIONS =
            "SELECT EventID, COUNT(*) FROM Session GROUP BY EventID";
    // An event ends on the next day when its end time is not after its start time.
    private static final String COMPLETE_ENDED = "UPDATE Event SET Status = ? "
            + "WHERE Status = ? AND (CASE WHEN EndTime > StartTime THEN Date "
            + "ELSE date(Date, '+1 day') END) || ' ' || EndTime <= ?";

    public EventDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        // SQLite leaves foreign keys off unless asked, and ON DELETE CASCADE needs them.
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
        db.execSQL(CREATE_SESSION_TABLE);
        db.execSQL(CREATE_SESSION_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Version 2 adds the status, and events saved before it count as upcoming.
            db.execSQL("ALTER TABLE Event ADD COLUMN Status TEXT NOT NULL DEFAULT 'UPCOMING'");
        }
        if (oldVersion < 3) {
            // Version 3 adds the optional details, which events saved before it leave empty.
            db.execSQL("ALTER TABLE Event ADD COLUMN Description TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE Event ADD COLUMN ContactEmail TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE Event ADD COLUMN ContactPhone TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 4) {
            // Version 4 adds the sessions of each event.
            db.execSQL(CREATE_SESSION_TABLE);
            db.execSQL(CREATE_SESSION_INDEX);
        }
    }

    /** Saves a new event and returns its ID. */
    public long insertEvent(Event event) {
        try (SQLiteStatement statement = getWritableDatabase().compileStatement(INSERT)) {
            bindDetails(statement, event);
            return statement.executeInsert();
        }
    }

    /** Saves the changes to an event that is already saved. */
    public void updateEvent(Event event) {
        try (SQLiteStatement statement = getWritableDatabase().compileStatement(UPDATE)) {
            bindDetails(statement, event);
            statement.bindLong(12, event.getId());
            statement.executeUpdateDelete();
        }
    }

    /** Deletes the event with this ID. */
    public void deleteEvent(long id) {
        try (SQLiteStatement statement = getWritableDatabase().compileStatement(DELETE)) {
            statement.bindLong(1, id);
            statement.executeUpdateDelete();
        }
    }

    /** Deletes these events in one transaction, so either all of them go or none do. */
    public void deleteEvents(List<Event> events) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (SQLiteStatement statement = db.compileStatement(DELETE)) {
            for (Event event : events) {
                statement.bindLong(1, event.getId());
                statement.executeUpdateDelete();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Saves deleted events and their sessions again under their IDs, in one transaction. */
    public void restoreEvents(List<Event> events, List<Session> sessions) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (SQLiteStatement eventStatement = db.compileStatement(RESTORE);
                SQLiteStatement sessionStatement = db.compileStatement(RESTORE_SESSION)) {
            for (Event event : events) {
                bindDetails(eventStatement, event);
                eventStatement.bindLong(12, event.getId());
                eventStatement.executeInsert();
            }
            // After the events, as each session needs its event to exist.
            for (Session session : sessions) {
                bindSessionDetails(sessionStatement, session);
                sessionStatement.bindLong(9, session.getId());
                sessionStatement.executeInsert();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Marks upcoming events that have ended by the given time as completed. */
    public void completeEndedEvents(LocalDateTime now) {
        try (SQLiteStatement statement =
                getWritableDatabase().compileStatement(COMPLETE_ENDED)) {
            statement.bindString(1, EventStatus.COMPLETED.name());
            statement.bindString(2, EventStatus.UPCOMING.name());
            statement.bindString(3,
                    now.toLocalDate() + " " + EventTimes.format(now.toLocalTime()));
            statement.executeUpdateDelete();
        }
    }

    /** Returns every saved event in start order. */
    public List<Event> getAllEvents() {
        return getEvents(new EventFilter());
    }

    /** Returns the saved events the filter keeps, in start order. */
    public List<Event> getEvents(EventFilter filter) {
        List<String> conditions = new ArrayList<>();
        List<String> args = new ArrayList<>();
        if (!filter.getText().isEmpty()) {
            addWordStart(conditions, args, filter.getText(), "Name", "Description");
        }
        if (filter.getDate() != null) {
            conditions.add("Date = ?");
            args.add(filter.getDate().toString());
        }
        if (filter.getOrganizer() != null) {
            conditions.add("Organizer = ? COLLATE NOCASE");
            args.add(filter.getOrganizer());
        }
        if (filter.getStatus() != null) {
            conditions.add("Status = ?");
            args.add(filter.getStatus().name());
        }
        // Only these fixed conditions are joined into the query; the values are bound to it.
        String where = conditions.isEmpty()
                ? "" : "WHERE " + String.join(" AND ", conditions) + " ";
        List<Event> events = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase()
                .rawQuery(SELECT + where + START_ORDER, args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                events.add(readEvent(cursor));
            }
        }
        return events;
    }

    /** Returns the saved event with this ID, or null if there is none. */
    @Nullable
    public Event getEvent(long id) {
        try (Cursor cursor = getReadableDatabase()
                .rawQuery(SELECT_BY_ID, new String[] {String.valueOf(id)})) {
            return cursor.moveToFirst() ? readEvent(cursor) : null;
        }
    }

    /** Saves a new session and returns its ID. */
    public long insertSession(Session session) {
        try (SQLiteStatement statement = getWritableDatabase().compileStatement(INSERT_SESSION)) {
            bindSessionDetails(statement, session);
            return statement.executeInsert();
        }
    }

    /** Saves the changes to a session that is already saved. */
    public void updateSession(Session session) {
        try (SQLiteStatement statement = getWritableDatabase().compileStatement(UPDATE_SESSION)) {
            bindSessionDetails(statement, session);
            statement.bindLong(9, session.getId());
            statement.executeUpdateDelete();
        }
    }

    /** Deletes the session with this ID. */
    public void deleteSession(long id) {
        try (SQLiteStatement statement = getWritableDatabase().compileStatement(DELETE_SESSION)) {
            statement.bindLong(1, id);
            statement.executeUpdateDelete();
        }
    }

    /** Saves a deleted session again under its ID, to undo a delete. */
    public void restoreSession(Session session) {
        try (SQLiteStatement statement =
                getWritableDatabase().compileStatement(RESTORE_SESSION)) {
            bindSessionDetails(statement, session);
            statement.bindLong(9, session.getId());
            statement.executeInsert();
        }
    }

    /** Returns the saved session with this ID, or null if there is none. */
    @Nullable
    public Session getSession(long id) {
        try (Cursor cursor = getReadableDatabase()
                .rawQuery(SELECT_SESSION_BY_ID, new String[] {String.valueOf(id)})) {
            return cursor.moveToFirst() ? readSession(cursor) : null;
        }
    }

    /** Returns the sessions of an event in the order they take place. */
    public List<Session> getSessions(Event event) {
        List<Session> sessions = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(SELECT_SESSIONS, new String[] {
                String.valueOf(event.getId()), EventTimes.format(event.getStartTime())})) {
            while (cursor.moveToNext()) {
                sessions.add(readSession(cursor));
            }
        }
        return sessions;
    }

    /** Returns the sessions of all these events, to save them again if the events are restored. */
    public List<Session> getSessions(List<Event> events) {
        List<Session> sessions = new ArrayList<>();
        for (Event event : events) {
            sessions.addAll(getSessions(event));
        }
        return sessions;
    }

    /** Returns how many sessions each event has, by event ID, without events that have none. */
    public Map<Long, Integer> getSessionCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(COUNT_SESSIONS, null)) {
            while (cursor.moveToNext()) {
                counts.put(cursor.getLong(0), cursor.getInt(1));
            }
        }
        return counts;
    }

    /** Keeps the rows where a word in one of the columns starts with the text, in any case. */
    private static void addWordStart(List<String> conditions, List<String> args, String text,
            String... columns) {
        // Typed wildcards are escaped, so % and _ match only themselves.
        String start = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        List<String> matches = new ArrayList<>();
        for (String column : columns) {
            matches.add(column + " LIKE ? ESCAPE '\\' OR " + column + " LIKE ? ESCAPE '\\'");
            args.add(start + "%");
            args.add("% " + start + "%");
        }
        conditions.add("(" + String.join(" OR ", matches) + ")");
    }

    /** Returns the organizers of the saved events, for the organizer filter. */
    public List<String> getOrganizers() {
        List<String> organizers = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(SELECT_ORGANIZERS, null)) {
            while (cursor.moveToNext()) {
                organizers.add(cursor.getString(0));
            }
        }
        return organizers;
    }

    /** Returns how many events are saved. */
    public long countEvents() {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), "Event");
    }

    /** Reads the event in the cursor's current row, whose columns are in the order of SELECT. */
    private static Event readEvent(Cursor cursor) {
        return new Event(cursor.getLong(0), cursor.getString(1),
                LocalDate.parse(cursor.getString(2)), EventTimes.parse(cursor.getString(3)),
                EventTimes.parse(cursor.getString(4)), cursor.getString(5), cursor.getString(6),
                EventType.valueOf(cursor.getString(7)), EventStatus.valueOf(cursor.getString(8)),
                cursor.getString(9), cursor.getString(10), cursor.getString(11));
    }

    /** Binds the event's details to parameters 1 to 11, in the order of the Event columns. */
    private static void bindDetails(SQLiteStatement statement, Event event) {
        statement.bindString(1, event.getName());
        statement.bindString(2, event.getDate().toString());
        statement.bindString(3, EventTimes.format(event.getStartTime()));
        statement.bindString(4, EventTimes.format(event.getEndTime()));
        statement.bindString(5, event.getLocation());
        statement.bindString(6, event.getOrganizer());
        statement.bindString(7, event.getType().name());
        statement.bindString(8, event.getStatus().name());
        statement.bindString(9, event.getDescription());
        statement.bindString(10, event.getContactEmail());
        statement.bindString(11, event.getContactPhone());
    }

    /** Reads the session in the cursor's current row, in the column order of SELECT_SESSION. */
    private static Session readSession(Cursor cursor) {
        return new Session(cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                EventTimes.parse(cursor.getString(3)), EventTimes.parse(cursor.getString(4)),
                SessionType.valueOf(cursor.getString(5)), cursor.getString(6),
                cursor.getString(7), cursor.getString(8));
    }

    /** Binds the session's details to parameters 1 to 8, in the order of the Session columns. */
    private static void bindSessionDetails(SQLiteStatement statement, Session session) {
        statement.bindLong(1, session.getEventId());
        statement.bindString(2, session.getName());
        statement.bindString(3, EventTimes.format(session.getStartTime()));
        statement.bindString(4, EventTimes.format(session.getEndTime()));
        statement.bindString(5, session.getType().name());
        statement.bindString(6, session.getSpeaker());
        statement.bindString(7, session.getRoom());
        statement.bindString(8, session.getDescription());
    }
}
