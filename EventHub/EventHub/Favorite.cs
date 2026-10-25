using SQLite;

namespace EventHub
{
    // A favorite event, kept with all its details so that it can be shown offline.
    public class Favorite
    {
        [PrimaryKey] // the event's key in the cloud, such as "e12"
        public string Key { get; set; } = string.Empty;

        // The event and its sessions as JSON, in the form the cloud sends them
        public string Data { get; set; } = string.Empty;
    }
}
