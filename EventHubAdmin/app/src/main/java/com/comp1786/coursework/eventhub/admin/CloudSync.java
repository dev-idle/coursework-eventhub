package com.comp1786.coursework.eventhub.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads the saved events and their sessions to Firebase, replacing the copy in the cloud.
 */
public final class CloudSync {

    private static final String TAG = "CloudSync";
    private static final String PREFERENCES = "cloud_sync";
    private static final String KEY_AUTO_SYNC = "auto_sync";

    // One thread, so uploads run in turn and the last one sent has the latest events.
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private CloudSync() {
    }

    /** Receives the result of an upload, on the main thread. */
    public interface UploadListener {

        void onUploaded(int count);

        void onFailed();
    }

    /** Returns whether the active network has been checked to reach the internet. */
    public static boolean isOnline(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public static boolean isAutoSync(Context context) {
        return preferences(context).getBoolean(KEY_AUTO_SYNC, true);
    }

    /** Turns automatic sync on or off, uploading at once when it comes on. */
    public static void setAutoSync(Context context, boolean autoSync) {
        preferences(context).edit().putBoolean(KEY_AUTO_SYNC, autoSync).apply();
        syncChanges(context);
    }

    /** Uploads every saved event at once and tells the listener how it went. */
    public static void upload(Context context, UploadListener listener) {
        send(context.getApplicationContext(), listener);
    }

    /** Uploads every saved event after a change, if automatic sync is on. */
    public static void syncChanges(Context context) {
        if (isAutoSync(context)) {
            send(context.getApplicationContext(), null);
        }
    }

    /** Reads the events on the upload thread, then writes them over the cloud copy. */
    private static void send(Context context, @Nullable UploadListener listener) {
        executor.execute(() -> {
            Map<String, Object> events = readEvents(context);
            Task<Void> write;
            try {
                // Waits here, so the next upload neither signs in again nor overtakes this one.
                signIn();
                write = FirebaseDatabase.getInstance().getReference("events").setValue(events);
            } catch (ExecutionException | InterruptedException e) {
                write = Tasks.forException(e);
            }
            write.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (listener != null) {
                        listener.onUploaded(events.size());
                    }
                } else {
                    Log.w(TAG, "Upload failed", task.getException());
                    if (listener != null) {
                        listener.onFailed();
                    }
                }
            });
        });
    }

    /** Signs in with an anonymous account, which the database rules ask for before a write. */
    private static void signIn() throws ExecutionException, InterruptedException {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        // The account is kept between launches, so this signs in only once.
        if (auth.getCurrentUser() == null) {
            Tasks.await(auth.signInAnonymously());
        }
    }

    /** Returns every saved event as the cloud stores it, each with its sessions, by key. */
    private static Map<String, Object> readEvents(Context context) {
        Map<String, Object> events = new HashMap<>();
        try (EventDatabase database = new EventDatabase(context)) {
            for (Event event : database.getAllEvents()) {
                Map<String, Object> sessions = new HashMap<>();
                for (Session session : database.getSessions(event)) {
                    sessions.put("s" + session.getId(), toValues(session));
                }
                Map<String, Object> values = toValues(event);
                values.put("sessions", sessions);
                // Keys start with a letter, so Firebase does not read the IDs as an array.
                events.put("e" + event.getId(), values);
            }
        }
        return events;
    }

    private static Map<String, Object> toValues(Event event) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", event.getName());
        values.put("date", event.getDate().toString());
        values.put("startTime", EventTimes.format(event.getStartTime()));
        values.put("endTime", EventTimes.format(event.getEndTime()));
        values.put("location", event.getLocation());
        values.put("organizer", event.getOrganizer());
        values.put("type", event.getType().name());
        values.put("status", event.getStatus().name());
        values.put("description", event.getDescription());
        values.put("contactEmail", event.getContactEmail());
        values.put("contactPhone", event.getContactPhone());
        return values;
    }

    private static Map<String, Object> toValues(Session session) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", session.getName());
        values.put("startTime", EventTimes.format(session.getStartTime()));
        values.put("endTime", EventTimes.format(session.getEndTime()));
        values.put("type", session.getType().name());
        values.put("speaker", session.getSpeaker());
        values.put("room", session.getRoom());
        values.put("description", session.getDescription());
        return values;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
