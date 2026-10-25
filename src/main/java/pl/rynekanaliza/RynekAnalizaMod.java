package pl.rynekanaliza;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Analiza rynku dla BK-Rynek.
 *   /analiza start [fragment nazwy]   - analiza wszystkich (albo wybranych) przedmiotów z listy BK
 *   /analiza stop
 *   /analiza lista                    - przedmioty, ich cena skupu i marża
 *   /analiza marza <kwota> <fragment> - marża w $ dla przedmiotów o takiej nazwie
 *   /analiza marza_domyslna <kwota>
 * Klawisz (domyślnie F8, do zmiany w sterowaniu): start/stop analizy wszystkich przedmiotów.
 */
public class RynekAnalizaMod implements ClientModInitializer {
    private static AnalizaConfig cfg;
    private static boolean bkWasRunning;

    @Override
    public void onInitializeClient() {
        cfg = AnalizaConfig.load();
        MarketAnalyzer.init();

        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rynek_analiza.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "category.rynek_analiza"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.wasPressed()) {
                if (MarketAnalyzer.isRunning()) MarketAnalyzer.stop();
                else startAnalysis("");
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("analiza")
                        .then(ClientCommandManager.literal("start")
                                .executes(ctx -> { startAnalysis(""); return 1; })
                                .then(ClientCommandManager.argument("fragment", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            startAnalysis(StringArgumentType.getString(ctx, "fragment"));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("stop")
                                .executes(ctx -> { MarketAnalyzer.stop(); return 1; }))
                        .then(ClientCommandManager.literal("lista")
                                .executes(ctx -> { printList(ctx.getSource()); return 1; }))
                        .then(ClientCommandManager.literal("marza")
                                .then(ClientCommandManager.argument("kwota", DoubleArgumentType.doubleArg(0))
                                        .then(ClientCommandManager.argument("fragment", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    setMargin(ctx.getSource(),
                                                            DoubleArgumentType.getDouble(ctx, "kwota"),
                                                            StringArgumentType.getString(ctx, "fragment"));
                                                    return 1;
                                                }))))
                        .then(ClientCommandManager.literal("marza_domyslna")
                                .then(ClientCommandManager.argument("kwota", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> {
                                            cfg.defaultMargin = DoubleArgumentType.getDouble(ctx, "kwota");
                                            cfg.save();
                                            ctx.getSource().sendFeedback(Text.literal("[Analiza] Domyślna marża: " + cfg.defaultMargin + "$"));
                                            return 1;
                                        })))));
    }

    private static void startAnalysis(String filter) {
        if (MarketAnalyzer.isRunning()) {
            MarketAnalyzer.log.accept("Analiza już trwa (/analiza stop, żeby przerwać).");
            return;
        }
        if (!BkBridge.available()) {
            MarketAnalyzer.log.accept("Nie widzę BK-Rynek 3.0 (" + BkBridge.error() + ").");
            return;
        }
        try {
            List<BkBridge.BkTarget> targets = BkBridge.targets(filter, cfg);
            if (targets.isEmpty()) {
                MarketAnalyzer.log.accept("Brak przedmiotów na liście BK" + (filter.isBlank() ? "." : " pasujących do: " + filter));
                return;
            }
            if (!BkBridge.usesBkMatcher()) {
                MarketAnalyzer.log.accept("Uwaga: używam własnego dopasowania (nie BK): " + BkBridge.error());
            }
            cfg.applyToAnalyzer();
            MarketAnalyzer.PRICE_REGEX = BkBridge.priceRegex();

            bkWasRunning = BkBridge.isRunning();
            if (bkWasRunning) BkBridge.stopBk();

            MarketAnalyzer.onFinished = () -> {
                BkBridge.commit();
                if (bkWasRunning && cfg.resumeBk) BkBridge.startBk();
            };
            if (!MarketAnalyzer.start(targets)) {
                MarketAnalyzer.log.accept("Nie udało się wystartować analizy.");
                if (bkWasRunning && cfg.resumeBk) BkBridge.startBk();
            }
        } catch (Throwable t) {
            MarketAnalyzer.log.accept("Błąd startu analizy: " + t);
        }
    }

    private static void printList(FabricClientCommandSource src) {
        try {
            if (!BkBridge.available()) { src.sendFeedback(Text.literal("[Analiza] Brak BK-Rynek: " + BkBridge.error())); return; }
            for (String line : BkBridge.describe(cfg)) src.sendFeedback(Text.literal("[Analiza] " + line));
        } catch (Throwable t) {
            src.sendFeedback(Text.literal("[Analiza] Błąd: " + t));
        }
    }

    private static void setMargin(FabricClientCommandSource src, double amount, String fragment) {
        try {
            int n = BkBridge.setMargin(fragment, amount, cfg);
            src.sendFeedback(Text.literal("[Analiza] Marża " + amount + "$ ustawiona dla " + n + " przedm."));
        } catch (Throwable t) {
            src.sendFeedback(Text.literal("[Analiza] Błąd: " + t));
        }
    }
}
