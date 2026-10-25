using System.Collections.ObjectModel;
using Firebase.Database;
using Microsoft.Maui.Controls.Shapes;
using Path = Microsoft.Maui.Controls.Shapes.Path;

namespace EventHub
{
    public partial class MainPage : ContentPage
    {
        // In the order of the coursework brief, with Other last
        private static readonly string[] Types = { "CONFERENCE", "WORKSHOP", "SEMINAR", "SOCIAL", "OTHER" };
        // In the order an event goes through them
        private static readonly string[] Statuses = { "Upcoming", "Completed", "Cancelled" };

        // The url is from the Firebase Realtime Database that the admin app uploads to
        private readonly FirebaseClient firebaseClient = new FirebaseClient(
            "https://comp1786-eventhub-default-rtdb.asia-southeast1.firebasedatabase.app/");

        // Every event in the cloud by key, kept up to date as the admin app changes them
        private readonly Dictionary<string, Event> events = new();
        private readonly ObservableCollection<MonthGroup> months = new();
        private IDisposable? subscription;
        private bool loaded;
        // Set while the events are read, and when they change again during the read
        private bool reading;
        private bool readAgain;
        // Set while an event opens, so a quick second tap does not open it twice
        private bool opening;

        // The search and filters, kept while the app runs but not saved, as in the admin app
        private string searchText = string.Empty;
        private DateTime? dateFilter;
        private string? typeFilter;
        private string? statusFilter;

        public MainPage()
        {
            InitializeComponent();
            eventList.ItemsSource = months;
            // The cards take their colors from the theme, so they are shown again when it changes
            Application.Current!.RequestedThemeChanged += (sender, e) => ShowEvents();
        }

        protected override void OnAppearing()
        {
            base.OnAppearing();
            opening = false;
            Connectivity.Current.ConnectivityChanged += OnConnectivityChanged;
            Window.Resumed += OnWindowResumed;
            if (loaded)
            {
                // The states and countdowns change as time passes
                ShowEvents();
            }
            GetEvents();
        }

        protected override void OnDisappearing()
        {
            base.OnDisappearing();
            Connectivity.Current.ConnectivityChanged -= OnConnectivityChanged;
            Window.Resumed -= OnWindowResumed;

            // Stop listening to Firebase when the page is closed
            subscription?.Dispose();
            subscription = null;
        }

        private void OnConnectivityChanged(object? sender, ConnectivityChangedEventArgs e)
        {
            if (e.NetworkAccess == NetworkAccess.Internet && subscription == null)
            {
                GetEvents();
            }
        }

        // Android pauses an app in the background, which can end the stream without a word, so the
        // events are read again and listened to afresh when the app comes back
        private void OnWindowResumed(object? sender, EventArgs e)
        {
            subscription?.Dispose();
            subscription = null;
            GetEvents();
        }

        private void OnRetryClicked(object? sender, EventArgs e)
        {
            GetEvents();
        }

        private async void OnEventTapped(object? sender, TappedEventArgs e)
        {
            // The BindingContext of the tapped card is its event, as in the module brief
            if (!opening && (sender as BindableObject)?.BindingContext is EventCard card)
            {
                opening = true;
                await Navigation.PushAsync(new EventDetailsPage(card));
            }
        }

        // Back closes an open menu, then the search, before it leaves the app
        protected override bool OnBackButtonPressed()
        {
            if (menuLayer.IsVisible)
            {
                menuLayer.IsVisible = false;
                return true;
            }
            if (searchBar.IsVisible)
            {
                CloseSearch();
                return true;
            }
            return base.OnBackButtonPressed();
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

        // Clears the search and turns the search field back into the title
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

        // Searches as the user types
        private void OnSearchTextChanged(object? sender, TextChangedEventArgs e)
        {
            string enteredText = e.NewTextValue ?? string.Empty;
            searchText = enteredText.Trim();
            clearSearchButton.IsVisible = enteredText.Length > 0;
            ShowEvents();
        }

        private void OnSearchCompleted(object? sender, EventArgs e)
        {
            HideKeyboard();
        }

        // Scrolling down the list puts the keyboard away, keeping the search and its results
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

        private void OnDateChipTapped(object? sender, TappedEventArgs e)
        {
            ShowDatePicker();
        }

        // The arrow opens the choices too, and once a date is chosen, the cross clears it
        private void OnDateChipButtonTapped(object? sender, TappedEventArgs e)
        {
            if (dateFilter == null)
            {
                ShowDatePicker();
            }
            else
            {
                dateFilter = null;
                ShowEvents();
            }
        }

        private void ShowDatePicker()
        {
            // Opens on the chosen date, or on none, so that choosing today also counts as a change
            datePicker.Date = dateFilter;
            datePicker.IsOpen = true;
        }

        private void OnDateSelected(object? sender, DateChangedEventArgs e)
        {
            dateFilter = e.NewDate;
            ShowEvents();
        }

        private void OnTypeChipTapped(object? sender, TappedEventArgs e)
        {
            ShowTypeMenu();
        }

        private void OnTypeChipButtonTapped(object? sender, TappedEventArgs e)
        {
            if (typeFilter == null)
            {
                ShowTypeMenu();
            }
            else
            {
                typeFilter = null;
                ShowEvents();
            }
        }

        private void ShowTypeMenu()
        {
            ShowMenu(typeChip, Types.Select(type => new MenuChoice(
                EventCard.GetTypeLabel(type), type == typeFilter, () => typeFilter = type)));
        }

        private void OnStatusChipTapped(object? sender, TappedEventArgs e)
        {
            ShowStatusMenu();
        }

        private void OnStatusChipButtonTapped(object? sender, TappedEventArgs e)
        {
            if (statusFilter == null)
            {
                ShowStatusMenu();
            }
            else
            {
                statusFilter = null;
                ShowEvents();
            }
        }

        private void ShowStatusMenu()
        {
            ShowMenu(statusChip, Statuses.Select(status => new MenuChoice(
                status, status == statusFilter, () => statusFilter = status)));
        }

        // Opens the choices under their chip, as wide as the longest and kept on the screen
        private void ShowMenu(View chip, IEnumerable<MenuChoice> choices)
        {
            BindableLayout.SetItemsSource(menuItems, choices.ToList());
            Point position = GetPosition(chip);
            // Measured without the margin left from the last menu, which the size would include
            filterMenu.Margin = new Thickness(0);
            double width = filterMenu.Measure(Width, Height).Width;
            // The menu layer starts where the first row does, below the status bar
            double top = position.Y - (searchBar.IsVisible ? searchBar.Y : titleBar.Y);
            filterMenu.Margin = new Thickness(
                Math.Min(position.X, Width - 16 - width), top + chip.Height + 4, 0, 0);
            menuLayer.IsVisible = true;
        }

        private void OnMenuChoiceTapped(object? sender, TappedEventArgs e)
        {
            // The BindingContext of the tapped row is its choice, as in the module brief
            if ((sender as BindableObject)?.BindingContext is MenuChoice choice)
            {
                menuLayer.IsVisible = false;
                choice.Choose();
                ShowEvents();
            }
        }

        private void OnMenuLayerTapped(object? sender, TappedEventArgs e)
        {
            menuLayer.IsVisible = false;
        }

        // Where a view is within the page, allowing for the scroll of the filter chips
        private Point GetPosition(VisualElement view)
        {
            double x = 0;
            double y = 0;
            for (Element? element = view; element != null && element != Content; element = element.Parent)
            {
                if (element is VisualElement visual)
                {
                    x += visual.X;
                    y += visual.Y;
                }
                if (element is ScrollView scroll)
                {
                    x -= scroll.ScrollX;
                    y -= scroll.ScrollY;
                }
            }
            return new Point(x, y);
        }

        // Reads every event once, then listens for the changes the admin app uploads
        private async void GetEvents()
        {
            if (subscription != null)
            {
                return;
            }
            if (Connectivity.Current.NetworkAccess != NetworkAccess.Internet)
            {
                // Events read before the connection was lost stay on screen
                if (!loaded)
                {
                    ShowMessage("No internet connection", "Connect to see the latest events.", true);
                }
                return;
            }
            if (!loaded)
            {
                messageView.IsVisible = false;
                loadingIndicator.IsRunning = true;
                loadingIndicator.IsVisible = true;
            }
            try
            {
                await ReadEventsAsync();
            }
            catch (Exception)
            {
                if (!loaded)
                {
                    ShowMessage("Couldn't load the events", "Check your connection and try again.", true);
                }
                return;
            }

            // The network can come back more than once, so another call may be listening already
            if (subscription != null)
            {
                return;
            }
            // The admin app writes every event at once, which the stream reports as inserts and
            // updates only, so each change is read again in full to drop deleted events too.
            subscription = firebaseClient.Child("events").AsObservable<Event>().Subscribe(
                // Firebase sends events on a background thread, the list must be updated on the main thread
                item => MainThread.BeginInvokeOnMainThread(ReadChanges),
                // Listened to again once the device is back online
                ex => MainThread.BeginInvokeOnMainThread(() => subscription = null));
        }

        private async void ReadChanges()
        {
            try
            {
                await ReadEventsAsync();
            }
            catch (Exception)
            {
                // The events shown stay as they are until the next change
            }
        }

        // Reads every event, and once more if the cloud changes during the read
        private async Task ReadEventsAsync()
        {
            if (reading)
            {
                readAgain = true;
                return;
            }
            reading = true;
            try
            {
                do
                {
                    readAgain = false;
                    // Gives up on a read that hangs, as one can while the network comes back
                    var items = await firebaseClient.Child("events").OnceAsync<Event>(TimeSpan.FromSeconds(15));
                    events.Clear();
                    foreach (var item in items)
                    {
                        item.Object.Key = item.Key;
                        foreach (var session in item.Object.Sessions)
                        {
                            session.Value.Key = session.Key;
                        }
                        events[item.Key] = item.Object;
                    }
                    loaded = true;
                    ShowEvents();
                } while (readAgain);
            }
            finally
            {
                reading = false;
            }
        }

        // Shows the events the search and filters keep under their months, or why there are none
        private void ShowEvents()
        {
            if (!loaded)
            {
                return;
            }
            DateTime now = DateTime.Now;
            List<Event> all = events.Values.Where(e => e.HasValidTimes).ToList();
            if (all.Count == 0)
            {
                // The search and filters hide while there is no event, so they go back to every event
                dateFilter = null;
                typeFilter = null;
                statusFilter = null;
                if (searchBar.IsVisible)
                {
                    CloseSearch();
                }
            }
            List<Event> shown = all
                .Where(e => Matches(e, now))
                .OrderBy(e => e.Start)
                .ThenBy(e => e.Id)
                .ToList();
            DateTime? nextStart = EventCard.GetNextStart(shown, now);

            months.Clear();
            foreach (var month in shown.GroupBy(e => new DateTime(e.Start.Year, e.Start.Month, 1)))
            {
                months.Add(new MonthGroup(
                    month.Key.ToString("MMMM yyyy", EventCard.English).ToUpperInvariant(),
                    month.Select(e => new EventCard(e, EventCard.GetState(e, now, nextStart), now))));
            }

            loadingIndicator.IsRunning = false;
            loadingIndicator.IsVisible = false;
            // Kept while the filters hide every event, so the chips stay where they were
            countLabel.Text = shown.Count == 1 ? "1 event" : $"{shown.Count} events";
            countLabel.IsVisible = all.Count > 0;
            filterBar.IsVisible = all.Count > 0;
            searchButton.IsVisible = all.Count > 0;
            ShowFilters();
            if (all.Count == 0)
            {
                ShowMessage("No events yet", "Events appear here once they are published.", false);
            }
            else if (shown.Count == 0)
            {
                ShowMessage("No matching events", searchText.Length > 0
                    ? "Check the spelling, or try fewer letters." : "Clear a filter to see more events.", false);
            }
            else
            {
                messageView.IsVisible = false;
            }
        }

        // Keeps an event that the search and every chosen filter allow
        private bool Matches(Event e, DateTime now)
        {
            return (searchText.Length == 0
                    || Search.StartsWord(e.Name, searchText)
                    || Search.StartsWord(e.Description, searchText))
                && (dateFilter == null || e.Start.Date == dateFilter.Value.Date)
                && (typeFilter == null || e.Type == typeFilter)
                && (statusFilter == null || GetStatus(e, now) == statusFilter);
        }

        // The status the admin app gives an event, completed once it has ended
        private static string GetStatus(Event e, DateTime now)
        {
            return e.IsCancelled ? "Cancelled" : e.End <= now ? "Completed" : "Upcoming";
        }

        private void ShowFilters()
        {
            ShowChip(dateChip, dateChipText, dateChipIcon, "Date",
                dateFilter?.ToString("dd/MM/yyyy", EventCard.English));
            ShowChip(typeChip, typeChipText, typeChipIcon, "Type",
                typeFilter == null ? null : EventCard.GetTypeLabel(typeFilter));
            ShowChip(statusChip, statusChipText, statusChipIcon, "Status", statusFilter);
        }

        // Shows a filter's name, or its chosen value with a cross that clears it
        private static void ShowChip(Border chip, Label text, Path icon, string name, string? value)
        {
            bool chosen = value != null;
            Color textColor = AppColors.Get(chosen ? "OnPrimaryContainer" : "OnSurfaceVariant");
            chip.BackgroundColor = chosen ? AppColors.Get("PrimaryContainer") : Colors.Transparent;
            chip.Stroke = new SolidColorBrush(AppColors.Get(chosen ? "PrimaryContainer" : "Outline"));
            text.Text = value ?? name;
            text.TextColor = textColor;
            icon.Data = (Geometry)Application.Current!.Resources[chosen ? "IconClose" : "IconDropDown"];
            icon.Fill = new SolidColorBrush(textColor);
            // Read out for the cross, such as "Clear date"
            if (icon.Parent is BindableObject button)
            {
                SemanticProperties.SetDescription(button, chosen ? "Clear " + name.ToLowerInvariant() : null);
            }
        }

        private void ShowMessage(string title, string text, bool canRetry)
        {
            loadingIndicator.IsRunning = false;
            loadingIndicator.IsVisible = false;
            messageTitle.Text = title;
            messageText.Text = text;
            messageIcon.IsVisible = canRetry;
            retryButton.IsVisible = canRetry;
            messageView.IsVisible = true;
        }
    }
}
