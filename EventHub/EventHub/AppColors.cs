namespace EventHub
{
    // Looks up a color of Colors.xaml for the light or dark theme in use.
    public static class AppColors
    {
        public static Color Get(string key)
        {
            Application app = Application.Current!;
            string themedKey = app.RequestedTheme == AppTheme.Dark ? key + "Dark" : key;
            return app.Resources.TryGetValue(themedKey, out object? color)
                ? (Color)color : Colors.Transparent;
        }
    }
}
