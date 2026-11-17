# Cardistry — Game Design

> Document vivant. Les éléments marqués **[À DÉCIDER]** sont ouverts : c'est Louis qui les tranche, pas Claude Code.

## Boucle de jeu (existant)
- Une partie = une suite de **combats** ; **un combat = un adversaire** (voir « Adversaires »).
- Chaque combat commence avec une **nouvelle main de 5 cartes** (la main précédente est défaussée). Taille max : 7.
- À chaque tour : jouer une carte **ou** piocher, puis l'adversaire attaque. Utiliser un objet est une **action gratuite** (pas de fin de tour, pas d'attaque adverse), possible avant de jouer ou piocher.
- Quand un adversaire meurt : Choomies, puis choix d'**1 récompense parmi 3** (objet ou trinket), puis retour à la carte. Chaque combat commence par ton tour.
- Cartes : **Sortilège** (dégâts), **Heal** (soin, boosté dans le terrain du même type), **Terrain** (change le terrain, modifie l'attaque adverse).
- Éléments : deux triangles, **Feu > Plante > Eau > Feu** et **Géométrique > Algébrique > Spirituel > Géométrique** ; les deux triangles ne se battent pas entre eux (×1). **GLITCH** (« error: null ») bat tout, lui-même et le neutre compris. « Plante » est le nom affiché de l'élément nature (l'enum du code reste `NATURE`).
- **Neutre** (cartes et adversaires) : ne bat rien, n'est battu que par GLITCH ; les terrains n'ont aucun effet sur l'attaque d'un adversaire neutre.
- Couleurs d'élément : feu rouge, eau bleu, plante vert, neutre gris clair, algébrique cyan, géométrique jaune ambre, spirituel magenta, GLITCH gris.
- **PV toujours entiers** (joueur et adversaires) : chaque changement (dégâts, soins, statuts) est arrondi à l'entier le plus proche au moment où il s'applique ; les multiplicateurs restent décimaux.
- Chaque combat commence sur le **terrain de l'adversaire** (voir « Terrains »).

### Multiplicateurs d'éléments (décidés)
Source de vérité dans le code : `cardistry.model.ElementRules` (testé).

**Sortilège → dégâts infligés**

| Sort \ Adversaire | Feu | Eau | Plante |
|---|---|---|---|
| Feu | ×1 | ×0.5 | ×1.5 |
| Eau | ×1.5 | ×1 | ×0.5 |
| Plante | ×0.5 | ×1.5 | ×1 |

×1.5 si le sort bat l'adversaire, ×0.5 si l'adversaire bat le sort, ×1 sinon. Même règle pour le second triangle et GLITCH (un sort GLITCH fait ×1.5 contre tout, un sort contre un adversaire GLITCH ×0.5).

**Terrain → attaque de l'adversaire** (le terrain modifie ce que l'adversaire *inflige*, pas ce qu'il reçoit)

| Terrain \ Adversaire | Feu | Eau | Plante |
|---|---|---|---|
| Feu | ×1.25 | ×1 | ×0.75 |
| Eau | ×0.75 | ×1.25 | ×1 |
| Plante | ×1 | ×0.75 | ×1.25 |

×1.25 sur un terrain de son élément, ×0.75 si le terrain bat son élément, ×1 sinon.

**Soin → PV rendus selon le terrain** : ×1.5 sur un terrain du même élément, ×0.75 si le terrain bat l'élément du soin, ×1 sinon.

## Adversaires
Registre : `cardistry.model.enemy.EnemyRegistry` (testé). Chaque adversaire a une liste d'attaques et
**annonce la prochaine** (intent, affiché avec les dégâts réels : niveau, FOR, terrain, ta DEF ; « Vise l'alien · »
quand l'attaque vise le familier de Sassy). Ses tirages (Gamble, coup raté, critique, cible) ne sont **pas**
doublés par CLAUDE.md. Chaque adversaire a les 6 stats (voir « Stats ») ; sa DEF et sa RES sont affichées près de
sa barre de PV, sa fiche complète avec **A**.

**Scaling par niveau** : PV ×1 / ×1.4 / ×1.9, dégâts ×1 / ×1.25 / ×1.5, et **+1 FOR et +1 DEF** par niveau
au-delà du premier (les boss ont des PV et des stats fixés pour leur niveau, leurs dégâts suivent le niveau).
Valeurs ci-dessous au niveau 1 ; leurs stats sont dans « Stats des adversaires ».

| Rang | Nom | Élément | PV | Attaques |
|---|---|---|---|---|
| Commun | Caddie énervé | Neutre | 28 | Roue voilée 6 ; tous les 3 tours, Charge de parking 12 (×2, annoncée) |
| Commun | Choomah | Plante | 22 | coup 4 ; tous les 3 tours, Vol à la tire : 5 Choomies, sinon 1 clope, sinon un coup. Attaques, descriptions et journal **criés** (« ooOOOaAAAOaAOOA ») ; les chiffres restent lisibles |
| Commun | Choomah pressé | Plante | 18 | coup 3 ; vole un tour sur deux |
| Commun | Choomah costaud | Plante | 30 | coup 6 ; vole tous les 3 tours |
| Commun | Camé | Feu | 25 | coup 5 ; un tour sur deux, Taxe : te prend 1 clope, ou deux coups de 5 si tu n'en as pas |
| Commun | Paresseux | Plante | 24 | accroché à sa branche, peu de dégâts : Griffe molle 3 ; un tour sur deux, « Badoing, badoing ! » : 50 % d'esquiver ton prochain sort, sinon 2 × 2 |
| Commun | Clarence | Neutre | 1 | « Clarence te regarde. » N'attaque jamais. 3 % des combats communs ; 75 Choomies |
| Commun | Seau de fuite | Eau | 26 | Ça goutte 2, 3, 4, 5 (le seau se remplit) ; au 5e tour Débordement 14, puis retour à zéro |
| Élite | Vafi | Feu | 55 | Correction 8 ; tous les 3 tours, Série d'algèbre : une carte de ta main devient « Exercice 3b » (injouable pour ce combat) ; sous 50 % PV, Pause pétou (+12 PV, n'attaque pas), 2 fois max |
| Élite | Le Peneu | Neutre | 90 | Rebond (passif) : renvoie 20 % des dégâts reçus, brut, sans jamais tuer ; Crevaison 6, +2 à chaque utilisation |
| Élite | T-Rex défoncé | Plante | 65 | Morsure 13 ; bras trop courts : 30 % de rater, et il se soigne de 8 quand il rate (fringale) |
| Élite | Étudiant post-examen | Eau | 60 | Larmes : 6, +3 par carte de moins que 5 dans ta main ; sous 50 % PV, Crise de nerfs : deux attaques par tour |
| Commun | Gourou du Sat | Spirituel | 24 | Coup d'encens 4 ; tous les 3 tours, Regard fixe : t'hypnotise 1 tour (son coup suivant ×2) |
| Commun | NG | Algébrique | 26 | Démonstration 5 ; tous les 3 tours, Contre-exemple : une carte de ta main devient « Exercice 3b » |
| Commun | Hypercube misogyne | Géométrique | 25 | Tunnel topologique 5 ; tous les 3 tours, Célibat forcé (« ouin ouin ») 2 × 4 |
| Commun | Gobelin | Plante | 22 | « 67 » (six seveeen) : 6 ou 7, chaque tour ; animation des deux mains et « SIX SEVEEEN ! » à l'écran |
| Élite | Engeance des cookies | GLITCH | 60 | Défoncée au space cookie. Piège pâtissier 9 ; tous les 3 tours, Green out : t'endort (Fons 1 tour) ; une fois sous 50 % PV, Méditation triptyque : passe à un autre élément (tiré au hasard, ni neutre ni GLITCH) jusqu'à la fin du combat |
| Boss | Nortin (niveau 1) | Neutre | 160 | Cycle de 4 tours : Bitch slap 8 → Esquive (70 % d'esquiver ton prochain sort, sinon Bitch slap) → Vol de trinket (20 % de te voler un trinket, rendu si tu le bats ; sans trinket : Bitch slap) → Hit in the nuts 16 |
| Boss | Zo le StreMon (niveau 2) | Algébrique | 250 | Cycle de 6 tours : Coup de teub 8 → Coup de teub 8 → Un dernier p'tit verre (5 dégâts, +1 Ivresse pour toi) → Coup de teub 8 → P'tite clope (se soigne de 10) → Capoeira 12 |
| Boss | Boss du niveau 3 (**nom à venir**) | Plante | 280 | Coup 10 ; toutes les 4 attaques, Grosse attaque 22 |

Statuts infligés (tirages de l'adversaire, pas doublés par CLAUDE.md) :

| Adversaire | Attaque | Statut |
|---|---|---|
| Camé | chaque attaque qui porte (pas la Taxe qui prend une clope) | 30 % de Brûlure |
| Choomah (et variantes) | Vol à la tire | Intox |
| Vafi | Série d'algèbre | Fons 1 tour (on s'endort en cours) |
| Paresseux | « Badoing, badoing ! » | 25 % de Fons 1 tour |
| Gourou du Sat | Regard fixe | Hypnotisé 1 tour (son coup suivant ×2) |
| Engeance des cookies | Green out | Fons 1 tour |

Pour l'instant, tous les adversaires peuvent être rencontrés à chaque niveau. **Prévu** : un système de niveaux
avec un type d'adversaires par niveau.

Règles communes :
- L'intent est choisi au début du combat et après chaque attaque. Un adversaire en Fons annonce « Endormi » et garde son attaque pour son réveil. Un tour sauté (Pétou ancestral) garde
  l'intent et n'avance pas les compteurs (Seau, Crevaison).
- **Moscow Mule** annule toute l'attaque (tous ses coups), mais pas les vols ni la Taxe (qui ne sont pas
  des attaques ; la Taxe sans clope, elle, frappe et s'annule).
- Esquive : seul le prochain **sort** est esquivé (un Shot de Jäger actif est consommé quand même).
- Cartes mortes (« NO CARDS », « Exercice 3b ») : injouables jusqu'à la fin du combat, jamais remplacées.
- Choomah : variante tirée au hasard ; deux Choomah ne se suivent pas, ni deux fois le même commun.
- **Vol de trinket** (Nortin) : 20 % (tirage de l'adversaire, pas doublé par CLAUDE.md), sinon rien ; un
    trinket équipé tiré au hasard, secrets compris ; un par vol réussi, il peut en voler plusieurs dans le combat ;
    sans trinket, Bitch slap à la place. Moscow Mule et l'immunité de Fons l'empêchent. Le Diplôme EPFL se vole comme les autres : il libère sa propre place, rien à jeter. Tous les trinkets volés sont rendus à la mort de
    l'adversaire, à la fin de la barre dans l'ordre du vol, avant ses effets (Monde Snack, Pétou ancestral…) et la
    récompense (un trinket rendu n'y est donc jamais proposé). Sur une défaite, ils restent perdus.
- **Un dernier p'tit verre** (Zo le StreMon) : un coup de 5, puis +1 Ivresse pour toi (palier, bonus de dégâts et
    Gueule de bois au seuil, comme une boisson). Moscow Mule et l'immunité de Fons annulent le coup, pas l'Ivresse.
    Gueule de bois éventuelle : au premier combat du niveau 3.

## Structure d'une run
- **3 niveaux** (sans nom) de **8 étages** chacun, boss compris : étages 1-6 au choix, 7 = Sieste, 8 = Boss.
  Un boss différent à la fin de chaque niveau ; battre celui du niveau 3 gagne la partie.
- Carte à **embranchements** façon Slay the Spire, générée par un **sous-flux nommé de la seed**
  (`Rng.fork("carte-<niveau>")`) : même seed = même carte, quels que soient les autres tirages. Les
  adversaires sont tirés dans un autre sous-flux (`rencontres-<niveau>`). Code : `run.map.MapGenerator`.
- Génération : 5 colonnes, 4 chemins qui montent d'au plus une colonne par étage ; ils se rejoignent
  mais ne se croisent jamais en X ; au moins deux départs.
- Types de nœuds : Combat `C`, Élite `E`, Shop `$`, Événement `?`, Sieste `Z`, Boss `B`.

| Étage | Contenu |
|---|---|
| 1 | Combat uniquement |
| 2-3 | Combat 60 %, Événement 25 %, Shop 15 % (pas d'élite ni de sieste) |
| 4-6 | Combat 45 %, Événement 22 %, Élite 15 %, Shop 10 %, Sieste 8 % (pas de sieste à l'étage 6) |
| 7 | Sieste partout |
| 8 | Boss |

- Contraintes : jamais deux Shop, deux Élites ou deux Siestes à la suite sur un chemin ; au moins un
  Shop par carte ; au moins un chemin sans élite.
- Écran de carte (fenêtre) : étage 1 à gauche, boss à droite ; icônes pixel art, chemins en
  pointillés courbes et nœuds décalés tirés du sous-flux `Rng.fork("carte-dessin-<niveau>")` (le
  dessin ne consomme aucun tirage de la carte) ; chemin parcouru en or, nœuds devenus inatteignables
  estompés. ←/→ ↑/↓ (ou 1-n) pour choisir, Entrée pour y aller, I inventaire.
- Terminal (`--terminal`, `--no-images`) : carte en lettres, boss en haut, étage 1 en bas. Mode
  texte : la carte en ASCII, les choix numérotés.
- Nœud `?` : un **événement** (voir « Événements »).
- **Sieste** (étage 7, parfois aux étages 4-5) : un choix parmi « Dormir » (+30 % des PV max ; c'est un
  **soin** : efficacité des soins et Ross s'appliquent, sans plafond avec Ross), « Rouler tranquille » (+3 clopes, sans dépasser le paquet), « Faire le tri » (retirer une
  carte du deck, avec le deck construit). Elle n'efface **pas** la Gueule de bois (les clopes sont là pour ça).
- **Récompenses** : 1 parmi 3 après chaque combat ; élite : toutes au moins Peu commun ; boss : toutes au
  moins Rare (les secrets débloqués restent possibles).
- **Après chaque boss** (sauf le dernier) : la moitié des PV manquants est rendue — une **récupération**,
  pas un soin (aucun bonus de soin, pas de Ross) —, puis **+25 PV max** (et +25 PV actuels) jusqu'à la fin de
  la partie, puis l'écran **Progression** : **2 points de stats** à placer un par un (FOR, INT, ESP, DEF, RES
  ou CHA), puis le niveau suivant commence.
- Mort = fin de la run. Méta-progression : seulement les secrets débloqués (voir `docs/SECRETS.md`).

### Deck construit (lot 1 bis)
- **Deck de départ, 12 cartes** : 6 sorts communs (Boule de feu, Jet d'eau, Fouet de lianes, Pet de Mamouz,
  Carte basique pas ouf, Lancer de manette), 3 Potions, 3 terrains (Marécage, Forêt, Volcan). Le terrain de
  départ (Dojo) est en jeu, pas dans le deck.
- Au début de chaque combat, tout le deck est mélangé en une **pioche** ; la main de 5 en est tirée.
  Les cartes jouées, remplacées (George) ou transformées (NO CARDS, Exercice 3b) vont à la **défausse** ;
  les cartes mortes, elles, disparaissent à la fin du combat. Pioche vide : la défausse est remélangée.
  Pioche et défausse vides : piocher est refusé (« Plus aucune carte à piocher »).
- **Récompense de carte** après chaque combat (sauf le boss final), **après** la récompense d'objet :
  3 cartes de noms différents tirées du registre (poids des trinkets compris), on en ajoute 1 au deck ou
  on passe (en plus du 1 parmi 3 d'objets).
- **Retirer une carte** : Sieste « Faire le tri » (gratuit) ou shop (payant) ; jamais sous **5 cartes**.
- Grinder : +1 Petite taffe dans le deck à l'obtention, Petite taffe ×2 dans les récompenses de cartes (et
  pour George). Paquet de cartes : 3 cartes de la pioche, les 2 autres à la défausse. George : carte du
  registre (elle ne reste pas dans le deck).
- Écran de combat : « Ta main 5/7 · Pioche 7 · Défausse 0 » ; inventaire (I) ; le deck complet avec **D**,
  n'importe où pendant une partie.
- Une main peut dépasser la taille max (taille qui baisse en cours de combat) : on garde ses cartes, toutes
  jouables, sans pouvoir piocher.
- Les **cartes mortes** (« NO CARDS », « Exercice 3b ») **ne comptent pas** dans la taille de main : on peut
  toujours piocher à côté (« Ta main X/7 » ne compte que les cartes vivantes). Sans cette règle, une main de 7
  « Exercice 3b » bloquait le combat.

## Personnages
Au début de chaque partie (après l'écran titre), on choisit un personnage. Sa capacité est permanente,
comme un trinket qu'on ne peut ni perdre ni remplacer. Code : `hero.HeroRegistry` (tous les chiffres).

| Personnage | PV | Capacité | Effet |
|---|---|---|---|
| **Louis** | 100 | MAN | +50 % de dégâts, tout le temps (tous éléments, additionné aux autres pourcentages, comme CMS). Aucun malus. |
| **Dave** | 100 | Full skill Mr. Le Juge | Un sort du même élément que le terrain (ex. Boule de feu à l'EPFL) soigne des PV enlevés à l'adversaire. |
| **Sassy** | 130 | wadayatalkinabit | À chaque début de tour, son alien inflige 4 dégâts bruts à l'adversaire, en plus de l'action du joueur. 10 % de chance que l'alien rate son tour et fume un joint à la place (aucun effet, pour le lore). L'alien est un **familier** de 20 PV (voir ci-dessous). |
| **Frederic BLC** | 100 | Aucune | Aucune compétence, mais des stats « parfaitement moyennes » : 2 partout (12 points au lieu de 8). |

**Stats de base** (8 points pour Louis, 9 pour Dave et Sassy, 12 pour Frederic BLC) :

| Personnage | FOR | INT | ESP | DEF | RES | CHA | Logique |
|---|---|---|---|---|---|---|---|
| Louis | 3 | 2 | 0 | 1 | 1 | 1 | MAN : profil offensif |
| Dave | 2 | 3 | 1 | 1 | 1 | 1 | INT pour les statuts et les sorts algébriques/géométriques |
| Sassy | 2 | 0 | 2 | 2 | 2 | 1 | Déjà 130 PV et l'alien : profil endurant |
| Frederic BLC | 2 | 2 | 2 | 2 | 2 | 2 | Aucune capacité : le perso des builds de stats |

**L'alien de Sassy (familier)** — code : `model.Familiar` (générique : un trinket ou un objet pourra plus tard
en invoquer un), `HeroRegistry.ALIEN` :
- **20 PV**, barre de vie au-dessus de son sprite, dans un emplacement dédié à droite du personnage.
- Quand l'adversaire annonce une attaque qui frappe, **25 %** de chances qu'elle **vise l'alien** à la place du
  joueur (tirage seedé de l'adversaire, pas doublé par CLAUDE.md) ; l'intent l'annonce (« Vise l'alien · … »,
  « visé ! » au-dessus de sa barre).
- Sur l'alien : la **DEF du joueur ne le protège pas** (FOR, terrain, hypnose et critique de l'adversaire
  comptent) ; **aucun statut** (les statuts « après un coup » sont perdus). Moscow Mule annule l'attaque comme
  d'habitude.
- À **0 PV**, il est **assommé** jusqu'à la fin du combat : il n'attaque plus, n'est plus visé, sprite « KO » ;
  les coups restants de l'attaque se perdent. Il revient au combat suivant avec tous ses PV.
- Animations : idle, attaque quand il frappe, « fume un joint » quand il rate, KO (placeholders dans
  `art/player/alien.aseprite`).

Choix d'implémentation :
- Louis : MAN = +50 % sur les stats de dégâts de tous les éléments (bonus permanent du personnage).
- Dave : le soin vaut les PV **réellement** enlevés (pas les dégâts en trop sur un adversaire achevé) ; rien
  si le sort est esquivé. *Provisoire : c'est un soin comme les autres (efficacité des soins, Ross, plafond
  des PV max).*
- Sassy : l'alien attaque aussi pendant un tour sauté et peut achever l'adversaire. Dégâts bruts (aucun
  multiplicateur, le Rebond du Peneu s'applique). Son raté n'est pas un tirage de chance : CLAUDE.md ne le
  double pas.

## Stats (plan V3)
Six stats de personnage, partagées par le joueur et les adversaires, de petites valeurs (0 à 10) pour calculer de
tête. Code : `core.stats.StatType` (`STRENGTH`, `INTELLIGENCE`, `MIND`, `DEFENSE`, `RESISTANCE`, `LUCK`), toutes
les formules dans `core.CombatFormula` (testée).

| Stat | Abrév. | Effet par point |
|---|---|---|
| Force | FOR | +1 dégât sur les sorts feu, eau, plante, neutre ; +1 sur chaque coup d'un adversaire |
| Intelligence | INT | +1 dégât sur les sorts algébriques et géométriques ; +3 % de chance d'infliger un statut |
| Esprit | ESP | +1 dégât sur les sorts spirituels ; +1 PV sur chaque soin à PV fixes |
| Défense | DEF | −1 dégât subi par coup (remplace l'ancienne réduction de dégâts et les « dégâts subis en plus ») |
| Résistance | RES | −5 % de chance de subir un statut ; à 5 ou plus, statuts subis −1 tour (minimum 1) |
| Chance | CHA | +3 % de coup critique (×1,5) |

**Formules** :
- **Dégâts d'un sort** : `(base + stat offensive) × multiplicateurs × critique − DEF de la cible`, **minimum 1**
  si la carte a des dégâts (un sort à 0 reste à 0). Stat offensive selon l'élément (FOR / INT / ESP) ; sort
  GLITCH : la meilleure des trois. Multiplicateurs : élément, terrain, statuts (Hypnotisé), trinkets en %,
  prochain sort ×N. Rejeu de l'Espace : le ×0,5 est un multiplicateur (avant la DEF).
  Exemple : Boule de feu (10), FOR 3, contre plante (×1,5) avec DEF 2 : (10 + 3) × 1,5 − 2 = 17,5 → 18.
- **Coup d'un adversaire** : `(dégâts déclarés × niveau + sa FOR) × terrain × hypnose × critique − ta DEF`,
  minimum 1, **coup par coup** (2 × 4 : deux fois la FOR, deux fois la DEF). Une DEF n'annule donc plus jamais
  un coup. Une DEF négative (malus de terrain) ajoute des dégâts.
- **Critique** : 3 % par CHA, ×1,5, sur les sorts multipliés seulement (pas Professeur d'or) ; tirage en faveur
  du joueur, doublé par CLAUDE.md. Les adversaires font aussi des critiques sur leurs coups (leur CHA, pas
  doublé). Journal : « Coup critique ! ».
- **Dégâts élémentaires des objets** (Gaz du bat Inf) : multiplicateurs et DEF, sans stat offensive ni critique.
  **Dégâts bruts inchangés** : statuts, Rebond, alien, Plan du campus, sorts non multipliés : ni stats, ni DEF.
- **Soins** : `(base + ESP) × multiplicateurs de soin`, puis plafond (sauf Ross), pour les **cartes et objets
  à PV fixes** (`ctx.itemHeal`). Pas d'ESP sur les clopes, la Sieste, les soins en % (Fons), le soin de Dave,
  les trinkets ni la récupération après boss. L'ESP d'un adversaire s'ajoute à ses propres soins (Pause pétou,
  fringale).
- **Chance de statut** : `chance de la carte + 3 % × INT du lanceur − 5 % × RES de la cible`, entre 0 et 100 %,
  **y compris pour les statuts « à coup sûr »** (Pet de Mamouz contre RES 4 : 80 %). L'INT du joueur compte pour
  tous les statuts qu'il pose (cartes, trinkets, objets) ; un statut qu'il se pose lui-même (Petite taffe, Taffe
  de foufou) ne passe ni par l'INT ni par la RES. Statuts d'un adversaire : son INT contre ta RES.
- Les tirages doublés par CLAUDE.md restent ceux en faveur du joueur (critiques compris).

**Stats temporaires** : `ctx.addCombatStat` donne ou retire des points pour un combat, au joueur ou à
l'adversaire (Jet d'eau, Big Lez, Stingin' Roger, Sparadrap, Soupe de mamie, objets du plan V3).

**Progression** : après chaque boss (sauf le dernier), +25 PV max pour la partie et **+2 points** à placer
(écran Progression, un point à la fois ; mode texte : choix numéroté). `StoryContext` pourra donner ou retirer
1 point (« Tu révises toute la nuit : +1 INT, −1 RES ») quand les vrais événements arriveront.

### Stats des adversaires
Valeurs au niveau 1 (FOR, INT, ESP, DEF, RES, CHA), code : `EnemyRegistry` (table `STATS`). Hors boss, +1 FOR et
+1 DEF par niveau au-delà du premier.

| Adversaire | FOR | INT | ESP | DEF | RES | CHA | Logique |
|---|---|---|---|---|---|---|---|
| Caddie énervé | 1 | 0 | 0 | 2 | 2 | 0 | Carcasse en métal : DEF |
| Choomah | 0 | 0 | 0 | 0 | 1 | 3 | Voleur : CHA |
| Choomah pressé | 0 | 0 | 0 | 0 | 0 | 4 | Rapide et chanceux, fragile |
| Choomah costaud | 2 | 0 | 0 | 1 | 1 | 1 | Plus de FOR |
| Camé | 1 | 2 | 0 | 0 | 3 | 1 | INT : Brûlure plus fréquente ; RES : habitué aux substances |
| Paresseux | 0 | 1 | 1 | 1 | 2 | 2 | Esquiveur |
| Clarence | 0 | 0 | 0 | 0 | 0 | 0 | Rien. Évidemment. |
| Seau de fuite | 0 | 0 | 0 | 1 | 4 | 0 | Un seau ne s'intoxique pas |
| Gourou du Sat | 0 | 1 | 3 | 0 | 2 | 0 | ESP |
| NG | 0 | 4 | 0 | 1 | 1 | 0 | INT |
| Hypercube misogyne | 1 | 2 | 0 | 2 | 1 | 1 | Forme solide : DEF |
| Gobelin | 1 | 0 | 0 | 0 | 1 | 3 | « 67 » : CHA |
| Vafi (élite) | 2 | 4 | 2 | 1 | 3 | 1 | Prof d'algèbre : INT ; ESP pour la Pause pétou |
| Le Peneu (élite) | 1 | 0 | 0 | 4 | 5 | 0 | Caoutchouc : les petits sorts rebondissent |
| T-Rex défoncé (élite) | 4 | 0 | 1 | 2 | 0 | 0 | FOR énorme, RES nulle : facile à statuer |
| Étudiant post-examen (élite) | 1 | 3 | 0 | 0 | 0 | 2 | Épuisé : aucune défense |
| Engeance des cookies (élite) | 2 | 3 | 2 | 1 | 3 | 2 | Équilibrée et imprévisible |
| Nortin | 3 | 1 | 1 | 2 | 3 | 1 | Test des stats |
| Zo le StreMon | 4 | 4 | 2 | 3 | 4 | 2 | Exige un build |
| Boss du niveau 3 | 5 | 3 | 3 | 4 | 5 | 3 | Fin de run |

Les boss ont une RES élevée pour qu'on ne puisse pas les enchaîner en Fons ou en Intox ; leurs stats seront à
revoir avec leurs vrais noms et attaques.

## Objets et trinkets
| | Objet | Trinket |
|---|---|---|
| Nature | Consommable, usage unique | Passif, actif toute la run |
| Slots | 3 | 5 (+1 avec Diplôme EPFL) |
| Inventaire plein | le joueur remplace un objet existant ou refuse la récompense | idem |
| Obtention (en attendant run et shop) | récompense après chaque adversaire vaincu : 1 au choix parmi 3, tirés selon les poids de rareté, sans trinket déjà possédé | idem |
| Exemple | Maté : +10 PV | Redistribution des briquets : +25 % dégâts feu |

### Effets sur les stats
Stats modifiables : PV max (plafond des soins), taille de main, cartes piochées, Choomies gagnés, multiplicateur par élément, efficacité des soins, et les 6 stats de personnage (voir « Stats »).
Ordre de calcul : base → bonus plats → pourcentages. Les pourcentages **s'additionnent** : deux trinkets +25 % feu = +50 % (×1.5), pas ×1.5625. Formule : `(base + plats) × (1 + somme des %)`, plancher à 0.

### Rareté
La couleur de rareté est la même partout : texte en terminal ; dans la fenêtre, cadre de l'icône en combat, dans les récompenses et au shop. La couleur d'élément (voir « Boucle de jeu ») est réservée aux cadres de cartes. Les cartes ont aussi une rareté : une lettre à sa couleur dans le coin haut droit de la carte (**C** Commun, **P** Peu commun, **R** Rare, **L** Légendaire, **S** Secret), ★ à côté si la carte a un pouvoir.

| Rareté | Couleur | Poids drop (indicatif) | Prix shop (indicatif) |
|---|---|---|---|
| Commun | blanc | 60 | 50 |
| Peu commun | vert | 25 | 80 |
| Rare | bleu | 12 | 130 |
| Légendaire | jaune | 1 | 200 |
| Secret | magenta | seulement une fois débloqué (poids 1), voir `docs/SECRETS.md` | — |

Les **cartes proposées** (récompenses de carte, shop, George) sont tirées selon leur rareté, avec leurs propres
poids : Commun 40, Peu commun 30, Rare 20, Légendaire 3 (× les poids des trinkets, Grinder…) ; elles étaient tirées uniformément, ce qui proposait une Légendaire
plusieurs fois par partie. Mesure (`./gradlew balanceRun`, 800 parties du bot) : 3,3 → 0,23 Légendaire proposée
par partie. La pioche des parties sans deck (tests) reste uniforme.

### Synergies
- Chaque objet/trinket porte des **tags** (élément, archétype : Brûlure, Soin, Terrain, Pioche…).
- Synergies de set : 2/3/4 trinkets du même tag → bonus croissant.
- Synergies directes : un trinket qui modifie l'effet d'un autre (ex. « tes trinkets Feu affectent aussi les Heals »).
- Tags utilisés par le premier jet : **EPFL**, **Soirée**, **Boisson**, **Méta**, éléments (Feu, Eau, Plante), archétypes (Soin, Pioche, Terrain, Défense, Force).

#### Bonus de set (décidés, étape 6)
Seuls les **trinkets équipés** comptent (pas les objets). Un trinket compte pour **chacun de ses tags**. Les paliers se **cumulent** (à 4, on a aussi ceux de 2 et 3). Recalculés à chaque changement d'inventaire ; notification quand un palier s'active ; détail dans l'inventaire (I). Noms provisoires.

| Set | 2 | 3 | 4 |
|---|---|---|---|
| EPFL « Cursus » | +10 % dégâts élémentaires | +1 taille de main | +25 PV max et +15 % dégâts élémentaires |
| Soirée « Before → Afterparty » | +1 clope au début de chaque combat (respecte le max du paquet) | Seuil de Gueule de bois +1 | Chaque boisson soigne aussi 5 PV |
| Soin | +15 % soins | — | — |
| Pioche | +1 carte dans la main de départ | — | — |

**Diplôme EPFL** (Légendaire, EPFL) : +1 emplacement de trinket, effet des trinkets EPFL ×2, bonus de set EPFL ×2 ; il compte lui-même dans le set EPFL. Effet boule de neige **voulu**.
- **Tout l'effet des trinkets EPFL est doublé, malus compris** (CMS : +100 % / adversaire +50 % ; Photocopieuse : 3 cartes ; Latte macchiato vanille : +2 cartes ; M1 et Plan du campus : 10). Le Diplôme ne se double pas lui-même (+1 emplacement seulement).
- Si on perd le Diplôme avec 6 trinkets équipés, le joueur doit **immédiatement choisir un trinket à jeter** (écran de choix, pas de tirage au hasard) — sinon c'est exploitable.
- Un trinket qui apporte ses propres emplacements (le Diplôme aujourd'hui) **s'équipe même barre pleine, sans remplacement** : il se met à la fin de la barre (récompense, shop, trinket rendu par Nortin).

### Ivresse
- Chaque **boisson** (objet tagué Boisson) donne **+1 Ivresse**. L'Ivresse retombe à 0 après chaque combat.
- **Paliers** (`core.DrunkTier`) : l'Ivresse donne un bonus de dégâts élémentaires (comme CMS, additionné aux autres pourcentages) tant qu'elle dure :

| Ivresse | Palier | Effet |
|---|---|---|
| 1 | Pompette | +10 % dégâts |
| 2 jusqu'au seuil | Bourré | +25 % dégâts |
| seuil (3) et plus | Cuite | +50 % dégâts, et **Gueule de bois** au combat suivant (−1 taille de main ; −2 pour Louis) |
- Modifiée par : Foie d'ingénieur (Cuite et Gueule de bois à 5 : Bourré de 2 à 4), Ptit Sat ? (+10 % dégâts par point d'Ivresse, **en plus** du palier), la clope (−1 Ivresse).
- Implémentation (étape 3) : la Gueule de bois s'active au début du combat suivant (quels que soient les nœuds entre les deux : Sieste, shop…) et dure tout ce combat ; une à chaque fois qu'on franchit le seuil vers le haut (l'Ivresse continue de monter au-delà, sans nouvelle Gueule de bois ni bonus supplémentaire) ; aucune pendant le boss final (il n'y a pas de suivant).
- Avec la carte (environ 18 combats par run), la Gueule de bois, Foie d'ingénieur et Ptit Sat ? deviennent réellement utiles.

### Clopes
- Ressource de run : **5 clopes** au début de chaque partie, conservées entre les combats, **20 maximum** (un paquet ; stat `CIGARETTE_MAX`).
- **Fumer (F)** : action gratuite. **+5 PV** (un soin : efficacité des soins, Ross, plafond des PV max) et **−1 Ivresse** (le palier redescend donc : on échange des dégâts contre des PV). On peut fumer à jeun.
- **Rouler (G)** : remplace l'action du tour (comme piocher) : +1 clope, puis l'adversaire attaque. Impossible si le paquet est plein.
- La **Gueule de bois a une intensité** (1 par défaut = −1 taille de main). Chaque clope retire 1 d'intensité ; à 0, elle disparaît. Si elle est seulement « à venir », la clope la réduit à l'avance. Si elle est à la fois active et à venir, la clope réduit d'abord celle du combat en cours. Exception : tant que l'Ivresse reste au seuil ou au-dessus après la clope, la Gueule de bois à venir n'est pas réduite (on ne garde pas la Cuite sans la payer) ; repasser le seuil vers le haut en redonne une.
- Affichage : un petit paquet ASCII en bas de l'écran, avec le nombre de clopes et les touches.

### Contenu — premier jet
Implémentés à ce jour — plan V3 : 6 trinkets de stats (Donut Musclé, Carte graphique, Bâton d'encens, Veste de ski, Carnet de vaccination, Benediction QCM) et 3 objets (Protéine en poudre, Documentation LATEX, Thé odorant). Étape 7 : Paquet de cartes (pioche au choix), défausse obligatoire quand on perd le Diplôme EPFL avec 6 trinkets. Étape 6 : bonus de set, Diplôme EPFL. Étape 5 : clopes, Googz, Honoré, Ross. Étape 4 : Caïpirinha, Petit pétou (objets), Shot de Jäger, Moscow Mule, Raclette, Space cake, Pétou ancestral. Étape 3 : Spritz, Pichet vide, Foie d'ingénieur, Ptit Sat ?. Étape 2 (tout ce qui est **a**) : 14 trinkets (Redistribution des briquets, Pichet de Sat, Plante du Rolex, Polycopié d'Analyse I, Latte macchiato vanille, M1, Pax, Monde Snack, Grinder, Plan du campus, Photocopieuse, Compréhension algébrique, CMS, George) et 3 objets (Maté, Cafffeeeeinnnn, Gaz du bat Inf). Le reste suit l'ordre de « Nouvelles mécaniques ».

Précisions d'implémentation :
- Les soins des objets et trinkets passent par l'efficacité des soins ; Grinder ajoute +50 % aux cartes de soin feu en plus.
- Polycopié d'Analyse I (+2 DEF) ne réduit que les attaques adverses (pas les dégâts qu'on s'inflige soi-même) ; un coup fait toujours au moins 1.
- Compréhension algébrique ne compte que les dégâts des sorts joués (pas ceux des objets ni de Plan du campus).
- Plan du campus et les dégâts « bruts » des objets ignorent éléments et stats ; Gaz du bat Inf est traité comme des dégâts élémentaires de l'élément tiré (multiplicateurs et DEF, ni stat offensive ni critique) et ne tire jamais GLITCH.
- George ne se déclenche que sur les pioches (pas sur la main distribuée en début de combat). La carte remplacée part à la défausse ; la nouvelle vient du registre et ne rejoint pas le deck.
- Pichet vide : les 5 PV perdus ignorent Polycopié d'Analyse I et peuvent tuer le joueur ; si le joueur et l'adversaire meurent en même temps, c'est une défaite.
- Ptit Sat ? : même portée que CMS (dégâts élémentaires), recalculé à chaque changement d'Ivresse.

Colonne **Méca.** : **a** = faisable avec les mécaniques existantes (stats, hooks sur les événements actuels, dégâts / soins / pioche / terrain) ; **b** = demande une nouvelle mécanique (voir « Nouvelles mécaniques »).

#### Trinkets
| Rareté | Nom | Effet | Tags | Méca. |
|---|---|---|---|---|
| Commun | Redistribution des briquets | +25 % dégâts feu | Feu, Soirée | a |
| Commun | Pichet de Sat | +25 % dégâts eau | Eau, EPFL | a |
| Commun | Plante du Rolex | +25 % dégâts plante | Plante, EPFL | a |
| Commun | Polycopié d'Analyse I | +2 DEF | Défense, EPFL | a |
| Commun | Honoré | Jauge Honoré : +1 par tour joué ; à 5, une clope gratuite et retour à 0. La jauge persiste entre les combats. Paquet plein : la jauge reste à 5 (« clope prête ») et la clope arrive dès qu'une place se libère (validé) | Soirée | b (M9) |
| Commun | Latte macchiato vanille | +1 carte au premier tour de chaque combat | Pioche, EPFL | a (+ notion de combat, M8) |
| Commun | Donut Musclé | +2 FOR | Force | a |
| Commun | Carte graphique | +2 INT (×2 avec le Diplôme EPFL) | EPFL | a |
| Commun | Bâton d'encens | +2 ESP | Soirée | a |
| Commun | Veste de ski | +2 DEF | Défense | a |
| Commun | Carnet de vaccination | +2 RES | Défense | a |
| Commun | Benediction QCM | +2 CHA (×2 avec le Diplôme EPFL) | EPFL | a |
| Peu commun | Googz | Quand tu fumes, 20 % de chances que la clope se transforme en Petit pétou : effet de l'objet Petit pétou (+15 PV soin feu, −1 Ivresse) **à la place** des +5 PV ; réduit quand même la Gueule de bois | Soirée | b (M9) |
| Peu commun | M1 | Changer de terrain rend 5 PV | Terrain, EPFL | a |
| Peu commun | Pax | +1 taille de main | Pioche, Soirée | a |
| Peu commun | Monde Snack | +10 PV à chaque adversaire vaincu | Soin, Soirée | a |
| Peu commun | Grinder | Soins de type feu +50 % ; +1 Petite taffe à l'obtention, Petite taffe deux fois plus fréquente dans la pioche | Soin, Soirée | a |
| Peu commun | Plan du campus | Jouer un terrain inflige aussi 5 dégâts | Terrain, EPFL | a |
| Rare | Photocopieuse | Piocher donne 2 cartes | Pioche, EPFL | a |
| Rare | Compréhension algébrique | 20 % des dégâts de sorts te soignent | Soin, Plante | a |
| Rare | Foie d'ingénieur | Cuite et Gueule de bois à 5 Ivresse au lieu de 3 | Soirée | b (M1) |
| Rare | CMS | +50 % dégâts, mais l'adversaire aussi +25 % | EPFL | a |
| Légendaire | Ross | Tous les soins ×2 (multiplicateur final, après tous les autres bonus de soin) ; les soins ne sont plus plafonnés par les PV max | Soin | b (M9) |
| Légendaire | Paquet de cartes | Chaque carte piochée pendant un combat devient un choix : 3 cartes tirées du pool, on en garde 1, les 2 autres sont défaussées | Méta | b (M3) |
| Légendaire | Diplôme EPFL | +1 emplacement de trinket ; effet des trinkets EPFL doublé (effet boule de neige **voulu**, voir plus bas) | EPFL | b (M4) |
| Légendaire | Ptit Sat ? | +10 % dégâts par point d'Ivresse, en plus du bonus d'Ivresse | Soirée | b (M1) |
| Légendaire | Pétou ancestral | À chaque combat gagné : +8 PV (soin feu : terrain, Grinder, ×2 avec Ross). Au début de chaque combat, l'adversaire saute son premier tour (« [Adversaire] passe son tour : tu as fait tourner le j. ») | Soirée, Feu | b (M2) |
| Légendaire | George | *« À l'origine de la redistribution des biens communs. »* À chaque carte piochée, une autre carte de ta main (tirée au hasard) est remplacée par une carte aléatoire du pool | Soirée | a |
| Secret | 3 trinkets | voir `docs/SECRETS.md` (spoilers) | Méta | b (M5, M6, M7) |

#### Objets (consommables)
| Rareté | Nom | Effet | Tags | Méca. |
|---|---|---|---|---|
| Commun | Maté | +10 PV (+ESP, comme tous les objets à PV fixes) | Soin | a |
| Commun | Pichet vide | 15 dégâts à l'adversaire, tu perds 5 PV, +1 Ivresse | Soirée, Boisson | a + b (M1) |
| Commun | Caïpirinha | +20 PV, +1 Ivresse | Soirée, Boisson | a + b (M1) |
| Commun | Petit pétou | +15 PV, −1 Ivresse (minimum 0). Compte comme un soin feu (boosté par Grinder) | Soirée | a + b (M1) |
| Commun | Cafffeeeeinnnn | Pioche 2 cartes | EPFL | a |
| Commun | Gaz du bat Inf | 20 dégâts d'un élément aléatoire (jamais GLITCH) | EPFL | a |
| Commun | Protéine en poudre | +3 FOR pour le combat | Force | a |
| Commun | Documentation LATEX | +3 INT pour le combat | EPFL | a |
| Commun | Thé odorant | +3 RES pour le combat et retire un statut négatif (le premier de Intox, Brûlure, Bondage, Fons) | Défense | a |
| Peu commun | Spritz | +30 PV, +1 Ivresse | Soirée, Boisson | a + b (M1) |
| Peu commun | Shot de Jäger | Prochain **sort** ×2, +1 Ivresse. Actif jusqu'à ce qu'un sort soit joué, perdu à la fin du combat | Soirée, Boisson | b (M1, M2) |
| Peu commun | Moscow Mule | Annule la prochaine attaque adverse, +1 Ivresse. Perdu à la fin du combat s'il n'a pas servi | Soirée, Boisson | b (M1, M2) |
| Peu commun | Raclette | +50 PV, puis tu sautes ton prochain tour (l'adversaire attaque normalement) | EPFL | b (M2) |
| Rare | Space cake | Au bout de 3 tours : +40 PV et +50 % dégâts pendant 2 tours. Si le combat se termine avant le déclenchement, le compte à rebours continue au combat suivant | Soirée | b (M2) |

La carte Soin « Petit pétou » est devenue « Petite taffe » (plan V2) ; l'objet Petit pétou (et Googz) ne change pas. Objets et cartes homonymes restent toujours distingués à l'écran et ont des identifiants distincts (`carte:` / `objet:`).

#### Choix assumés
- **Diplôme EPFL** fait boule de neige avec les trinkets EPFL et double aussi le bonus de set EPFL : c'est voulu (synergies façon *The Binding of Isaac*).
- Les soins restent plafonnés aux PV max, même si ça gaspille une partie des gros soins — **sauf avec Ross**.
- Ross : ×2 appliqué **après** tous les autres bonus de soin (multiplicateur final, pas +100 % additif). Les PV peuvent dépasser le max (affichés en doré) ; les PV max ne bougent pas. Si on perd Ross, les PV en trop sont gardés mais les soins suivants sont de nouveau plafonnés.

#### Nouvelles mécaniques nécessaires
- **M1 — Ivresse** : compteur du joueur, remis à 0 à chaque fin de combat, seuil de Gueule de bois modifiable.
- **M2 — Effets temporaires** : système générique (comme les malédictions) : délai avant déclenchement, durée en tours et/ou en déclenchements (charges), perdu ou gardé à la fin du combat, modificateurs de stats pendant l'effet, effets possibles : multiplier le prochain sort, annuler la prochaine attaque, faire sauter un tour. Affichés à l'écran avec leur durée restante.
  - Tour sauté de l'adversaire (Pétou ancestral) : vérifié avant les annulations d'attaque, donc un Moscow Mule reste pour l'attaque suivante. L'effet est posé au début du combat : équiper Pétou ancestral en cours de combat ne fait effet qu'au combat suivant.
  - Choix provisoires : deux Shot de Jäger ne se cumulent pas sur le même sort (un par sort) ; Raclette est perdue si le combat se termine avant le tour sauté ; Space cake utilisé au tour T se déclenche au début du tour T+3, et ses +50 % (dégâts élémentaires, comme CMS) valent pour les tours T+3 et T+4 ; un tour sauté compte comme un tour ; Petit pétou (objet) se comporte comme une carte de soin feu (terrain, efficacité des soins, Grinder).
- **M3 — Pioche au choix** (Paquet de cartes, étape 7) : chaque carte piochée pendant un combat (action piocher, Photocopieuse, Cafffeeeeinnnn, Latte macchiato vanille…) devient un choix de 3 ; pioche multiple = un choix par carte, l'un après l'autre ; la main de départ n'est pas concernée ; George se déclenche après le choix (la carte choisie est protégée) ; Grinder s'applique aux propositions. Écran : 3 cartes côte à côte, touches 1/2/3, phrase méta au premier déclenchement (« Un jeu de cartes dans ton jeu de cartes. »).
- **M4 — Puissance des trinkets** : un multiplicateur d'effet par trinket (Diplôme EPFL).
- **M5 — Carte morte / mort instantanée** : carte « NO CARDS » injouable jusqu'à la fin du combat, adversaire tué sur le coup (jamais un boss). Fait.
- **M6 — Annulation de tour** : revenir à l'état du début du tour précédent, en rejouant la seed et les actions depuis le début. Fait (`run.Run`).
- **M7 — Tirage avantageux** : seuls les tirages de chance en faveur du joueur sont faits deux fois (Googz, NullPointerException, rareté des récompenses) ; on garde le favorable. Fait (`core.Luck`).
- **M10 — Déblocages durables** : conditions remplies pendant une partie → secret dans le pool à partir de la partie suivante, sauvegardé dans le dossier de sauvegarde. Fait (détails et spoilers : `docs/SECRETS.md`).
- **M9 — Clopes** : ressource de run, actions Fumer / Rouler, intensité de malédiction, multiplicateur final de stat (Ross), soins non plafonnés.
- **M8 — Combats et malédictions** : la partie est découpée en combats (un par adversaire) ; malédictions qui durent jusqu'au combat suivant (Gueule de bois).

## Statuts
Code : `core.status` (`StatusType` = tous les chiffres, `Statuses`), appliqués par `CombatEngine`.
- Applicables au **joueur et aux adversaires**, au plus un de chaque type par combattant ; retirés à la fin du combat.
- **Durée** : N tours de la cible ; l'effet par tour s'applique **au début** de son tour, le décompte se fait
  **à la fin** de son tour. Un statut ne décompte jamais à la fin du tour où il a été posé (une Fons qu'on se
  pose soi-même fait bien sauter un tour). **Hypnotisé** est compté en tours de celui qui frappe : posé sur
  l'adversaire, il vaut pour tes prochains tours.
- **Tirages** (seedés) : un statut que le joueur inflige à l'adversaire est un tirage en sa faveur (doublé par
  CLAUDE.md) ; un statut subi par le joueur (adversaire, ou sa propre Petite taffe) ne l'est pas.
- Dégâts de statut **bruts** : ni multiplicateur, ni DEF, ni Rebond.
- **Chance et durée** : +3 % par INT du lanceur, −5 % par RES de la cible (même à coup sûr), −1 tour à 5 RES
  (minimum 1) ; voir « Stats ».
- Affichage : icône 8×8 + tours restants à côté de chaque barre de PV (fenêtre), « Int3 » (terminal), « Statuts : … »
  (mode texte) ; journal ; un son par statut (`sfx.status.<id>`, sons existants en attendant les vrais).

| Statut | Effet | Durée | Réapplication |
|---|---|---|---|
| **Intox** | −5 PV par tour | 3 tours | **impossible** tant qu'Intox est actif |
| **Fons** | la cible ne joue pas (le joueur passe ses tours, l'adversaire n'attaque pas et garde son intent) | 1 à 3 tours (tirés au hasard) | durée remise au max |
| **Hypnotisé** | les cartes d'attaque font **×2** à la cible (attaques adverses ×2 si c'est le joueur) | 1 tour (certaines cartes : plus) | durée remise au max |
| **Bondage** | vole 5 PV par tour à la cible, au profit de celui qui l'a infligé ; **8 PV** sur un terrain plante | 3 tours | durée remise au max |
| **Brûlure** | −8 PV/tour sur terrain feu, −4 PV/tour ailleurs | 3 tours | durée remise au max |

- **Brûlure et eau** : impossible à poser sur un terrain eau ; **éteinte immédiatement** quand un terrain eau est posé.
- **Statuts négatifs du joueur** (Berceuse, purges) : Intox, Fons, Bondage, Brûlure. **Pas** l'Hypnose, la Gueule de bois ni l'Ivresse.
- Variantes de Fons posées par des cartes : soin en % des PV max par tour (un vrai soin : efficacité, Ross),
  immunité aux coups adverses (les statuts passent toujours). Avec une Fons d'un tour, l'attaque qui suit le
  tour sauté n'est plus couverte (le statut a décompté).

## Cartes
Code : `model.card` (`CardPool` = registre ; les cartes à pouvoir sont des fabriques partagées par le pool, le
deck de départ, les événements et les trinkets).
- **Pouvoir** : un texte + un effet (`EffectContext`), déclenché **après** l'effet principal (dégâts, soin,
  terrain) ; `copy()` le conserve. Affiché par ★ sur la carte et dans la description sous la main.
- Un sort peut avoir des **dégâts calculés** au moment du coup, **avant** le coup (Matrix, Pyromane…), ou
  **non multipliés** (ni élément, ni stats, ni hypnose, ni prochain sort ×N). Un sort à 0 dégât ne frappe pas ;
  un soin à 0 PV n'agit que par son pouvoir.
- Fiche détaillée de la carte sélectionnée avec **V** (en combat : effet réel, chaque multiplicateur, « + FOR »,
  « − DEF » de l'adversaire et la part ignorée, chance de critique, « (10 + 3) ×1.5 − 2 = 18 » ; au shop :
  valeurs de base). Les écrans de récompense et de pioche au choix montrent les valeurs de base.

### Sorts
| Rareté | Carte | Élément | Effet |
|---|---|---|---|
| Commun | Boule de feu | feu | 10 ; 10 % d'infliger Brûlure |
| Commun | Jet d'eau | eau | 10 ; l'adversaire perd 1 DEF pour le combat (l'eau use) |
| Commun | Fouet de lianes | plante | 10 ; 10 % d'infliger Bondage |
| Commun | Pet de Mamouz | neutre | 0 ; Intox à coup sûr |
| Commun | Carte basique pas ouf | neutre | 6 + 2 × FOR (la FOR est comptée par la carte, pas une seconde fois) |
| Commun | Lancer de manette | neutre | 12 ; critique garanti si CHA ≥ 3 |
| Commun | CAYOUUU | neutre | 12 ; ignore 2 points de DEF |
| Commun | Pain pita | géométrique | 10 ; ignore la DEF |
| Commun | Matrix | algébrique | 10 ; ×2 si les PV de l'adversaire finissent par 0 ou 1 (vérifié avant le coup) |
| Peu commun | Pluie de flammes | feu | 15 ; 20 % de Brûlure |
| Peu commun | Tsunami | eau | 15 ; ignore la DEF |
| Peu commun | Tempête d'épines | plante | 15 ; 20 % de Bondage |
| Peu commun | Moindre carré | algébrique | 1 à 30 + 2 × INT, tirage en ta faveur (le meilleur de deux avec CLAUDE.md) ; l'INT n'est pas ajoutée une seconde fois |
| Peu commun | dist(PLAYER) | géométrique | 10 + étages parcourus |
| Peu commun | Somnifère | spirituel | 0 ; Fons 1 tour à l'adversaire |
| Peu commun | Réveil brutal | neutre | 20 à un adversaire en Fons (et le réveille), sinon 8 |
| Rare | Purification des Chakra | spirituel | 0 ; l'adversaire est hypnotisé pour ton prochain tour |
| Rare | Pyromane | feu | 15, ×2 si l'adversaire brûle |
| Rare | Contre-exemple | algébrique | 0 ; annule les 1 + INT ÷ 3 (arrondi en dessous) prochains statuts que tu subirais (ce combat, cumulable) |
| Légendaire | Big Lez Shotgun | neutre | 80 ; recul : −2 FOR pour le reste du combat |
| Légendaire | Professeur d'or | spirituel | 35 non multipliés ; Hypnotisé 5 tours |

### Soins
| Rareté | Carte | Élément | Effet |
|---|---|---|---|
| Commun | Potion | plante | +10 PV |
| Commun | Petite taffe (ex-Petit pétou) | feu | +15 PV ; 15 % de tomber en Fons, +5 % des PV max par tour pendant cette Fons |
| Commun | Verre d'eau | eau | +10 PV |
| Commun | Sparadrap | neutre | +12 PV ; +1 DEF pour le combat |
| Commun | Soupe de mamie | neutre | +10 PV, retire l'Intox ; +1 RES pour le combat |
| Commun | Stingin' Roger | feu | −5 PV, +1 FOR pour tout le combat (cumulable) |
| Peu commun | Grande potion | plante | +20 PV |
| Peu commun | Seau d'eau | eau | +20 PV |
| Peu commun | Berceuse inconnue | spirituel | retire tous tes statuts négatifs ; +2 × ESP PV (soin spirituel, sans ESP en plus) |
| Rare | Taffe de foufou bâtard | feu | Fons 3 tours : immunisé aux coups adverses, +8 % des PV max par tour ; les statuts passent toujours |

## Terrains
### Terrains du joueur
- Un terrain peut porter des **bonus de stats** (source « terrain ») et/ou un **effet spécial** ; ils ne sont
  actifs **que tant qu'il est en jeu** : retirés quand le terrain change (l'ancien d'abord, puis le nouveau) et à
  la fin du combat.

| Terrain | Élément | Rareté | Effet |
|---|---|---|---|
| Marécage, Forêt, Volcan | eau, plante, feu | Commun | aucun |
| Dojo | neutre | Commun | **+1 FOR** (terrain de départ, et celui des boss) |
| Migros | neutre | Commun | si le combat se termine dessus : **+25 % de Choomies** |
| Gare | neutre | Commun | jouer une carte terrain depuis la Gare **ne coûte pas le tour** et donne **+1 clope** |
| Amazonie, Enfer, Atlantis | plante, feu, eau | Peu commun | attaques de leur élément **+20 %** |
| Désert kaléidoscopique | spirituel | Rare | l'hypnose fait **×4** au lieu de ×2 (dans les deux sens) ; **+1 ESP** |
| Espace de dimension n | algébrique | Rare | **+1 INT** ; chaque sort ou soin joué dessus est **rejoué une fois à 50 %** au début du tour suivant, **seulement si on y est encore** ; son pouvoir est rejoué en entier, avec sa chance normale (Boule de feu = 10 % au rejeu aussi) ; les terrains ne sont pas rejoués, un rejeu ne se rejoue pas et ne consomme pas le prochain sort ×N |
| Fractale | géométrique | Rare | **tous** les multiplicateurs totaux (sorts, soins, dégâts élémentaires des objets, attaque adverse) arrondis à l'entier supérieur (×1.25 → ×2, ×0.5 → ×1) |
| EPFL | feu | Légendaire | voir ci-dessous |

**EPFL** : pendant **3 tours** du joueur (après celui où il est posé), toutes ses valeurs sont **réellement
doublées** : dégâts de toutes les cartes, soins, DEF, **PV max et PV actuels** (×2 à l'arrivée).
Pas doublés : taille de main, Choomies, clopes. En quittant l'EPFL (changement de terrain, fin des 3 tours ou fin
du combat) : **PV actuels ÷ 2** (arrondis), bonus retirés ; en fin de combat, avant la fin du combat. Après 3 tours :
retour automatique sur le **Dojo** ; changer de terrain avant annule le minuteur. Tours restants affichés à côté
du terrain.

### Terrains des adversaires
Code : `model.enemy.EnemyTerrains`. Chaque combat commence sur le **terrain de l'adversaire** ; ces terrains ne
sont **jamais** dans le pool (ni récompense, ni shop). Leurs malus touchent le joueur tant qu'il y reste : jouer
une carte terrain, c'est « sortir de chez l'ennemi ». Arriver chez l'adversaire n'est pas un changement de terrain
joué (M1 et Plan du campus ne se déclenchent pas). La règle existante s'applique : un adversaire sur un terrain de
son élément attaque à ×1.25. Boss provisoires : départ sur le Dojo.

| Adversaire | Terrain | Élément | Malus pour le joueur |
|---|---|---|---|
| Caddie énervé | Parking du Migros | neutre | −1 taille de main |
| Clarence | Le canapé | neutre | aucun |
| Le Peneu | Bord d'autoroute | neutre | −2 DEF (stat `DEFENSE`) |
| Choomah (et variantes) | Buisson louche | plante | −20 % soins |
| Paresseux | Canopée | plante | −15 % dégâts des sorts |
| T-Rex défoncé | Jurassic Ecublens | plante | adversaire +15 % dégâts |
| Camé | Passage sous-gare | feu | −2 RES |
| Vafi | Amphi d'examen | feu | −2 INT |
| Seau de fuite | Toit qui fuit | eau | −2 ESP |
| Étudiant post-examen | Biblio à 3h du mat | eau | −1 taille de main |
| Gourou du Sat | Salle de méditation | spirituel | −15 % dégâts des sorts |
| NG | Salle d'exercices | algébrique | −1 taille de main |
| Hypercube misogyne | Cabinet psy scintillant | géométrique | adversaire +15 % dégâts |
| Gobelin | Clairière d'adolescent de base | plante | −1 taille de main *(provisoire)* |
| Engeance des cookies | Le canapé | neutre | aucun |

## Choomies (monnaie)
- **15 Choomies** au début de la partie. Gagnés à chaque victoire : commun 12-18, élite 30-40, boss 80-100
  (tirés au hasard), Clarence 75. Multipliés par la stat `CHOOMIES_GAIN` (arrondi).
- Les Choomah en volent (5 par vol). Jamais sous 0.
- Affichage : en haut de l'écran de combat (« 15 Choomies »), dans l'état en mode texte.

## Événements (lot 3)
Code : `cardistry.story` (`Story`, `StoryChoice`, `StoryContext`, `StoryRegistry` = registre), joué par
`CombatEngine.startStory` / `chooseStory`.
- Un événement = un titre, un texte, des choix. Un choix peut demander une condition (ex. « il te faut 20
  Choomies ») : sinon il est affiché grisé avec ce qui manque, et on ne peut pas le prendre.
- Conséquences possibles (`StoryContext`) : Choomies (gain ou paiement), soin (un vrai soin : efficacité,
  Ross), dégâts (**jamais mortels** hors combat : il reste au moins 1 PV), clopes, Gueule de bois au
  prochain combat, carte ajoutée au deck, et des tirages de chance (en faveur du joueur : doublés par
  CLAUDE.md). On en ajoutera selon les vrais événements.
- Tirage : un sous-flux de la seed (`evenements`) ; on ne revoit un événement qu'après les avoir tous vus.
- Après le choix, un écran montre ce qui s'est passé (« Entrée continuer »). Ctrl+Z rejoue les choix.
- **Placeholders** (en attendant les vrais, références EPFL et soirées) :

| Événement | Choix |
|---|---|
| Distributeur en panne | Secouer : 50 % +25 Choomies, sinon −6 PV · Passer ton chemin |
| Un inconnu généreux | Accepter : +15 PV et Gueule de bois au prochain combat · Lui demander une astuce : +1 Boule de feu dans le deck · Refuser |
| Machine à clopes | Acheter un paquet (20 Choomies) : +4 clopes · Taper dessus : 30 % +2 clopes, sinon −4 PV · Partir |

## Shop (lot 2)
Code : `CombatEngine.openShop` / `shopAction`, `core.Shop`, `core.shop` (`ShopPrices`, `ShopOffer`, `ShopAction`).
- Nœud `$` de la carte. **5 cartes** (noms différents, tirées du registre avec les poids des trinkets),
  **2 objets**, **2 trinkets** (jamais un trinket déjà équipé, jamais un secret). La rareté des articles
  n'est **pas** doublée par CLAUDE.md (au shop, plus rare = plus cher, ce n'est pas un avantage).
- **Prix** (Choomies) : objets et trinkets selon la rareté, 50 / 80 / 130 / 200. Cartes : **60 %** du prix
  d'un objet de même rareté, arrondi à 5 → **30 / 50 / 80 / 120**, selon la **rareté de la carte**.
- **Retrait d'une carte** : 75, +25 à chaque retrait de la **partie** (75, 100, 125…) ; jamais sous 5 cartes ;
  autant de fois qu'on veut tant qu'on peut payer.
- **Reroll** : 20, +10 à chaque reroll dans **ce** shop (le prix repart à 20 au shop suivant) ; renouvelle les
  9 articles (vendus compris).
- Inventaire plein à l'achat : même écran de remplacement que pour les récompenses (ou retour au shop).
- Écran : cartes en haut, objets et trinkets en dessous, prix grisés si pas assez de Choomies, « vendu »
  après achat, détail de l'article sélectionné ; R reroll, T retirer une carte, P partir (Échap ouvre la pause, comme ailleurs). Mode texte :
  liste numérotée, `r`, `t`, `0`.
- Accueil **provisoire** : « Entre, entre ! Tout est à vendre... enfin, presque. » (nom du vendeur et
  réplique à venir).
- Hooks : `onShopEnter`, `onItemBought` (Grinder acheté ajoute aussi son Petit pétou).
- Ctrl+Z : les achats sont rejoués à l'identique (mêmes articles, mêmes rerolls).

## Secrets / easter eggs
- Trinkets « Secret » débloqués durablement par des conditions remplies pendant une partie (façon *Isaac*) ; des codes (seed spéciale, code clavier) les donnent pour une seule partie.
- Garder la liste des secrets dans `docs/SECRETS.md` (spoilers !).
- Existant : terrain « EPFL » de type feu (Légendaire), soin « Petite taffe » (ex-Petit pétou).

## Interface (terminal ASCII, Lanterna)
Fait : écrans titre, règles, combat, fin de partie ; clavier direct (←/→ + Entrée, 1-7, P piocher, Q quitter avec confirmation) ; sprites ASCII des 3 adversaires ; barres de PV ; main en cadres ASCII colorés par élément ; aperçu de l'effet réel de la carte sélectionnée ; journal de combat ; animations (dégâts qui clignotent, chiffres flottants, tremblement/charge de l'ennemi, mort, arrivée, pioche).
Fait aussi : écran de pioche au choix (Paquet de cartes : 3 cartes, touches 1/2/3, effet réel de la sélectionnée, phrase méta au premier déclenchement) ; écran « jeter un trinket » obligatoire (Diplôme perdu) ; sets dans l'inventaire (palier atteint, prochain palier, ×2 si amplifié) et bandeau « ★ Set … activé » ; trinkets amplifiés marqués « (effet ×2) » ; paquet de clopes (petit sprite ASCII en bas à droite, n/20, touches F fumer / G rouler, jauge Honoré) ; PV au-dessus du max en doré (Ross) ; transformation Googz affichée (journal + texte flottant) ; effets temporaires affichés avec leur durée restante (ligne au-dessus du terrain, liste complète dans l'inventaire) ; objets et cartes homonymes (Caïpirinha, Petit pétou) distingués à l'écran (« la carte … » / « l'objet … » dans le journal, couleur d'élément pour les cartes, de rareté pour les objets) ; écran de récompense (cartes côte à côte, couleur de rareté), écran de remplacement si inventaire plein, inventaire (touche I), objets utilisables avec W/E/R (clavier QWERTZ, action gratuite), trinkets affichés en combat.
Fait aussi : carte de la run (écran Lanterna + mode texte), intents des adversaires, sprites animés et visionneuse `--sprites`.
Fait aussi : shop (plein écran + mode texte) ; fenêtre du jeu par défaut (police pixel embarquée, double buffer, images PNG avec repli ASCII, filtre CRT désactivé par défaut).
Fait aussi : événements (plein écran + mode texte).
Fait aussi : son (musique par contexte avec fondu, effets sur les événements, placeholders), menu pause (Échap), Options (volumes, muet) depuis le titre et la pause.
Fait aussi : statuts (icônes et tours restants près des PV, intent « Endormi »), pouvoirs (★), deck (**D**, n'importe où : regroupé par type, ×N, défilement ; mode texte `d`), fiche de carte (**V** en combat et au shop ; mode texte `v N`).
Fait aussi (plan V3) : fiche des stats du joueur (**C**, n'importe où pendant une partie : les 6 stats, leur effet, le détail de chaque bonus) et de l'adversaire (**A**, en combat) ; mode texte `c` et `a` ; DEF et RES de l'adversaire près de sa barre de PV ; écran Progression après les boss ; personnage animé dans la fenêtre (idle, attaque quand il lance un sort, dégâts, mort : `images/player/<id>.<animation>.<n>.png`) ; emplacement du familier (alien de Sassy) avec sa barre de vie.
Reste : transitions d'écran ; vrais sons des statuts ; vrais sprites des personnages, de l'alien, de Choomah, de Clarence et du boss (placeholders, un fichier par animation).

- Plein écran, input direct au clavier (plus de « Appuie sur Entrée »).
- Écrans : titre, carte de la run, combat, shop, récompense, game over.
- Combat : sprite ASCII de l'ennemi, barres de PV, main de cartes dessinées en cadres ASCII, trinkets affichés, journal de combat.
- Animations : dégâts qui clignotent, chiffres flottants, tremblement de l'ennemi, transitions d'écran.
- Couleur de rareté et d'élément partout.

## Questions ouvertes
- **[À DÉCIDER]** Nom et attaques du boss du niveau 3 (placeholder « Boss du niveau 3 »).
- **[À DÉCIDER]** Noms des variantes de Choomah (provisoires : « Choomah pressé », « Choomah costaud »).
- **[À DÉCIDER]** Nom du vendeur du shop et sa réplique d'accueil. Il a un sprite (ébauche : bonnet, barbe, clope au bec) et il fume.
- **[À DÉCIDER]** Dave : son soin profite-t-il des bonus de soin (efficacité, Ross) ou est-il brut ? *Provisoire : il en profite.*
- Provisoire : chiffres des paliers d'Ivresse (+10 / +25 / +50 %) et de la clope (+5 PV, sans limite par tour) à ajuster en jouant.
- **[À DÉCIDER]** Vrais sprites des personnages et de l'alien (placeholders par animation dans `art/player/`).
- **[À DÉCIDER]** Équilibrage après le plan V3 : avec les cartes tirées selon leur rareté, le bot d'équilibrage gagne beaucoup moins (voir le compte rendu du plan V3) ; poids des cartes à revoir ?
- **[À DÉCIDER]** CMS : reste « +50 % dégâts, adversaire +25 % » pour l'instant (option : +3 FOR, +3 INT, adversaire +2 FOR).
- **[À DÉCIDER]** Les vrais événements (les 3 actuels sont des placeholders).
- Provisoire : les intents des Choomah gardent les chiffres lisibles dans le cri.
- Nom définitif du jeu (« Cardistry » affiché en attendant).
- **[À DÉCIDER]** Valeur des PV max de départ (100 provisoirement, stat `MAX_HP`).
- Provisoire : PV du Gobelin (22) et malus de sa Clairière (−1 taille de main).
- **[À DÉCIDER]** CMS : « +50 % dégâts » = sorts seulement, ou aussi objets et trinkets ? *Provisoire : +50 % sur les dégâts élémentaires (sorts et Gaz du bat Inf), pas sur les dégâts bruts (Plan du campus, Pichet vide).*

### Tranchées
- ~~Plan V3 (stats)~~ → six stats (FOR, INT, ESP, DEF, RES, CHA) pour le joueur et les adversaires (section « Stats »). La RES s'applique aussi aux statuts « à coup sûr » ; critique ×1,5 ; +2 points de stats et +25 PV max après chaque boss ; Frederic BLC 2 partout ; adversaires +1 FOR et +1 DEF par niveau (pas les boss) ; sorts GLITCH : la meilleure stat offensive ; CMS inchangé pour l'instant ; Gaz du bat Inf sans GLITCH. Interprétations validées : coup adverse = (déclaré × niveau + FOR) × multiplicateurs × critique − DEF, minimum 1, coup par coup ; critique sur les sorts multipliés (et les coups adverses) ; dégâts élémentaires des objets sans stat ni critique ; rejeu = ×0,5 avant la DEF ; ESP sur les cartes et objets à PV fixes ; INT du joueur pour tous ses statuts ; boss sans scaling de stats ; Carte basique et Moindre carré comptent leur stat eux-mêmes ; Berceuse 2 × ESP sans ESP en plus ; Contre-exemple 1 + ⌊INT ÷ 3⌋ ; nouveaux objets communs ; touches C et A. Alien de Sassy : familier de 20 PV, 25 % des attaques le visent, assommé à 0 PV jusqu'au combat suivant, ni DEF ni statuts. Légendaires : cartes tirées selon leur rareté, poids des Légendaires et des secrets à 1. Cartes mortes hors de la taille de main.
- ~~Qui bat qui des nouveaux éléments~~ → Géométrique > Algébrique > Spirituel > Géométrique, GLITCH bat tout (règle du code, `model.Element`).
- ~~Prix des cartes au shop~~ → selon la rareté de la carte.
- ~~Ennemis des nouveaux éléments~~ → Gourou du Sat (spirituel), NG (algébrique), Hypercube misogyne (géométrique), plus le Gobelin (plante) et l'élite Engeance des cookies (GLITCH).
- ~~Plan V2~~ → statuts, pouvoirs, terrains à effet, terrains des adversaires, nouvelles cartes (voir ces sections). Moindre carré : tirage en ta faveur (doublé par CLAUDE.md). Petite taffe : reste Commune. Fractale : multiplicateur total arrondi à l'entier **supérieur**. Espace : le rejeu garde la chance normale des statuts. Hypnose comptée en tours de celui qui frappe ; un statut ne décompte pas à la fin du tour où il est posé ; chances doublées seulement contre l'adversaire ; immunité de la Taffe = coups adverses ; main au-delà du max : on garde ses cartes.
- ~~Tables d'éléments contradictoires~~ → sortilèges : table (×1.5 / ×0.5 / ×1) ; terrain : multiplie l'attaque adverse (×1.25 / ×0.75 / ×1). Voir « Multiplicateurs d'éléments ».
- ~~Le nouvel adversaire attaque-t-il le tour où il arrive ?~~ → Non.
- ~~Cumul des multiplicateurs de stats~~ → ils s'additionnent.
- ~~Soins sans plafond~~ → les soins sont plafonnés aux PV max, que des objets/trinkets peuvent augmenter.
- ~~Obtention des objets/trinkets~~ → 1 récompense au choix parmi 3 après chaque adversaire vaincu ; inventaire plein → remplacer ou refuser.
- ~~Slots~~ → 3 objets, 5 trinkets.
- ~~Utiliser un objet~~ → action gratuite.
- ~~Qu'est-ce qu'un combat~~ → un adversaire. Chaque combat commence avec une nouvelle main de 5 cartes.
- ~~Diplôme EPFL : portée du doublement, perte avec 6 trinkets~~ → tout l'effet doublé malus compris ; +1 emplacement seulement ; perdre le Diplôme avec 6 trinkets oblige à en jeter un (au choix).
- ~~Gueule de bois : combien, quand~~ → une seule par combat, aucune pendant le dernier combat.
- ~~Ptit Sat ?~~ → dégâts élémentaires, comme CMS.
- ~~Pichet vide mortel~~ → peut tuer le joueur ; mort simultanée = défaite.
- ~~Gueule de bois avec une main pleine~~ → sans objet : la main de départ (5) reste sous la taille max (6 avec la malédiction).
- ~~Secrets : déblocage~~ → durable, par conditions remplies pendant une partie ; le secret entre dans le pool (poids 2, puis 1 depuis le plan V3) à la partie suivante ; les codes ne valent que pour la partie en cours. NullPointerException : NO CARDS injouable jusqu'à la fin du combat, le dernier adversaire compte comme boss. CLAUDE.md : seuls les tirages de chance en faveur du joueur sont doublés ; un secret débloqué pendant la partie gagnante compte. Ctrl+Z : une fois par combat, ni après la mort ni au premier tour d'un combat. `--seed null` : seed aléatoire affichée. Mode texte : seul le code « claude » (détails : `docs/SECRETS.md`).
- ~~Pioche infinie~~ → remplacée par un deck construit (lot 1 bis de la run complète).
- ~~Run complète (lot 1)~~ → 3 niveaux de 8 étages boss compris (7 = Sieste, 8 = Boss) ; valeurs d'adversaires, de boss et de scaling validées pour démarrer ; Rebond du Peneu ne tue pas ; Sieste : soin 30 %, +3 clopes ou retirer une carte, n'efface pas la Gueule de bois ; après chaque boss, la moitié des PV manquants ; NullPointerException peut annuler une élite (seuls les 3 boss sont immunisés) ; Moscow Mule annule toute une attaque mais pas les vols ni la Taxe ; tirages des adversaires hors CLAUDE.md ; carte tirée d'un sous-flux nommé de la seed ; « Plante » affiché pour l'élément nature.
- ~~Lot 1 : choix provisoires~~ → récompense de carte à part, après l'objet ; Peu commun en élite, Rare au boss ; la Sieste est un soin (efficacité, Ross), la récupération après boss non ; le Shot de Jäger est consommé si le Paresseux esquive ; Sieste possible aux étages 4-5.
- ~~Koala~~ → c'est un **Paresseux** accroché à sa branche (« Badoing, badoing ! »), peu de dégâts, pas de saut. ~~Contrôle surprise~~ → **Série d'algèbre** (Vafi). Les Choomah parlent en cris (« ooOOOaAAAOaAOOA »).
