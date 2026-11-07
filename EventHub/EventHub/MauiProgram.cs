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

#if ANDROID
            // The search field has no underline, as in the admin app
            Microsoft.Maui.Handlers.EntryHandler.Mapper.AppendToMapping("NoUnderline", (handler, view) =>
                handler.PlatformView.Background = null);
#endif

#if DEBUG
    		builder.Logging.AddDebug();
#endif

            return builder.Build();
        }
    }
}
