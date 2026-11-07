using Microsoft.Maui.Controls.Shapes;

namespace EventHub
{
    public partial class EventDetailsPage : ContentPage
    {
        private readonly EventCard card;
        private readonly FavoriteDatabase favorites;
        private bool isFavorite;

        // The card that was tapped, so the details show the status it showed
        public EventDetailsPage(EventCard card, FavoriteDatabase favorites)
        {
            InitializeComponent();
            this.card = card;
            this.favorites = favorites;
            isFavorite = favorites.IsFavorite(card.Event.Key);
        }

        protected override void OnAppearing()
        {
            base.OnAppearing();
            // The colors come from the theme, so the event is shown again when it changes
            Application.Current!.RequestedThemeChanged += OnThemeChanged;
            ShowEvent();
        }

        protected override void OnDisappearing()
        {
            base.OnDisappearing();
            Application.Current!.RequestedThemeChanged -= OnThemeChanged;
        }

        private void OnThemeChanged(object? sender, AppThemeChangedEventArgs e)
        {
            ShowEvent();
        }

        private async void OnBackTapped(object? sender, TappedEventArgs e)
        {
            await Navigation.PopAsync();
        }

        private void OnFavoriteTapped(object? sender, TappedEventArgs e)
        {
            isFavorite = favorites.ToggleEvent(card.Event);
            ShowFavorite();
            SemanticScreenReader.Announce(isFavorite ? "Added to favorites" : "Removed from favorites");
        }

        // A filled star once the event is a favorite, and an outlined one otherwise
        private void ShowFavorite()
        {
            favoriteIcon.Data = (Geometry)Application.Current!.Resources[isFavorite ? "IconStar" : "IconStarOutline"];
            favoriteIcon.Fill = new SolidColorBrush(AppColors.Get(isFavorite ? "Primary" : "OnSurface"));
            SemanticProperties.SetDescription(favoriteButton,
                isFavorite ? "Remove from favorites" : "Add to favorites");
        }

        private async void OnSessionsTapped(object? sender, TappedEventArgs e)
        {
            if (card.Event.Sessions.Count > 0)
            {
                await Navigation.PushAsync(new SessionsPage(card.Event));
            }
        }

        // Opens the email app with the organizer's address filled in
        private async void OnEmailTapped(object? sender, TappedEventArgs e)
        {
            if (Email.Default.IsComposeSupported)
            {
                await Email.Default.ComposeAsync(new EmailMessage { To = [card.Event.ContactEmail] });
            }
            else
            {
                await DisplayAlertAsync("No email app", "Install an email app to write to the organizer.", "OK");
            }
        }

        // Opens the dialer with the number filled in, so the user decides to call
        private async void OnPhoneTapped(object? sender, TappedEventArgs e)
        {
            if (PhoneDialer.Default.IsSupported)
            {
                PhoneDialer.Default.Open(card.Event.ContactPhone);
            }
            else
            {
                await DisplayAlertAsync("No phone app", "This device can't make phone calls.", "OK");
            }
        }

        private void ShowEvent()
        {
            Event shown = card.Event;
            ShowFavorite();
            nameLabel.Text = shown.Name;
            nameLabel.TextDecorations = card.NameDecorations;
            nameLabel.TextColor = card.TextColor;
            typeLabel.BackgroundColor = card.TypeColor;
            typeText.Text = card.TypeLabel;
            liveDot.IsVisible = card.IsLive;
            statusLabel.Text = card.StatusText;
            statusLabel.TextColor = card.StatusColor;

            dateLabel.Text = card.LongDate;
            timeLabel.Text = card.TimeRange;
            nextDayLabel.IsVisible = card.EndsNextDay;
            lengthLabel.Text = card.Length;
            SemanticProperties.SetDescription(timeRow, card.EndsNextDay
                ? $"From {shown.StartTime} to {shown.EndTime} the next day, {card.Length}"
                : $"From {shown.StartTime} to {shown.EndTime}, {card.Length}");
            locationLabel.Text = shown.Location;
            organizerLabel.Text = shown.Organizer;
            ShowOptional(emailRow, emailLabel, shown.ContactEmail);
            ShowOptional(phoneRow, phoneLabel, shown.ContactPhone);
            ShowSessions();
            ShowOptional(descriptionCard, descriptionLabel, shown.Description);
        }

        // Shows how many sessions there are and, unless the event is cancelled, the next to start
        private void ShowSessions()
        {
            Event shown = card.Event;
            int count = shown.Sessions.Count;
            sessionCountLabel.Text = card.SessionCount;
            sessionsChevron.IsVisible = count > 0;
            DateTime now = DateTime.Now;
            Session? next = card.IsCancelled ? null : shown.Sessions.Values
                .Where(session => session.HasValidTimes && session.GetStart(shown) > now)
                .OrderBy(session => session.GetStart(shown))
                .ThenBy(session => session.Id)
                .FirstOrDefault();
            nextSessionLabel.Text = next == null ? string.Empty : $"Next: {next.StartTime} {next.Name}";
            nextSessionLabel.IsVisible = next != null;
        }

        // Shows an optional detail, or hides its row or card when it was left empty
        private static void ShowOptional(View container, Label label, string value)
        {
            label.Text = value;
            container.IsVisible = value.Length > 0;
        }
    }
}
