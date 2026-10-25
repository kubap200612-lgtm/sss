package pl.rynekanaliza;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ustawienia w config/rynek-analiza.json (marże i ewentualne własne frazy wyszukiwania). */
public final class AnalizaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Marża w $ używana, gdy przedmiot nie ma własnej. */
    public double defaultMargin = 100;
    /** Klucz = "nazwa bez kolorów|material" (małe litery), wartość = marża w $ (np. 100, 200, 500). */
    public Map<String, Double> margins = new LinkedHashMap<>();
    /** Klucz jak wyżej, wartość = fraza wpisywana po "/ah search " (domyślnie nazwa bez kolorów). */
    public Map<String, String> search = new LinkedHashMap<>();
    public double taxFactor = 0.97;
    public int listingsUsed = 3;
    public int minListings = 3;
    public int maxPages = 20;
    public int clickDelayTicks = 6;
    /** Czy po analizie włączyć z powrotem BK-Rynek, jeśli działał przed startem. */
    public boolean resumeBk = true;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rynek-analiza.json");
    }

    public static AnalizaConfig load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                AnalizaConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), AnalizaConfig.class);
                if (c != null) {
                    if (c.margins == null) c.margins = new LinkedHashMap<>();
                    if (c.search == null) c.search = new LinkedHashMap<>();
                    return c;
                }
            }
        } catch (Exception e) {
            System.err.println("[rynek-analiza] nie udało się wczytać configu: " + e);
        }
        AnalizaConfig c = new AnalizaConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[rynek-analiza] nie udało się zapisać configu: " + e);
        }
    }

    /** Przenosi ustawienia do silnika analizy. */
    public void applyToAnalyzer() {
        MarketAnalyzer.TAX_FACTOR = taxFactor;
        MarketAnalyzer.LISTINGS_USED = Math.max(1, listingsUsed);
        MarketAnalyzer.MIN_LISTINGS = Math.max(1, minListings);
        MarketAnalyzer.MAX_PAGES = Math.max(1, maxPages);
        MarketAnalyzer.CLICK_DELAY_TICKS = Math.max(2, clickDelayTicks);
    }
}
