using System.Collections.ObjectModel;
using Firebase.Database;

namespace EventHub
{
    public partial class MainPage : ContentPage
    {
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
            Connectivity.Current.ConnectivityChanged += OnConnectivityChanged;
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

        private void OnRetryClicked(object? sender, EventArgs e)
        {
            GetEvents();
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
                    var items = await firebaseClient.Child("events").OnceAsync<Event>();
                    events.Clear();
                    foreach (var item in items)
                    {
                        item.Object.Key = item.Key;
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

        // Shows the events in start order under their months, or a message if there are none
        private void ShowEvents()
        {
            if (!loaded)
            {
                return;
            }
            DateTime now = DateTime.Now;
            List<Event> shown = events.Values
                .Where(e => e.HasValidTimes)
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
            countLabel.Text = shown.Count == 1 ? "1 event" : $"{shown.Count} events";
            countLabel.IsVisible = shown.Count > 0;
            if (shown.Count == 0)
            {
                ShowMessage("No events yet", "Events appear here once they are published.", false);
            }
            else
            {
                messageView.IsVisible = false;
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
