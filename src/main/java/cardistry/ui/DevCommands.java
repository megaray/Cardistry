package cardistry.ui;

import cardistry.core.DevCheats;
import cardistry.core.GameState;
import cardistry.core.status.StatusTarget;
import cardistry.core.status.StatusType;
import cardistry.items.ItemRegistry;
import cardistry.model.card.CardPool;
import cardistry.run.Run;
import cardistry.utils.Log;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registre des commandes de la console de développement ({@code --debug}, F12). Une commande =
 * un nom, sa syntaxe, une aide et une action ; ajouter une commande = une ligne {@link #add}.
 * Les triches passent par {@link DevCheats} et {@link Run#cheat} (rejouées par Ctrl+Z).
 */
public final class DevCommands {

    /** Ce qu'une commande reçoit : la partie en cours (null au titre) et ses arguments. */
    public record Call(Run run, List<String> args) {

        GameState state() {
            if (run == null) {
                throw new IllegalStateException("Pas de partie en cours");
            }
            return run.state();
        }

        String arg(int index, String usage) {
            if (index >= args.size()) {
                throw new IllegalArgumentException("Syntaxe : " + usage);
            }
            return args.get(index);
        }

        int number(int index, String usage) {
            try {
                return Integer.parseInt(arg(index, usage));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Nombre attendu : " + args.get(index));
            }
        }

        /** Arguments à partir de {@code from}, recollés (noms de cartes à espaces). */
        String rest(int from, String usage) {
            arg(from, usage);
            return String.join(" ", args.subList(from, args.size()));
        }

        /** Applique une triche (rejouée par Ctrl+Z). */
        void cheat(java.util.function.Consumer<GameState> change) {
            state();
            run.cheat(change);
        }
    }

    /** Une commande : renvoie le texte à écrire dans le journal. */
    @FunctionalInterface
    public interface Action {
        String run(Call call);
    }

    public record Command(String name, String usage, String help, Action action) {
    }

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    static {
        add("help", "help", "liste des commandes", c -> COMMANDS.values().stream()
                .map(cmd -> cmd.usage() + " — " + cmd.help()).collect(Collectors.joining("\n")));
        add("clear", "clear", "vide le journal affiché", c -> {
            Log.clearMemory();
            return "";
        });
        add("level", "level debug|info|warn|error", "niveau du journal", c -> {
            Log.Level level = Log.Level.valueOf(c.arg(0, "level debug|info|warn|error").toUpperCase(Locale.ROOT));
            Log.setLevel(level);
            return "Journal : " + level;
        });
        add("seed", "seed", "seed de la partie", c -> {
            c.state();
            return "Seed : " + c.run().seed();
        });
        add("state", "state", "résumé de la partie", c -> describe(c.state()));
        add("list", "list cards|items|trinkets|status", "identifiants utilisables", c -> {
            String what = c.arg(0, "list cards|items|trinkets|status");
            return switch (what) {
                case "cards" -> new CardPool().cards().stream().map(cardistry.model.card.Card::name).distinct()
                        .collect(Collectors.joining(", "));
                case "items" -> ItemRegistry.items().stream().map(i -> i.id()).collect(Collectors.joining(", "));
                case "trinkets" -> ItemRegistry.trinkets().stream().map(t -> t.id()).collect(Collectors.joining(", "));
                case "status" -> Arrays.stream(StatusType.values()).map(StatusType::id)
                        .collect(Collectors.joining(", "));
                default -> throw new IllegalArgumentException("cards, items, trinkets ou status");
            };
        });
        add("hp", "hp <n>", "PV du joueur", c -> {
            int hp = c.number(0, "hp <n>");
            c.cheat(DevCheats.playerHp(hp));
            return "PV du joueur : " + hp;
        });
        add("ehp", "ehp <n>", "PV de l'adversaire", c -> {
            int hp = c.number(0, "ehp <n>");
            c.cheat(DevCheats.enemyHp(hp));
            return "PV de l'adversaire : " + hp;
        });
        add("kill", "kill", "adversaire à 1 PV (le prochain coup le tue)", c -> {
            c.cheat(DevCheats.enemyHp(1));
            return "Adversaire à 1 PV";
        });
        add("choomies", "choomies <n>", "Choomies du joueur", c -> {
            int n = c.number(0, "choomies <n>");
            c.cheat(DevCheats.choomies(n));
            return "Choomies : " + n;
        });
        add("cigs", "cigs <n>", "clopes du paquet", c -> {
            int n = c.number(0, "cigs <n>");
            c.cheat(DevCheats.cigarettes(n));
            return "Clopes : " + n;
        });
        add("drunk", "drunk <n>", "Ivresse", c -> {
            int n = c.number(0, "drunk <n>");
            c.cheat(DevCheats.drunkenness(n));
            return "Ivresse : " + n;
        });
        add("card", "card <nom>", "carte dans la main", c -> {
            String name = c.rest(0, "card <nom>");
            c.cheat(DevCheats.cardToHand(name));
            return "Main : + " + DevCheats.findCard(name).name();
        });
        add("deck", "deck <nom>", "carte dans le deck", c -> {
            String name = c.rest(0, "deck <nom>");
            c.cheat(DevCheats.cardToDeck(name));
            return "Deck : + " + DevCheats.findCard(name).name();
        });
        add("item", "item <id>", "objet dans l'inventaire", c -> {
            String id = c.arg(0, "item <id>");
            c.cheat(DevCheats.item(id));
            return "Objet : + " + ItemRegistry.item(id).name();
        });
        add("trinket", "trinket <id>", "trinket équipé", c -> {
            String id = c.arg(0, "trinket <id>");
            c.cheat(DevCheats.trinket(id));
            return "Trinket : + " + ItemRegistry.trinket(id).name();
        });
        add("status", "status <id> <tours> [joueur]", "statut sur l'adversaire (ou le joueur)", c -> {
            String usage = "status <id> <tours> [joueur]";
            String id = c.arg(0, usage);
            int turns = c.number(1, usage);
            StatusTarget target = c.args().size() > 2 && c.args().get(2).startsWith("j")
                    ? StatusTarget.PLAYER : StatusTarget.ENEMY;
            c.cheat(DevCheats.status(id, turns, target));
            return StatusType.byId(id).displayName() + " " + turns + " tours sur "
                    + (target == StatusTarget.PLAYER ? "le joueur" : "l'adversaire");
        });
        add("stat", "stat <FOR|INT|…> <n>", "bonus plat à une stat", c -> {
            String usage = "stat <FOR|INT|…> <n>";
            var stat = DevCheats.statType(c.arg(0, usage));
            int value = c.number(1, usage);
            c.cheat(DevCheats.stat(stat, value));
            return stat.displayName() + " " + (value >= 0 ? "+" : "") + value;
        });
    }

    private DevCommands() {
    }

    /** Ajoute une commande au registre. */
    public static void add(String name, String usage, String help, Action action) {
        COMMANDS.put(name, new Command(name, usage, help, action));
    }

    public static List<Command> all() {
        return List.copyOf(COMMANDS.values());
    }

    /**
     * Exécute {@code line} et écrit la commande puis son résultat (ou l'erreur) dans le journal.
     *
     * @return {@code true} si la commande a réussi
     */
    public static boolean execute(String line, Run run) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        Log.info("> " + trimmed);
        List<String> words = List.of(trimmed.split("\\s+"));
        Command command = COMMANDS.get(words.get(0).toLowerCase(Locale.ROOT));
        if (command == null) {
            Log.warn("Commande inconnue : " + words.get(0) + " (help pour la liste)");
            return false;
        }
        try {
            String result = command.action().run(new Call(run, words.subList(1, words.size())));
            result.lines().forEach(Log::info);
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.warn(e.getMessage());
            return false;
        } catch (RuntimeException e) {
            Log.error("La commande « " + trimmed + " » a échoué", e);
            return false;
        }
    }

    /** Résumé d'une ligne par sujet : PV, Choomies, inventaire, adversaire, statuts. */
    static String describe(GameState state) {
        StringBuilder text = new StringBuilder();
        text.append("Joueur ").append(state.player().hp()).append("/").append(Math.round(state.maxHp()))
                .append(" PV, ").append(state.choomies()).append(" Choomies, niveau ").append(state.level())
                .append(" étage ").append(state.floor()).append(", tour ").append(state.turn());
        text.append("\nMain : ").append(state.hand().stream().map(cardistry.model.card.Card::name)
                .collect(Collectors.joining(", ")));
        text.append("\nTrinkets : ").append(state.trinkets().stream().map(t -> t.name())
                .collect(Collectors.joining(", ")));
        text.append("\nObjets : ").append(state.items().stream().map(i -> i.name())
                .collect(Collectors.joining(", ")));
        text.append("\nStatuts joueur : ").append(state.statuses(StatusTarget.PLAYER).all());
        if (state.hasEnemy()) {
            text.append("\nAdversaire : ").append(EventLog.value(state.currentEnemy()))
                    .append(", statuts ").append(state.statuses(StatusTarget.ENEMY).all());
        }
        return text.toString();
    }
}
