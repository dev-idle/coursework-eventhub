using Newtonsoft.Json;

namespace EventHub
{
    // A session of an event, stored under the event in Firebase.
    public class Session
    {
        [JsonIgnore]
        public string Key { get; set; } = string.Empty; // such as "s31"

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
    }
}
