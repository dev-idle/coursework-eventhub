namespace EventHub
{
    // The search of the admin app: a word in the text starts with what was typed, in any case.
    public static class Search
    {
        public static bool StartsWord(string text, string search)
        {
            return text.StartsWith(search, StringComparison.OrdinalIgnoreCase)
                || text.Contains(" " + search, StringComparison.OrdinalIgnoreCase);
        }
    }
}
