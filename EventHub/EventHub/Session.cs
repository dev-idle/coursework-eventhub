using System.Globalization;
using Newtonsoft.Json;

namespace EventHub
{
    // A session of an event, stored under the event in Firebase.
    public class Session
    {
        [JsonIgnore]
        public string Key { get; set; } = string.Empty; // such as "s31"

        // The admin app's ID, after the "s" of the key, to order sessions that start together.
        [JsonIgnore]
        public long Id => Key.Length > 1 && long.TryParse(Key.AsSpan(1), out long id) ? id : 0;

        [JsonProperty("name")]
        public string Name { get; set; } = string.Empty;

        [JsonProperty("startTime")]
        public string StartTime { get; set; } = string.Empty;

        [JsonProperty("endTime")]
        public string EndTime { get; set; } = string.Empty;

        // WORKSHOP, PRESENTATION, PANEL_DISCUSSION, NETWORKING, KEYNOTE or OTHER
        [JsonProperty("type")]
        public string Type { get; set; } = string.Empty;

        [JsonProperty("speaker")]
        public string Speaker { get; set; } = string.Empty;

        [JsonProperty("room")]
        public string Room { get; set; } = string.Empty;

        [JsonProperty("description")]
        public string Description { get; set; } = string.Empty;

        // False for a session whose times cannot be read, which the lists leave out.
        public bool HasValidTimes => IsTime(StartTime) && IsTime(EndTime);

        // Within its event, only a time after midnight comes before the event's start time.
        public DateTime GetStart(Event e)
        {
            DateTime start = Event.ToDateTime(e.Date, StartTime);
            return start < e.Start ? start.AddDays(1) : start;
        }

        // On the day after the session starts if it runs past midnight.
        public DateTime GetEnd(Event e)
        {
            DateTime start = GetStart(e);
            DateTime end = start.Date + Event.ToDateTime(e.Date, EndTime).TimeOfDay;
            return end > start ? end : end.AddDays(1);
        }

        public bool StartsNextDay(Event e) => GetStart(e).Date > e.Start.Date;

        public bool EndsNextDay(Event e) => GetEnd(e).Date > e.Start.Date;

        private static bool IsTime(string time) =>
            TimeOnly.TryParseExact(time, "HH:mm", CultureInfo.InvariantCulture, DateTimeStyles.None, out _);
    }
}
