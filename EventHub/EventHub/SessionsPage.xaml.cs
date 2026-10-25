namespace EventHub
{
    public partial class SessionsPage : ContentPage
    {
        private readonly Event shown;
        private string searchText = string.Empty;
        // Set while a session opens, so a quick second tap does not open it twice
        private bool opening;

        public SessionsPage(Event e)
        {
            InitializeComponent();
            shown = e;
            eventNameLabel.Text = e.Name;
        }

        protected override void OnAppearing()
        {
            base.OnAppearing();
            opening = false;
            // The colors come from the theme, so the sessions are shown again when it changes
            Application.Current!.RequestedThemeChanged += OnThemeChanged;
            ShowSessions();
        }

        protected override void OnDisappearing()
        {
            base.OnDisappearing();
            Application.Current!.RequestedThemeChanged -= OnThemeChanged;
        }

        private void OnThemeChanged(object? sender, AppThemeChangedEventArgs e)
        {
            ShowSessions();
        }

        // Back closes the search before it leaves the page
        protected override bool OnBackButtonPressed()
        {
            if (searchBar.IsVisible)
            {
                CloseSearch();
                return true;
            }
            return base.OnBackButtonPressed();
        }

        private async void OnBackTapped(object? sender, TappedEventArgs e)
        {
            await Navigation.PopAsync();
        }

        private void OnSearchTapped(object? sender, TappedEventArgs e)
        {
            titleBar.IsVisible = false;
            searchBar.IsVisible = true;
            searchEntry.Focus();
        }

        private void OnCloseSearchTapped(object? sender, TappedEventArgs e)
        {
            CloseSearch();
        }

        private void CloseSearch()
        {
            searchEntry.Text = string.Empty;
            searchEntry.Unfocus();
            searchBar.IsVisible = false;
            titleBar.IsVisible = true;
        }

        private void OnClearSearchTapped(object? sender, TappedEventArgs e)
        {
            searchEntry.Text = string.Empty;
            searchEntry.Focus();
        }

        private void OnSearchTextChanged(object? sender, TextChangedEventArgs e)
        {
            string enteredText = e.NewTextValue ?? string.Empty;
            searchText = enteredText.Trim();
            clearSearchButton.IsVisible = enteredText.Length > 0;
            ShowSessions();
        }

        private void OnSearchCompleted(object? sender, EventArgs e)
        {
            HideKeyboard();
        }

        private void OnListScrolled(object? sender, ItemsViewScrolledEventArgs e)
        {
            if (e.VerticalDelta > 0)
            {
                HideKeyboard();
            }
        }

        private async void HideKeyboard()
        {
            if (searchEntry.IsSoftInputShowing())
            {
                await searchEntry.HideSoftInputAsync(CancellationToken.None);
            }
        }

        private async void OnSessionTapped(object? sender, TappedEventArgs e)
        {
            // The BindingContext of the tapped card is its session, as in the module brief
            if (!opening && (sender as BindableObject)?.BindingContext is SessionCard card)
            {
                opening = true;
                await Navigation.PushAsync(new SessionDetailsPage(shown, card.Session));
            }
        }

        // Shows the matching sessions in order, colored by where each is in time
        private void ShowSessions()
        {
            DateTime now = DateTime.Now;
            List<Session> sessions = shown.Sessions.Values
                .Where(session => session.HasValidTimes && Matches(session))
                .OrderBy(session => session.GetStart(shown))
                .ThenBy(session => session.Id)
                .ToList();
            DateTime? nextStart = EventCard.GetNextStart(
                sessions.Select(session => (session.GetStart(shown), session.GetEnd(shown))), now);
            sessionList.ItemsSource = sessions
                .Select(session => new SessionCard(shown, session, EventCard.GetState(
                    shown.IsCancelled, session.GetStart(shown), session.GetEnd(shown), now, nextStart)))
                .ToList();
            emptyView.IsVisible = sessions.Count == 0;
        }

        // A word in the name or description starts with the search
        private bool Matches(Session session)
        {
            return searchText.Length == 0
                || Search.StartsWord(session.Name, searchText)
                || Search.StartsWord(session.Description, searchText);
        }
    }
}
