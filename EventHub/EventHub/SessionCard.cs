namespace EventHub
{
    // A session as its card shows it, colored like the event cards by where it is in time.
    public class SessionCard
    {
        public SessionCard(Event e, Session session, CardState state)
        {
            Event = e;
            Session = session;
            State = state;
        }

        public Event Event { get; }

        public Session Session { get; }

        public CardState State { get; }

        public string Name => Session.Name;

        public string TypeLabel => EventCard.GetSessionTypeLabel(Session.Type);

        public string StartTime => Session.StartTime;

        public string EndTime => Session.EndTime;

        // Empty details are named, so every card has the same rows
        public string Speaker => Session.Speaker.Length > 0 ? Session.Speaker : "No speaker";

        public string Room => Session.Room.Length > 0 ? Session.Room : "No room";

        // In an event past midnight, every card keeps room for a +1, so the times line up
        public bool HasNextDay => Event.End.Date > Event.Start.Date;

        public double StartNextDayOpacity => Session.StartsNextDay(Event) ? 1 : 0;

        public double EndNextDayOpacity => Session.EndsNextDay(Event) ? 1 : 0;

        public Color PanelColor => EventCard.GetPanelColor(State);

        public Color PanelTextColor => EventCard.GetPanelTextColor(State);
    }
}
