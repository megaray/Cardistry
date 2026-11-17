# Cardistry

Jeu de cartes roguelite au tour par tour, en Java. Il s'ouvre dans sa propre fenêtre, en pixel art
(carte, combats, cartes illustrées), et se joue aussi en plein écran dans un terminal
([Lanterna](https://github.com/mabe02/lanterna)) ou en mode texte. Tourne sous **Linux, macOS et Windows**.

## Installer l'app

Chaque version est publiée dans les **[Releases GitHub](https://github.com/megaray/Cardistry/releases)** :
une app autonome, **Java inclus** (rien d'autre à installer). Les paquets ne sont **pas signés** : au
premier lancement, macOS et Windows affichent un avertissement, à passer une seule fois (voir plus bas).

### macOS (Apple Silicon)

1. Télécharger `Cardistry-<version>-macos-arm64.dmg`, l'ouvrir, glisser **Cardistry** dans
   **Applications**.
2. Premier lancement : macOS (Gatekeeper) refuse d'ouvrir une app d'un « développeur non identifié ».
   - Ouvrir Cardistry une fois (le message s'affiche), puis **Réglages Système → Confidentialité et
     sécurité**, descendre jusqu'au message sur Cardistry et cliquer **« Ouvrir quand même »**, confirmer.
   - Sur les versions plus anciennes de macOS, **clic droit (ou Ctrl+clic) sur l'app → Ouvrir → Ouvrir**
     suffit.
   - Ou, dans le Terminal : `xattr -dr com.apple.quarantine /Applications/Cardistry.app`
3. Ensuite, Cardistry s'ouvre normalement. Désinstaller : glisser l'app à la corbeille.

Pas de version pour les Mac Intel pour l'instant.

### Windows

1. Télécharger `Cardistry-<version>-windows-x64.msi` et le lancer. L'installation se fait pour
   l'utilisateur courant (pas besoin d'être administrateur) ; un raccourci est créé dans le menu
   Démarrer et sur le bureau.
2. Premier lancement : Windows (SmartScreen) affiche « Windows a protégé votre ordinateur ». Cliquer sur
   **« Informations complémentaires »**, puis **« Exécuter quand même »**.
3. Sans installation : le `.zip` portable ; le décompresser et lancer `Cardistry\Cardistry.exe`.
4. Désinstaller : **Paramètres → Applications → Cardistry → Désinstaller**.

### Linux

- Debian, Ubuntu : `sudo apt install ./cardistry_<version>_amd64.deb`
- Fedora, openSUSE : `sudo dnf install ./cardistry-<version>.x86_64.rpm`

Cardistry apparaît dans le menu des applications (catégorie Jeux) ; en ligne de commande :
`/opt/cardistry/bin/Cardistry` (options comprises, ex. `--terminal`). Désinstaller :
`sudo apt remove cardistry` ou `sudo dnf remove cardistry`.

Les déblocages et réglages ne sont pas effacés à la désinstallation (voir « Sauvegarde »).

## Prérequis (pour lancer depuis les sources)

- **JDK 21 ou plus récent** (`java -version` pour vérifier). Gradle n'a pas besoin d'être installé :
  le wrapper (`gradlew`, `gradlew.bat`) le télécharge.
- Un terminal d'au moins **80×24** pour le plein écran.

## Lancer le jeu

| OS | Commande |
|---|---|
| Linux, macOS | `./play` |
| Windows | `play.bat` |

Par défaut, le jeu s'ouvre dans **sa propre fenêtre** sur les trois OS : il y dessine lui-même sa grille
de caractères avec une police pixel embarquée (aucun scintillement) et y ajoute les images : sprites,
icônes, carte (voir « Interface de la fenêtre »).

- `--terminal` : plein écran dans le terminal (Lanterna), comme avant. Il faut un vrai terminal d'au
  moins **80×24**.
- `--no-ui` : mode texte simple (lignes, saisie + Entrée).
- Sans affichage graphique (SSH, CI) : plein écran terminal si c'est un vrai terminal, sinon mode texte.

> `./gradlew run` lance aussi le jeu ; en `--terminal`, Gradle ne transmet pas un vrai terminal et le
> jeu passe en mode texte.

### Menus

- Écran titre : **Jouer**, **Règles**, **Options**, **Quitter** (↑/↓ ou ←/→, Entrée ; R et Q en raccourci).
- Puis le **choix du personnage** (←/→ ou 1-3, Entrée ; Échap revient au titre) : **Louis** (MAN : premier
  sort de chaque combat +50 %, Gueule de bois doublée), **Dave** (Full skill Mr. Le Juge : un sort de
  l'élément du terrain le soigne des PV enlevés), **Sassy** (wadayatalkinabit : 130 PV, un alien
  inflige 4 dégâts à chaque tour, sauf quand il fume un joint).
- En jeu, **Échap** ouvre le menu **pause** (Reprendre, Options, Quitter la partie) sur la carte, en combat, à la
  Sieste, dans un événement et sur les écrans de récompense. Dans un sous-écran (remplacer un objet,
  faire le tri, inventaire…), Échap annule d'abord ; au shop, P fait partir.
- **Quitter la partie** (menu pause, ou Q puis O) ramène à l'**écran titre**, prêt pour une nouvelle
  partie ; la fin d'une partie aussi. Seul « Quitter » de l'écran titre (ou fermer la fenêtre) ferme le jeu.
  En mode texte (`--no-ui`), la fin de la partie termine le programme.
- **Options** : volume de la musique et des effets (0 à 10, ←/→, appliqué tout de suite) et son coupé.
  Sauvegardé dans `settings.txt`, à côté des déblocages. En mode texte : taper `options` à l'écran titre.

### Dans la fenêtre

| Touche | Effet |
|---|---|
| F10 | filtre CRT (lignes de balayage) : activer / couper |
| F11 | agrandir / restaurer la fenêtre |
| Ctrl + / Ctrl − (Cmd sous macOS) | taille de la police (multiples nets de la police pixel) |

La fenêtre se redimensionne librement (80×24 cellules au minimum). La fermer, ou Cmd+Q sous macOS,
quitte le jeu (les déblocages sont déjà sauvegardés).

### Options

| Option | Effet |
|---|---|
| `--seed N` | rejoue la partie de seed N |
| `--reset-unlocks` | efface les secrets débloqués, puis lance le jeu |
| `--sprites` | visionneuse : fait défiler les sprites des adversaires et leurs animations, sans partie |
| `--fast` | sans pauses ni animations |
| `--terminal` | plein écran dans le terminal au lieu de la fenêtre |
| `--no-ui` | mode texte simple |
| `--crt` | fenêtre : filtre CRT activé au départ (désactivé par défaut) |
| `--no-images` | fenêtre : aucun PNG, les écrans en texte (sprites ASCII, carte en lettres) |
| `--mute` | sans aucun son (cette session seulement) |
| `--no-music` | sans musique, effets gardés (cette session seulement) |
| `--ascii` | ASCII pur (cadres `+-|`, barres `#`, accents retirés) |
| `--no-color` | mode texte sans couleurs ANSI |
| `--window` | ancien nom de la fenêtre (c'est le mode par défaut) |
| `--debug` | journal détaillé (chaque événement du jeu) et console de développement (F12) |

Exemples : `./play --seed 42 --crt`, `./play --terminal`, `play.bat --no-ui --no-color`.

### Journal de débogage

`debug.log` est écrit dans le dossier de sauvegarde (Linux `~/.local/share/cardistry`, macOS
`~/Library/Application Support/Cardistry`, Windows `%APPDATA%\Cardistry`) ; celui du lancement
précédent est gardé en `debug.1.log`. Par défaut : démarrage, seed, erreurs et plantages (avec la
pile) ; avec `--debug` : tous les événements du jeu. Dans le code : `Log.debug / info / warn / error`
(`cardistry.utils.Log`), le fichier et la ligne de l'appelant sont ajoutés tout seuls. Les tests
n'écrivent aucun fichier (`-Dcardistry.log=off`).

## Particularités par OS

- **Linux et macOS**, en `--terminal` : l'ASCII pur s'active tout seul si le terminal n'est pas en UTF-8
  (ex. `LANG=C`) ; Ctrl+Z est une touche du jeu, il ne suspend pas le programme.
- **Windows**, en `--no-ui` dans la console : les couleurs ne sont activées que dans **Windows Terminal**
  ou un terminal de type Unix (Git Bash) ; dans la vieille console `cmd`, elles sont coupées
  automatiquement. Si la console n'est pas en UTF-8, le mode texte passe en ASCII pur (`chcp 65001` pour
  les accents). Lanterna 3.1 n'a pas de plein écran dans la console Windows : sous Windows, utiliser la
  fenêtre (par défaut).

## Polices

La fenêtre embarque deux polices, toutes deux sous **SIL Open Font License 1.1** (licences complètes dans
`src/main/resources/fonts/`) :

- **Departure Mono** 1.500 (© 2022–2024 Helena Zhang) — police pixel principale ;
  https://github.com/rektdeckard/departure-mono
- **JetBrains Mono** 2.304 (© 2020 The JetBrains Mono Project Authors) — secours pour les caractères
  absents de la première (▶) ; https://github.com/JetBrains/JetBrainsMono

## Sauvegarde

Le jeu range ses données (secrets débloqués, dans `unlocks.txt`, un fichier texte lisible) dans :

| OS | Dossier |
|---|---|
| Linux | `$XDG_DATA_HOME/cardistry`, sinon `~/.local/share/cardistry` |
| macOS | `~/Library/Application Support/Cardistry` |
| Windows | `%APPDATA%\Cardistry` |

Fichier absent ou abîmé : le jeu repart de zéro sans planter. `--reset-unlocks` remet tout à zéro.

## Sprites ASCII des adversaires

Ces sprites servent en `--terminal`, avec `--no-images` et en mode texte ; dans la fenêtre, ce sont les
images PNG qui sont affichées (voir « Interface de la fenêtre »).

Un fichier texte par sprite : `src/main/resources/sprites/<id>.txt` (l'`id` est celui du registre des
adversaires, ex. `caddie-enerve`). Zone maximale : **30 colonnes × 10 lignes**. Le dessin est en
**ASCII pur** (un test le vérifie), donc il s'affiche tel quel avec `--ascii`.

```
@name Caddie énervé       nom affiché par la visionneuse (facultatif)
@# commentaire
@frame idle               une frame ; 2-3 blocs « idle » = animation au repos (en boucle)
...le dessin...
@colors                   facultatif : couleurs de la frame juste au-dessus
...une lettre par caractère du dessin
@frame idle
...
@frame attack             frames jouées quand l'adversaire attaque
@frame hurt               quand il prend des dégâts (sinon : clignotement)
@frame death              à sa mort (sinon : clignotement puis disparition)
```

- **Couleurs** : `r` rouge, `g` vert, `y` jaune, `b` bleu, `m` magenta, `c` cyan, `w` blanc, `k` gris ;
  en majuscule, la version vive. `.` ou espace : couleur de l'élément de l'adversaire.
- Une frame sans `@colors` reprend les couleurs de la première frame du fichier.
- Un fichier sans aucune directive est une seule frame idle, sans couleurs.
- Pas de fichier : sprite `inconnu.txt`. Choomah, Clarence et les boss ont un cadre provisoire à
  remplacer.
- Pour voir le résultat : `./play --sprites` (←/→ change de sprite, 1-4 joue idle / attaque / dégâts /
  mort, Q quitte ; en mode texte, les frames défilent).

## Interface de la fenêtre

Dans la fenêtre, le jeu dessine des images PNG par-dessus sa grille de caractères. Avec `--terminal` ou
`--no-images`, il garde ses écrans en texte (sprites ASCII, carte en lettres, journal toujours visible).

**Carte** — de gauche (étage 1) à droite (boss) : une icône par nœud, des chemins courbes en
pointillés, les nœuds décalés dans leur colonne. Le tracé vient de la seed : même seed, même dessin.
Le nœud où tu es est cerclé d'or, le chemin parcouru est en or, le nœud sélectionné est cerclé de
blanc, les autres nœuds accessibles pulsent, ceux que tu ne peux plus atteindre sont estompés.

**Combat** — toi à gauche, l'adversaire à droite, une barre de PV sous chaque sprite. L'attaque
annoncée (intent) est au-dessus de l'adversaire avec son icône ; tes états (Ivresse, Gueule de bois,
effets en cours) sont à côté de ton sprite. En bas, la main en cartes illustrées (illustration, icône
de type, valeur, nom, cadre à la couleur de l'élément), puis une barre d'icônes : objets, trinkets,
paquet de clopes.

| Touche (combat) | Effet |
|---|---|
| ←/→ puis Entrée, ou 1-7 | jouer une carte |
| P | piocher |
| W / E / R | utiliser un objet |
| F / G | fumer (gratuit : +5 PV, −1 Ivresse) / rouler une clope (fin du tour) |
| J | ouvrir ou fermer le **journal** (↑/↓ pour défiler, Échap le ferme aussi) |
| I | inventaire |

Le journal est fermé par défaut : la dernière action tient sur une ligne en bas de l'écran.

**Couleurs** — le cadre d'une carte porte la couleur de son élément (feu rouge, eau bleu, plante vert,
neutre gris). Le cadre d'une icône d'objet ou de trinket porte la couleur de sa rareté (Commun blanc,
Peu commun vert, Rare bleu, Légendaire jaune, Secret magenta), en combat, dans les récompenses et au shop.

**Shop** — P pour partir ; Échap ouvre la pause, comme ailleurs.

### Fichiers d'images

Tout est dans `src/main/resources/images/`, en pixel art agrandi d'un facteur **entier**, sans lissage.

| Dossier | Contenu | Taille |
|---|---|---|
| `enemies/` | `<id>.<animation>.<n>.png` (`n` à partir de 1 ; animations `idle`, `attack`, `hurt`, `death`) et `<id>.png`, l'image de base | 64×64 |
| `player/player.png` | le joueur (placeholder) ; `player/louis.png`, `dave.png`, `sassy.png` sont pris s'ils existent | 64×64 |
| `npc/marchand.idle.<n>.png` | le marchand du shop (fenêtre d'au moins 100×30) | 64×64 |
| `cards/<id>.png` | illustration d'une carte ; `id` = nom en minuscules, sans accents, avec des tirets (`jet-d-eau`) | 24×24 |
| `items/<id>.png` | icône d'un objet ou d'un trinket (même `id` que dans le registre) | 16×16 |
| `map/<type>.png` | nœuds de la carte : `combat`, `elite`, `boss`, `shop`, `rest`, `event` | 16×16 |
| `ui/` | icônes de type de carte (`type-*.png`), d'intent (`intent-*.png`), paquet de clopes, placeholders | 8×8 et 16×16 |

- **Adversaires** : le sprite est affiché ×3 en 80×24 et ×6 en 100×32. Les dégâts le font clignoter en
  blanc, la mort l'estompe. Une animation plus courte que prévu reste sur sa dernière frame ; une
  animation absente utilise `<id>.png`. Choomah, Clarence et le boss ont un placeholder à redessiner.
- **Image absente** : une carte montre son icône de type en grand, à la couleur de son élément ; un objet
  ou un trinket, le placeholder de sa catégorie ; un adversaire, son sprite ASCII ; un nœud, sa lettre.
- `./play --sprites` montre l'image d'un adversaire et son repli ASCII côte à côte.

## Pixel art (Aseprite)

Les sources sont dans `art/` (hors du jeu), les PNG exportés sont commités dans
`src/main/resources/images/` : sans Aseprite, rien à faire, le jeu utilise les PNG.

Pour **retoucher un dessin** : l'ouvrir dans Aseprite (`art/items.aseprite`, `art/enemies/came.aseprite`…),
le modifier, enregistrer, puis `./gradlew exportArt planche`. Pour **ajouter** une icône : une nouvelle
frame avec un tag à son id.

| Source | Contenu | Export |
|---|---|---|
| `art/palette.gpl` | palette commune (27 couleurs : celles de la fenêtre, des éléments, des raretés, plus 9 teintes) | — |
| `art/map.aseprite` | icônes de la carte du monde, 16×16 | `images/map/<tag>.png` |
| `art/items.aseprite`, `art/trinkets.aseprite` | icônes d'objets et de trinkets, 16×16 | `images/items/<tag>.png` |
| `art/cards.aseprite` | illustrations de cartes, 24×24 | `images/cards/<tag>.png` |
| `art/enemies/<id>.aseprite` | sprite d'un adversaire, 64×64, tourné vers la gauche ; tags `idle`, `attack`, `hurt`, `death` | `images/enemies/<id>.<tag>.<n>.png` et `<id>.png` |
| `art/npc/marchand.aseprite` | le marchand du shop, 64×64, tourné vers la droite ; tag `idle` (frame 2 = il tire sur sa clope). Le bout de la clope doit rester en (51, 27) : la fumée animée part de là | `images/npc/marchand.idle.<n>.png` et `marchand.png` |

Une icône = une frame, avec un **tag** qui porte son id (`space-cake`, `m1`, `boule-de-feu`…) : c'est le
tag qui donne le nom du PNG. Pour un adversaire, un tag = une animation (une ou plusieurs frames).

- `./gradlew art` : tout d'un coup (les trois tâches ci-dessous).
- `./gradlew generateArt` : ajoute aux `.aseprite` les ébauches **manquantes** (`art/tools/generate.lua`,
  lancé par `aseprite -b`). **Un tag déjà présent n'est jamais redessiné** : tes retouches sont à l'abri,
  le script n'ajoute que de nouvelles frames à la fin, et ne réenregistre pas un fichier complet. Les
  ébauches viennent de `art/tools/items.txt` et `trinkets.txt` (grilles de lettres), `cards.lua` et
  `enemies.lua` (dessin par formes). Pour refaire une ébauche : supprimer son tag (et sa frame).
- `./gradlew exportArt` : réexporte tous les PNG. Aseprite est cherché dans le `PATH` ou dans la
  variable `ASEPRITE` ; s'il est absent, `generateArt` et `exportArt` sont ignorées.
- `./gradlew planche` : `art/planche.png` (toutes les icônes agrandies avec leur nom et leur id, par
  catégorie et rareté ; les manquantes sont marquées « à faire ») et `art/planche-ennemis.png` (toutes
  les frames des adversaires qui ont un sprite).

## Sons et musiques

Le son passe par une seule sortie audio (44,1 kHz stéréo) : il marche dans la fenêtre, en `--terminal` et
en `--no-ui`, sous Linux (PipeWire, PulseAudio), macOS et Windows. Sans carte son, le jeu tourne
simplement en silence. Les tests ne jouent jamais de son.

Les effets et les musiques sont ceux choisis par Louis (WAV et OGG stéréo 44,1 kHz). Seule la musique de
victoire (`music/victory.ogg`) est encore un placeholder généré ; `tools/generate-sounds.sh` recrée les
placeholders (bips et petites boucles chiptune ; Java + ffmpeg avec libvorbis) et **écraserait les vrais
sons** : ne plus le lancer sans sauvegarde.

**Niveaux** — le jeu ramène chaque fichier au même niveau quand il le charge (−18 dB RMS, mesuré sur les
passages forts, sans jamais saturer) : inutile de normaliser les fichiers à la main. Un son très sec dont
la crête est déjà au maximum peut rester quelques dB en dessous. Le `volume=` de `sounds.txt` sert
seulement à doser un son par rapport aux autres.

### Ajouter ou remplacer un son

1. Mettre le fichier dans `src/main/resources/audio/` (`sfx/` pour les effets, `music/` pour les
   musiques).
2. Le déclarer dans **`src/main/resources/audio/sounds.txt`**, la table nom → fichier :

   ```
   music.combat = music/combat.ogg ; loop=4.0      point de boucle (secondes)
   music.victory = music/victory.ogg ; loop=no     jouée une seule fois (jingle)
   sfx.card.fire = sfx/card-fire.wav ; volume=0.8  volume propre à ce son (0 à 1)
   sfx.choomah = sfx/choomah-1.wav, sfx/choomah-2.wav   variantes, jouées chacune à son tour
   ```

   Les noms (`music.*`, `sfx.*`) sont ceux que le jeu demande : garder le nom, changer le fichier. Un
   fichier ou une ligne absente donne du silence, jamais une erreur.

| | Effets | Musiques |
|---|---|---|
| Format | **WAV** PCM 16 bits | **OGG Vorbis** (qualité 4 à 6) ; WAV accepté |
| Fréquence | 44,1 kHz (22,05 kHz possible) | 44,1 kHz |
| Canaux | mono conseillé | stéréo |
| Niveau | crête vers **−3 dBFS** | environ **−16 LUFS** intégré, crêtes sous **−1 dBFS** |
| Durée | court ; **pas de silence au début** (sinon le son paraît en retard) | boucle de 30 s à 2 min |

- **Boucles** : la musique reprend au point `loop=` (0 par défaut) quand elle arrive au bout. Pour une
  boucle propre : couper sur un temps fort, sans fondu ni silence à la fin, et faire coïncider la fin
  avec le point de boucle (pas de clic : couper sur un passage à zéro). Une intro qui ne doit pas se
  répéter va avant le point de boucle.
- Les **sons fréquents** (curseur, pioche, gain de Choomies) sont réglés plus bas dans la table
  (`volume=0.35` à `0.4`) : garder ce genre de réglage pour qu'ils restent discrets.
- Changement d'écran : fondu enchaîné de 1,5 s entre deux musiques.
- Licence : n'ajouter que des sons dont on a le droit (les siens, CC0, CC-BY avec crédit dans ce README).

### Composants tiers

Polices et bibliothèques OGG (JOrbis, VorbisSPI, Tritonus-share, sous LGPL, livrées comme jars
séparés) : voir [`licenses/THIRD-PARTY.md`](licenses/THIRD-PARTY.md).

## Construire l'app (jpackage)

- `./gradlew jpackageImage` : l'app autonome dans `build/jpackage/out/Cardistry` (pour tester).
- `./gradlew jpackage` : les installateurs de l'OS courant dans `build/jpackage/out` (macOS `.dmg`,
  Windows `.msi` + `.zip` portable, Linux `.deb` + `.rpm`). Un OS à la fois : pas de compilation croisée.
  `-PinstallerTypes=rpm` pour un seul type ; `-PappVersion=1.2.3` pour la version (1.0.0 par défaut).
- Il faut une JDK **avec ses `jmods`** (ex. Temurin 21) : certaines JDK de distribution Linux ne
  permettent pas de produire le Java embarqué. Windows : WiX Toolset 3 pour le `.msi`. Linux : `rpmbuild`
  pour le `.rpm`, `dpkg-deb` pour le `.deb`.
- **Publier une version** : `git tag -a v1.2.3 -m "Titre de la release"` puis `git push origin v1.2.3`. La
  CI (`.github/workflows/release.yml`) teste, construit les paquets sur les trois OS et crée la GitHub
  Release (pré-version si le titre contient Alpha, Beta ou RC).
- Icône (provisoire) : `src/main/resources/icon/` ; `.ico` et `.icns` sont générés au build.

## Développement

- Tests : `./gradlew test` (Windows : `gradlew.bat test`). La CI GitHub Actions les lance sur Ubuntu,
  macOS et Windows.
- Les tests d'interface écrivent chaque écran testé dans `build/screens/*.txt`, et le rendu de la
  fenêtre (carte, combat, récompense, shop) dans `build/screens/*.png`.
- `./gradlew animationCheck` : ouvre la vraie fenêtre, joue tout seul (carte, combats, journal) à vitesse
  normale et écrit la trace des animations réellement affichées ; `-Penemies=came,vafi` pour choisir
  les adversaires. Il faut un écran : ce n'est pas un test de la CI.
- `./gradlew balanceRun` : un bot joue des parties complètes avec chaque personnage, sans affichage, et
  écrit la durée des combats, les PV autour des boss, le taux de victoire et la fréquence des Légendaires
  proposées ; `-Pseeds=200` pour le nombre de parties, `-Pheroes=louis,sassy` pour choisir les personnages.
- Contexte et conventions du projet : [`CLAUDE.md`](CLAUDE.md) ; game design :
  [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md).
