# EventHub

Coursework for COMP1786 Mobile Application Design and Development. Two apps
share one Firebase Realtime Database. Organizers use EventHub Admin, a native
Android app, to manage events and their sessions on the phone and upload them.
Attendees use EventHub, a .NET MAUI app, to browse the uploaded events.

| Folder                | Contents                                           |
| --------------------- | -------------------------------------------------- |
| `EventHubAdmin/`      | Android Studio project for EventHub Admin, in Java |
| `EventHub/`           | Visual Studio solution for EventHub, in C#         |
| `database.rules.json` | Security rules of the Realtime Database            |

## EventHub Admin

The admin app continues the Logbook Exercise 3 event tracker.

### Features

- Sign in with the admin's Firebase account [1]. Firebase keeps the account
  between launches, and Sign out is in the More options menu.
- Events with a name, date, start and end time, location, organizer, type and
  status, plus an optional description, contact email and contact phone. A
  review screen shows the event before it is saved, with Edit to go back to
  the form.
- Sessions for each event, with a name, start and end time, type, and an
  optional speaker, room and description. Each event card shows its number of
  sessions, and deleting an event deletes its sessions.
- Search events by name and description, and filter them by date, status and
  organizer. A search matches words that start with the typed letters. Each
  event's sessions can be searched the same way.
- Upload to cloud sends every event with its sessions at once, after checking
  that the device is online [2]. With Sync automatically on, each change is
  uploaded as it is saved, on a background thread [3].
- Show QR code, in an event's More options menu, shows the event and its
  sessions as a QR code [4] that any phone camera reads as text, with no app
  needed.
- Tap a contact email or phone number to open the email app or the dialer.
- From Exercise 3: event cards under month headings, statuses that complete
  events once they end, editing, deleting with Undo, selecting several events,
  light and dark themes and two columns in landscape.

### Validation

- Name: required, and not already used on the same date, ignoring case.
- Date: required, day/month/year such as 5/3/2026 or 05/03/2026, a real date,
  and from 01/01/1900 to 31/12/2100.
- Start and end time: required. The end must differ from the start, and an
  edited event must still include all its sessions.
- Location, organizer and type: required.
- Status: Completed only once the event has ended, and Upcoming only before
  then. Cancelled is always allowed.
- Contact email: optional, checked with Android's `Patterns.EMAIL_ADDRESS`.
- Contact phone: optional, 7 to 15 digits as in E.164 [5], with an optional
  leading plus and spaces, hyphens or brackets.
- Session name and type: required. Session times are chosen from lists that
  stay within the event's times.

Error messages follow the GOV.UK Design System guidance [6].

### Database

`events.db` holds two tables [7]. `Event`:

| Column         | Type                              | Example             |
| -------------- | --------------------------------- | ------------------- |
| `ID`           | INTEGER PRIMARY KEY AUTOINCREMENT | 4                   |
| `Name`         | TEXT NOT NULL                     | Tech conference     |
| `Date`         | TEXT NOT NULL                     | 2026-11-06          |
| `StartTime`    | TEXT NOT NULL                     | 09:00               |
| `EndTime`      | TEXT NOT NULL                     | 17:30               |
| `Location`     | TEXT NOT NULL                     | Painted Hall        |
| `Organizer`    | TEXT NOT NULL                     | School of Computing |
| `Type`         | TEXT NOT NULL                     | CONFERENCE          |
| `Status`       | TEXT NOT NULL                     | UPCOMING            |
| `Description`  | TEXT NOT NULL                     | Talks and demos     |
| `ContactEmail` | TEXT NOT NULL                     | events@example.com  |
| `ContactPhone` | TEXT NOT NULL                     | 020 7946 0123       |

Optional fields hold an empty string when they are not filled in.

`Session`:

| Column        | Type                                     | Example         |
| ------------- | ---------------------------------------- | --------------- |
| `ID`          | INTEGER PRIMARY KEY AUTOINCREMENT        | 12              |
| `EventID`     | INTEGER NOT NULL, REFERENCES `Event(ID)` | 4               |
| `Name`        | TEXT NOT NULL                            | Opening keynote |
| `StartTime`   | TEXT NOT NULL                            | 09:00           |
| `EndTime`     | TEXT NOT NULL                            | 10:00           |
| `Type`        | TEXT NOT NULL                            | KEYNOTE         |
| `Speaker`     | TEXT NOT NULL                            | Dr Amy Lee      |
| `Room`        | TEXT NOT NULL                            | Painted Hall    |
| `Description` | TEXT NOT NULL                            |                 |

- Dates are stored as YYYY-MM-DD and times as HH:MM, so text order is time
  order.
- `EventID` deletes a session with its event (`ON DELETE CASCADE`), and foreign
  keys are turned on when the database opens [8]. An index on `EventID` keeps
  an event's sessions quick to find.
- Version 2 added `Status`, version 3 the description and contact columns, and
  version 4 the `Session` table. Upgrading keeps every saved event.
- Every statement binds its values, and changes to several rows run in one
  transaction.

### Cloud

- `CloudSync` reads every event and its sessions on one background thread,
  then writes them to `/events` with one `setValue` [9]. The cloud copy is
  replaced each time, so deletions reach it too. Events are stored under keys
  such as `e4`, and sessions under keys such as `s12`.
- One thread keeps uploads in order, so the last one sent has the latest
  events. The app also uploads once when it opens, which sends changes made
  while it was offline.
- The rules [10] let anyone read `/events`, so EventHub needs no account, and
  let only the admin's account write. They also check every field's type,
  format and length, and reject any other field.

## EventHub

The user app reads the events that EventHub Admin uploads.

### Features

- The events from the cloud as cards under month headings, updated while the
  app is open when the admin uploads changes.
- Search by name and description with the admin app's rule, and filters for
  favorites, date, type and status.
- An event's details and sessions, with a search of the sessions, and each
  session in full.
- Favorite events, starred on the cards or the details. They are kept in
  SQLite on the phone with all their details, so they still show offline, and
  they follow the cloud, so an event the admin deletes leaves them.
- Tap a contact email or phone number to open the email app [11] or the dialer
  [12]. The app declares both for Android 11 and later, which hides apps that
  are not declared [13].
- Light and dark themes, as in the admin app.

## Build and run

Both apps use the Firebase project `comp1786-eventhub`. Its Realtime Database is
at `https://comp1786-eventhub-default-rtdb.asia-southeast1.firebasedatabase.app/`,
and the admin app reads its settings from
`EventHubAdmin/app/google-services.json`.

### EventHub Admin

- Android Studio with its bundled JDK, AGP 9.5.1 and Gradle 9.6.0.
- minSdk 33, targetSdk 37, Java 11.
- Firebase Authentication 25.0.0, Firebase Realtime Database 23.0.0 and ZXing
  core 3.5.4.

Open `EventHubAdmin` in Android Studio and run the `app` configuration, or
build from the command line:

```
cd EventHubAdmin
./gradlew assembleDebug
```

Sign in with the admin account in `admin-account.txt`, which comes in the
submitted ZIP file rather than the repository.

### EventHub

- Visual Studio 2026 with the .NET MAUI workload, or the .NET 10 SDK with the
  `maui` and `android` workloads.
- FirebaseDatabase.net 5.0.0 [14] and sqlite-net-pcl 1.11.285 [15].

Open `EventHub/EventHub.slnx` in Visual Studio, choose an Android emulator and
run, or from the command line:

```
cd EventHub/EventHub
dotnet build -t:Run -f net10.0-android
```

Both apps were tested on a Pixel 7 Pro emulator running API 33. Run the admin
app first and upload the events, then open EventHub to see them.

## Module brief

The admin app follows the module's Android Studio brief, as Exercise 3 did:
explicit intents with Serializable extras, an ActivityResultLauncher, a
DatePickerDialog in a DialogFragment, and a SQLiteOpenHelper with bound
statements. The upload uses the brief's
`FirebaseDatabase.getInstance().getReference()`. The brief adds each item with
`push().setValue()`, which makes a new key every time, so the app writes the
whole `/events` node with one `setValue` instead, and edits and deletions
replace the old copy.

The user app follows the module's .NET MAUI brief: a `FirebaseClient` that
reads the events and listens for changes with `AsObservable`, and
sqlite-net-pcl for the favorites, as the brief's `DatabaseService` does.

## Project structure

### EventHub Admin

| File                          | Purpose                                        |
| ----------------------------- | ---------------------------------------------- |
| `SignInActivity.java`         | Admin sign-in, shown until the admin signs in  |
| `MainActivity.java`           | Event list, search, filters, upload, sign out  |
| `EventDetailsActivity.java`   | One event in full, its review and its QR code  |
| `AddEditEventActivity.java`   | Event form, errors and the review step         |
| `SessionsActivity.java`       | An event's sessions, with a search             |
| `SessionDetailsActivity.java` | One session in full, with Edit and Delete      |
| `AddEditSessionActivity.java` | Session form with times within the event       |
| `EventDatabase.java`          | SQLite tables, queries and upgrades            |
| `CloudSync.java`              | Upload on a background thread                  |
| `EventQrCode.java`            | The event as QR code text and image            |
| `EventAdapter.java`           | Month headings, event cards and selection      |
| `SessionAdapter.java`         | Session cards                                  |
| `EventCardState.java`         | Card states, colors and status phrases         |
| `EventCardDrawable.java`      | Card shape with the side panel and divider     |
| `Event.java`, `Session.java`  | Event and session models                       |
| `EventType.java`              | Event types and their label colors             |
| `SessionType.java`            | Session types from the coursework brief        |
| `EventStatus.java`            | Event statuses                                 |
| `EventFilter.java`            | The search text and filters of the list        |
| `EventValidator.java`         | Validation, status rules and duplicate check   |
| `EventDates.java`             | Date parsing and formatting                    |
| `EventTimes.java`             | Time slots, lengths and formatting             |
| `DropdownField.java`          | Menu field that opens on the chosen item       |
| `MoreMenu.java`               | Compact menu as wide as its widest item        |
| `IconSnackbar.java`           | Messages with an icon before their text        |
| `DatePickerFragment.java`     | Date picker dialog hosted in a DialogFragment  |

### EventHub

| File                       | Purpose                                         |
| -------------------------- | ----------------------------------------------- |
| `MainPage.xaml`            | Event list, search, filters and offline notice  |
| `EventDetailsPage.xaml`    | One event in full, with its favorite star       |
| `SessionsPage.xaml`        | An event's sessions, with a search              |
| `SessionDetailsPage.xaml`  | One session in full                             |
| `Event.cs`, `Session.cs`   | Models read from Firebase                       |
| `EventCard.cs`             | An event as its card shows it                   |
| `SessionCard.cs`           | A session as its card shows it                  |
| `MonthGroup.cs`            | The cards of one month                          |
| `Search.cs`                | The search rule shared with the admin app       |
| `Favorite.cs`              | A favorite event kept with all its details      |
| `FavoriteDatabase.cs`      | Favorites in SQLite                             |
| `MenuChoice.cs`            | One choice in a filter's menu                   |
| `AppColors.cs`             | Theme colors for code-behind                    |

Icons are from Material Symbols and Icons [16].

## Sources

1. Firebase. Authenticate with Firebase using password-based accounts on
   Android. https://firebase.google.com/docs/auth/android/password-auth
2. Android Developers. Read network state.
   https://developer.android.com/develop/connectivity/network-ops/reading-network-state
3. Android Developers. ExecutorService.
   https://developer.android.com/reference/java/util/concurrent/ExecutorService
4. ZXing. https://github.com/zxing/zxing
5. ITU-T. Recommendation E.164. https://www.itu.int/rec/T-REC-E.164
6. GOV.UK Design System. Error message.
   https://design-system.service.gov.uk/components/error-message/
7. Android Developers. Save data using SQLite.
   https://developer.android.com/training/data-storage/sqlite
8. SQLite. Foreign key support. https://www.sqlite.org/foreignkeys.html
9. Firebase. Read and write data on Android.
   https://firebase.google.com/docs/database/android/read-and-write
10. Firebase. Understand Firebase Realtime Database Security Rules.
    https://firebase.google.com/docs/database/security
11. Microsoft Learn. Email.
    https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/communication/email
12. Microsoft Learn. Phone dialer.
    https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/communication/phone-dialer
13. Android Developers. Package visibility filtering on Android.
    https://developer.android.com/training/package-visibility
14. FirebaseDatabase.net.
    https://github.com/step-up-labs/firebase-database-dotnet
15. sqlite-net. https://github.com/praeclarum/sqlite-net
16. Google. Material Symbols and Icons. https://fonts.google.com/icons
