using System.Globalization;

namespace EventHub
{
    // Where an event is in time, or that it was cancelled, as on the admin app's cards.
    public enum CardState
    {
        Live,
        Next,
        Upcoming,
        Past,
        Cancelled
    }

    // An event as its card shows it, worked out for the time the list was shown.
    public class EventCard
    {
        public static readonly CultureInfo English = CultureInfo.GetCultureInfo("en-GB");

        public EventCard(Event e, CardState state, DateTime now, bool isFavorite)
        {
            Event = e;
            State = state;
            StatusText = GetStatusText(e, state, now.Date);
            IsFavorite = isFavorite;
        }

        public Event Event { get; }

        public CardState State { get; }

        public string StatusText { get; }

        public bool IsFavorite { get; }

        public string Name => Event.Name;

        public string Month => Event.Start.ToString("MMM", English).ToUpperInvariant();

        public string Day => Event.Start.Day.ToString(English);

        public string DayOfWeek => Event.Start.ToString("ddd", English);

        public string TypeLabel => GetTypeLabel(Event.Type);

        public string StartTime => Event.StartTime;

        public string EndTime => Event.EndTime;

        public bool EndsNextDay => Event.End.Date > Event.Start.Date;

        public string Length => GetLength(Event.End - Event.Start);

        public string Location => Event.Location;

        public string Organizer => Event.Organizer;

        public string SessionCount => Event.Sessions.Count switch
        {
            0 => "No sessions",
            1 => "1 session",
            int count => $"{count} sessions"
        };

        public bool IsLive => State == CardState.Live;

        public bool IsCancelled => State == CardState.Cancelled;

        // Such as "Friday 6 November 2026"
        public string LongDate => Event.Start.ToString("dddd d MMMM yyyy", English);

        public string TimeRange => $"{Event.StartTime}–{Event.EndTime}";

        public TextDecorations NameDecorations =>
            State == CardState.Cancelled ? TextDecorations.Strikethrough : TextDecorations.None;

        // Colors for the theme in use, as the list is shown again when the theme changes.
        public Color PanelColor => GetPanelColor(State);

        public Color PanelTextColor => GetPanelTextColor(State);

        // Cancelled is the only status in red, as the one that does not follow from the time.
        public Color StatusColor => AppColors.Get(State switch
        {
            CardState.Live or CardState.Next => "Primary",
            CardState.Cancelled => "Error",
            _ => "OnSurfaceVariant"
        });

        public Color TextColor =>
            AppColors.Get(State == CardState.Cancelled ? "OnSurfaceVariant" : "OnSurface");

        public Color TypeColor => AppColors.Get(State == CardState.Cancelled
            ? "SurfaceContainerHighest" : "Type" + TypeLabel);

        // The date panel of an event card, or the time panel of a session card
        public static Color GetPanelColor(CardState state) => AppColors.Get(state switch
        {
            CardState.Live or CardState.Next => "Primary",
            CardState.Upcoming => "PrimaryContainer",
            _ => "SurfaceContainerHighest"
        });

        public static Color GetPanelTextColor(CardState state) => AppColors.Get(state switch
        {
            CardState.Live or CardState.Next => "OnPrimary",
            CardState.Upcoming => "OnPrimaryContainer",
            _ => "OnSurfaceVariant"
        });

        // When the next events start, or null while one is happening or none is ahead.
        public static DateTime? GetNextStart(IEnumerable<Event> events, DateTime now)
        {
            return GetNextStart(events.Where(e => !e.IsCancelled).Select(e => (e.Start, e.End)), now);
        }

        // When the next of these start, or null while one is happening or none is ahead.
        public static DateTime? GetNextStart(IEnumerable<(DateTime Start, DateTime End)> times,
            DateTime now)
        {
            DateTime? nextStart = null;
            foreach (var (start, end) in times)
            {
                if (start <= now && now < end)
                {
                    return null;
                }
                if (start > now && (nextStart == null || start < nextStart))
                {
                    nextStart = start;
                }
            }
            return nextStart;
        }

        public static CardState GetState(Event e, DateTime now, DateTime? nextStart)
        {
            return GetState(e.IsCancelled, e.Start, e.End, now, nextStart);
        }

        // A session is cancelled along with its event.
        public static CardState GetState(bool cancelled, DateTime start, DateTime end,
            DateTime now, DateTime? nextStart)
        {
            if (cancelled)
            {
                return CardState.Cancelled;
            }
            if (end <= now)
            {
                return CardState.Past;
            }
            if (start <= now)
            {
                return CardState.Live;
            }
            return start == nextStart ? CardState.Next : CardState.Upcoming;
        }

        public static string GetTypeLabel(string type) => type switch
        {
            "CONFERENCE" => "Conference",
            "WORKSHOP" => "Workshop",
            "SEMINAR" => "Seminar",
            "SOCIAL" => "Social",
            _ => "Other"
        };

        public static string GetSessionTypeLabel(string type) => type switch
        {
            "WORKSHOP" => "Workshop",
            "PRESENTATION" => "Presentation",
            "PANEL_DISCUSSION" => "Panel discussion",
            "NETWORKING" => "Networking",
            "KEYNOTE" => "Keynote",
            _ => "Other"
        };

        // Such as "8 hr 30 min".
        public static string GetLength(TimeSpan length)
        {
            int hours = (int)length.TotalHours;
            if (length.Minutes == 0)
            {
                return $"{hours} hr";
            }
            return hours == 0 ? $"{length.Minutes} min" : $"{hours} hr {length.Minutes} min";
        }

        // The status with when the event is, such as "Upcoming in 6 days", as in the admin app.
        private static string GetStatusText(Event e, CardState state, DateTime today)
        {
            if (state == CardState.Live)
            {
                return "Happening now";
            }
            if (state == CardState.Cancelled)
            {
                return "Cancelled";
            }
            // A past event counts from the day it ended, any other from the day it starts.
            DateTime date = state == CardState.Past ? e.End.Date : e.Start.Date;
            int daysAway = (date - today).Days;
            switch (daysAway)
            {
                case 0:
                    return state == CardState.Past ? "Completed today" : "Upcoming today";
                case 1:
                    return "Upcoming tomorrow";
                case -1:
                    return "Completed yesterday";
            }
            return daysAway > 0
                ? $"Upcoming in {GetDuration(today, date)}"
                : $"Completed {GetDuration(date, today)} ago";
        }

        // Whole years, else whole months, else days, as Java's Period counts them.
        private static string GetDuration(DateTime from, DateTime to)
        {
            int months = (to.Year - from.Year) * 12 + to.Month - from.Month;
            if (to.Day < from.Day)
            {
                months--;
            }
            if (months >= 12)
            {
                return Count(months / 12, "year");
            }
            return months > 0 ? Count(months, "month") : Count((to - from).Days, "day");
        }

        private static string Count(int count, string unit) =>
            count == 1 ? $"1 {unit}" : $"{count} {unit}s";
    }
}
