using Newtonsoft.Json;
using SQLite;

namespace EventHub
{
    // Keeps the favorite events in SQLite on the phone, as DatabaseService does in the module brief.
    public class FavoriteDatabase
    {
        private readonly SQLiteConnection database;

        public FavoriteDatabase(string dbPath)
        {
            database = new SQLiteConnection(dbPath);
            database.CreateTable<Favorite>();
        }

        public bool IsFavorite(string key)
        {
            return database.Find<Favorite>(key) != null;
        }

        public HashSet<string> GetKeys()
        {
            return database.Table<Favorite>().Select(favorite => favorite.Key).ToHashSet();
        }

        // The saved copies of the favorite events, to show while the phone is offline
        public List<Event> GetEvents()
        {
            List<Event> events = new();
            foreach (Favorite favorite in database.Table<Favorite>().ToList())
            {
                Event? e = JsonConvert.DeserializeObject<Event>(favorite.Data);
                if (e != null)
                {
                    e.SetKeys(favorite.Key);
                    events.Add(e);
                }
            }
            return events;
        }

        // Adds the event to the favorites, or takes it off, and returns whether it is one now
        public bool ToggleEvent(Event e)
        {
            if (IsFavorite(e.Key))
            {
                DeleteEvent(e.Key);
                return false;
            }
            AddEvent(e);
            return true;
        }

        public void AddEvent(Event e)
        {
            database.InsertOrReplace(new Favorite { Key = e.Key, Data = JsonConvert.SerializeObject(e) });
        }

        public void DeleteEvent(string key)
        {
            database.Delete<Favorite>(key);
        }

        // Keeps the saved copies as the cloud has them, and drops events the admin has deleted
        public void UpdateEvents(IReadOnlyDictionary<string, Event> events)
        {
            foreach (string key in GetKeys())
            {
                if (events.TryGetValue(key, out Event? e))
                {
                    AddEvent(e);
                }
                else
                {
                    DeleteEvent(key);
                }
            }
        }
    }
}
