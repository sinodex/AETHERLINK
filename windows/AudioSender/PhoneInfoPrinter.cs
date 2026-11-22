using System.Text.Json;

namespace AudioBridge;

/// <summary>Turns the status JSON sent by the phone into console lines; a line is only printed when it changes.</summary>
sealed class PhoneInfoPrinter
{
    string lastHeadphones = "", lastPhone = "", lastBuffer = "";

    public void Print(ReadOnlyMemory<byte> json)
    {
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        string headphones = "Kopfhörer: keine erkannt (Bluetooth-Berechtigung in der App erlaubt?)";
        if (root.TryGetProperty("bt", out var bt) && bt.ValueKind == JsonValueKind.Object)
        {
            int battery = Int(bt, "battery");
            headphones =
                $"Kopfhörer: {Str(bt, "alias")} | Marke: {Str(bt, "brand")} (aus Name erraten) | MAC: {Str(bt, "address")} | " +
                $"Akku: {(battery >= 0 ? battery + "%" : "nicht auslesbar")} | Typ: {Str(bt, "class")} | " +
                $"Profile: {Str(bt, "profiles")} | {Str(bt, "bond")}, {Str(bt, "type")}";
        }

        string phone =
            $"Handy: {Str(root, "manufacturer")} {Str(root, "model")} | Akku {Int(root, "battery")}%" +
            $"{(Flag(root, "charging") ? " (lädt)" : "")} | Mikro-Streaming: {(Flag(root, "mic") ? "aktiv" : "aus")}";

        int outputMs = Int(root, "out_ms");
        string buffer =
            $"Puffer am Handy: {Int(root, "buf_ms")} ms | Handy->Kopfhörer gesamt: {(outputMs >= 0 ? "~" + outputMs / 10 * 10 + " ms" : "?")} | " +
            $"Underruns: {Int(root, "underruns")} | verworfene Frames: {Int(root, "dropped")}";

        PrintIfChanged(headphones, ref lastHeadphones);
        PrintIfChanged(phone, ref lastPhone);
        PrintIfChanged(buffer, ref lastBuffer);
    }

    static void PrintIfChanged(string line, ref string last)
    {
        if (line == last) return;
        Console.WriteLine(line);
        last = line;
    }

    static string Str(JsonElement e, string key) =>
        e.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "?" : "?";

    static int Int(JsonElement e, string key) =>
        e.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.Number ? v.GetInt32() : -1;

    static bool Flag(JsonElement e, string key) =>
        e.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.True;
}
