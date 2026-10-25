namespace EventHub
{
    public partial class SessionDetailsPage : ContentPage
    {
        public SessionDetailsPage(Event e, Session session)
        {
            InitializeComponent();
            nameLabel.Text = session.Name;
            typeText.Text = EventCard.GetSessionTypeLabel(session.Type);

            DateTime start = session.GetStart(e);
            DateTime end = session.GetEnd(e);
            string length = EventCard.GetLength(end - start);
            bool endsNextDay = end.Date > start.Date;
            dateLabel.Text = start.ToString("dddd d MMMM yyyy", EventCard.English);
            timeLabel.Text = $"{session.StartTime}–{session.EndTime}";
            nextDayLabel.IsVisible = endsNextDay;
            lengthLabel.Text = length;
            SemanticProperties.SetDescription(timeRow, endsNextDay
                ? $"From {session.StartTime} to {session.EndTime} the next day, {length}"
                : $"From {session.StartTime} to {session.EndTime}, {length}");

            // Without labels on screen, screen readers hear what each row is
            ShowOptional(speakerRow, speakerLabel, session.Speaker, "Speaker");
            ShowOptional(roomRow, roomLabel, session.Room, "Room");
            descriptionLabel.Text = session.Description;
            descriptionCard.IsVisible = session.Description.Length > 0;
        }

        private async void OnBackTapped(object? sender, TappedEventArgs e)
        {
            await Navigation.PopAsync();
        }

        // Shows an optional detail, read out with what it is, or hides its row when empty
        private static void ShowOptional(View row, Label label, string value, string name)
        {
            label.Text = value;
            SemanticProperties.SetDescription(label, $"{name}: {value}");
            row.IsVisible = value.Length > 0;
        }
    }
}
