using Microsoft.Extensions.Logging;

namespace EventHub
{
    public static class MauiProgram
    {
        public static MauiApp CreateMauiApp()
        {
            var builder = MauiApp.CreateBuilder();
            // The platform font, Roboto on Android, as in the admin app.
            builder.UseMauiApp<App>();

#if DEBUG
    		builder.Logging.AddDebug();
#endif

            return builder.Build();
        }
    }
}
