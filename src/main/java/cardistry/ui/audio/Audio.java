package cardistry.ui.audio;

import cardistry.utils.Log;
/**
 * Le son du jeu, piloté par l'interface et l'{@link AudioDirector} (jamais par la logique).
 * {@link #silent()} : aucune sortie (tests, CI, pas de carte son, {@code cardistry.audio=off}).
 */
public interface Audio extends AutoCloseable {

    /** Joue l'effet {@code name} de la table (ex. {@code sfx.card.fire}) ; rien s'il manque. */
    void effect(String name);

    /** Change de musique (fondu enchaîné) ; même contexte : rien ; {@code null} : silence. */
    void music(MusicContext context);

    /** Volumes (0 à 10) et muet, appliqués tout de suite. */
    void setLevels(int music, int effects, boolean muted);

    @Override
    void close();

    /** Son coupé : ne fait rien. */
    static Audio silent() {
        return new Audio() {
            @Override
            public void effect(String name) {
            }

            @Override
            public void music(MusicContext context) {
            }

            @Override
            public void setLevels(int music, int effects, boolean muted) {
            }

            @Override
            public void close() {
            }
        };
    }

    /**
     * Son réel si possible, sinon silencieux (sans erreur ni message bloquant).
     *
     * @param mute {@code --mute} : tout coupé pour cette session
     * @param noMusic {@code --no-music} : musique coupée pour cette session
     */
    static Audio open(int music, int effects, boolean muted, boolean mute, boolean noMusic) {
        if (mute || "off".equalsIgnoreCase(System.getProperty("cardistry.audio", "on"))) {
            Log.info("Son coupé (--mute ou cardistry.audio=off)");
            return silent();
        }
        return SoftwareAudio.start(SoundTable.load(), music, effects, muted, noMusic).orElseGet(Audio::silent);
    }
}
