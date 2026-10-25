namespace EventHub
{
    // The cards of one month, shown under a heading such as "OCTOBER 2026".
    public class MonthGroup : List<EventCard>
    {
        public MonthGroup(string name, IEnumerable<EventCard> cards) : base(cards)
        {
            Name = name;
        }

        public string Name { get; }
    }
}
