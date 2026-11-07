namespace EventHub
{
    // One choice in a filter's menu, ticked when it is the chosen one.
    public class MenuChoice
    {
        public MenuChoice(string label, bool isChosen, Action choose)
        {
            Label = label;
            IsChosen = isChosen;
            Choose = choose;
        }

        public string Label { get; }

        public bool IsChosen { get; }

        public Action Choose { get; }

        // Hidden rather than removed, so every choice keeps room for the tick
        public double TickOpacity => IsChosen ? 1 : 0;

        public string Description => IsChosen ? $"{Label}, selected" : Label;
    }
}
