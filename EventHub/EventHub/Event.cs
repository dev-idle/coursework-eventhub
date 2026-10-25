using System.Globalization;
using Newtonsoft.Json;

namespace EventHub
{
    // JsonProperty keeps the field names the admin app writes to "events" in Firebase,
    // as Book does for the books of lab 07 in lab 10.
    public class Event
    {
        [JsonIgnore]
        public string Key { get; set; } = string.Empty; // such as "e12", from the admin app's ID

        // The admin app's ID, after the "e" of the key, to order events that start together.
        [JsonIgnore]
        public long Id => Key.Length > 1 && long.TryParse(Key.AsSpan(1), out long id) ? id : 0;

        [JsonProperty("name")]
        public string Name { get; set; } = string.Empty;

        // Dates are written as yyyy-MM-dd and times as HH:mm.
        [JsonProperty("date")]
        public string Date { get; set; } = string.Empty;

        [JsonProperty("startTime")]
        public string StartTime { get; set; } = string.Empty;

        [JsonProperty("endTime")]
        public string EndTime { get; set; } = string.Empty;

        [JsonProperty("location")]
        public string Location { get; set; } = string.Empty;

        [JsonProperty("organizer")]
        public string Organizer { get; set; } = string.Empty;

        // CONFERENCE, WORKSHOP, SEMINAR, SOCIAL or OTHER
        [JsonProperty("type")]
        public string Type { get; set; } = string.Empty;

        // UPCOMING, COMPLETED or CANCELLED
        [JsonProperty("status")]
        public string Status { get; set; } = string.Empty;

        [JsonProperty("description")]
        public string Description { get; set; } = string.Empty;

        [JsonProperty("contactEmail")]
        public string ContactEmail { get; set; } = string.Empty;

        [JsonProperty("contactPhone")]
        public string ContactPhone { get; set; } = string.Empty;

        [JsonProperty("sessions")]
        public Dictionary<string, Session> Sessions { get; set; } = new();

        public DateTime Start => ToDateTime(Date, StartTime);

        // An event ends on the next day when its end time is not after its start time.
        public DateTime End
        {
            get
            {
                DateTime end = ToDateTime(Date, EndTime);
                return end > Start ? end : end.AddDays(1);
            }
        }

        public bool IsCancelled => Status == "CANCELLED";

        // False for an event whose date or times cannot be read, which the list leaves out.
        public bool HasValidTimes => IsDateTime(Date, StartTime) && IsDateTime(Date, EndTime);

        public static DateTime ToDateTime(string date, string time) =>
            DateTime.ParseExact($"{date} {time}", "yyyy-MM-dd HH:mm", CultureInfo.InvariantCulture);

        private static bool IsDateTime(string date, string time) =>
            DateTime.TryParseExact($"{date} {time}", "yyyy-MM-dd HH:mm",
                CultureInfo.InvariantCulture, DateTimeStyles.None, out _);
    }
}
