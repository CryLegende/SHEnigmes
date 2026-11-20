<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHEnigmes

*Codes secrets propres à chaque joueur pour les énigmes : séquences de symboles et mots, collectes de pièces à retrouver et présences à tenir dans une zone.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Une énigme est un code secret différent pour chaque joueur, tiré au hasard la première fois qu'on en a besoin, ou une collecte de pièces à trouver une par une. Le plugin ne connaît aucun autre plugin : tout est lancé depuis la console par des commandes, et tout ce qui se passe en retour est une liste de commandes console écrite dans `config.yml` ou un placeholder PlaceholderAPI.

| Type | Le code | Exemples d'usage |
|---|---|---|
| `sequence` | une suite de symboles numérotés, avec ou sans répétition | lanternes à allumer dans l'ordre, mélodie à rejouer |
| `mot` | un mot dont chaque lettre est une pièce | lettres cachées dans des livres, mot écrit en runes |
| `collecte` | aucun code, N pièces numérotées à trouver une fois chacune, dans n'importe quel ordre | abeilles perdues cachées dans le monde |
| `presence` | aucun code, rester N secondes d'affilée dans une zone en remplissant une condition de tenue | méditer assis ou accroupi au pied d'un arbre |

### Règles

- **Un code par joueur, pour toujours.** Le code est tiré avec `SecureRandom`, puis gardé dans le `PersistentDataContainer` du joueur (clé `shenigmes:<id>`, JSON compact avec le code, la saisie en cours, les pièces vues et l'état résolu). Il survit aux reconnexions et aux redémarrages et ne change qu'avec `/shenigmes reset`. Lire un placeholder tire le code s'il n'existe pas encore.
- **Séquence.** Chaque `saisir` compare l'entrée au code. Une bonne entrée lance `bon`, la dernière lance `reussi`, une mauvaise lance `faux` et remet la saisie à zéro. Sans entrée pendant `oubli-saisie` secondes, la saisie est aussi remise à zéro. Une fois résolue, `saisir` lance `deja`.
- **Rejeu.** `jouer` joue le code symbole par symbole, un toutes les `intervalle` ticks (`avant` au départ, `apres` un intervalle après le dernier). Un nouveau rejeu de la même énigme remplace celui en cours, et la déconnexion du joueur l'arrête.
- **Mot.** Le mot est tiré parmi les mots de `mots` dont la longueur vaut `pieces` (les autres sont signalés au chargement). `montrer` révèle une pièce (`%lettre%`) et la marque comme vue. `repondre` compare sans casse, sans accents et sans espaces. Une fois résolu, `repondre` lance `deja`.
- **Collecte.** Pas de code : `pieces` pièces numérotées de 1 à `pieces`, que le plugin ne place nulle part (l'objet cliqué, un modèle 3D par exemple, lance `trouver` depuis la console). Chaque `trouver` d'une pièce nouvelle l'enregistre pour le joueur et lance `trouve` (`%rang%`, `%trouves%`, `%pieces%`, `%restants%`) ; si c'était la dernière, l'énigme est résolue et `reussi` est lancé juste après `trouve`, dans le même bloc de commandes. Une pièce déjà trouvée ne lance que `deja`. Lire un placeholder d'une collecte n'enregistre rien, et changer `pieces` ne fait rien perdre (seuls les rangs de 1 à `pieces` comptent).
- **Présence.** Pas de code : une sphère (`zone` : monde, x, y, z, rayon) et une `duree` en secondes. Une fois par seconde, chaque joueur du monde de la zone qui s'y tient, remplit les `conditions` et au moins une condition de `tenir` (par exemple `%shsit_assis% == true` ou `%player_is_sneaking% == yes`) avance d'une seconde et lance `progres` (`%secondes%`, `%duree%`, `%restants%`) ; à la dernière, `reussi`. Dès qu'il cesse (sorti, condition fausse, plus aucune tenue), il repart de zéro et `interrompu` est lancé. Rien n'est enregistré : une déconnexion, un `reload` ou un redémarrage fait repartir de zéro. Les placeholders ne sont lus que pour les joueurs dans la zone.
- **Conditions.** Une énigme peut avoir des `conditions` (`%player_name% == Steve`, `%shenigmes_autre_resolue% == true`...), évaluées avec PlaceholderAPI pour le joueur. Si l'une est fausse, `saisir`, `jouer`, `montrer`, `repondre` et `trouver` ne font rien d'autre que lancer `actions.refuse`, et rien n'est tiré ni enregistré. Les placeholders ne tiennent pas compte des conditions.
- **Aucun texte de joueur dans le code.** Les messages des joueurs sont dans les actions de `config.yml`, ceux de la console dans `messages.yml`. Le texte d'une réponse n'est jamais inséré dans une commande.
- **Parallélisme.** Les états et les rejeux passent par le scheduler du joueur, et les commandes des actions sont lancées en console sur le thread principal, dans l'ordre (DivineMC fait tourner les mondes en parallèle). Les placeholders lisent une copie en mémoire, sans risque depuis un autre thread.

## Dépendances

Facultative : `PlaceholderAPI`. Sans lui, les placeholders `%shenigmes_...%` n'existent pas et les conditions comparent leurs textes sans les remplacer.

## Commandes

Alias `/enigmes`. Toutes marchent depuis la console, le joueur doit être connecté.

| Commande | Description | Permission |
|---|---|---|
| `/shenigmes saisir <joueur> <id> <symbole>` | Séquence, une entrée du joueur. | `shenigmes.admin.saisir` |
| `/shenigmes jouer <joueur> <id>` | Séquence, rejoue le code au joueur (section `jouer`). | `shenigmes.admin.jouer` |
| `/shenigmes montrer <joueur> <id> <rang>` | Mot, lance `montrer` avec `%lettre%` et marque la pièce comme vue. | `shenigmes.admin.montrer` |
| `/shenigmes repondre <joueur> <id> <texte...>` | Mot, compare sans casse, sans accents et sans espaces. Sans texte, c'est une mauvaise réponse. | `shenigmes.admin.repondre` |
| `/shenigmes trouver <joueur> <id> <rang>` | Collecte, le joueur trouve la pièce de ce rang (de 1 à `pieces`) : lance `trouve` puis `reussi` si c'est la dernière, ou `deja` si elle était déjà trouvée. | `shenigmes.admin.trouver` |
| `/shenigmes reset <joueur> <id\|*>` | Efface le code ou les pièces trouvées (un nouveau code est tiré au prochain besoin, une collecte repart de zéro), `*` pour toutes les énigmes. | `shenigmes.admin.reset` |
| `/shenigmes etat <joueur> [id]` | Code, saisie en cours, pièces vues ou trouvées et état résolu, sans rien tirer. | `shenigmes.admin.etat` |
| `/shenigmes reload` | Recharge `config.yml` et `messages.yml` (un fichier illisible ne change rien et est nommé, les codes des joueurs ne sont jamais touchés). | `shenigmes.admin.reload` |

Sans argument, une ligne d'usage ne liste que les sous-commandes permises. Les variables remplacées dans les commandes des actions sont `%player%`, `%id%`, `%symbole%`, `%rang%`, `%longueur%`, `%lettre%`, `%nom%`, `%trouves%`, `%pieces%` et `%restants%`.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shenigmes.admin` | Toutes les sous-commandes de `/shenigmes` (parent des nœuds `shenigmes.admin.*`). | `op` |
| `shenigmes.admin.saisir`, `.jouer`, `.montrer`, `.repondre`, `.trouver`, `.reset`, `.etat`, `.reload` | Une par sous-commande du même nom. | `op` |

## Placeholders

`%shenigmes_<id>_<valeur>%`, pour le joueur concerné. Un identifiant inconnu, une valeur qui ne s'applique pas au type de l'énigme ou un joueur hors ligne donnent un texte vide.

| Valeur | Type | Résultat |
|---|---|---|
| `resolue` | tous | `true` ou `false` |
| `code` | séquence, mot | `3-1-4-5-2` pour une séquence, le mot pour un mot |
| `code_<i>` | séquence, mot | le symbole ou la lettre de rang `i` |
| `longueur` | tous | longueur du code, nombre de pièces d'une collecte, ou secondes à tenir d'une présence |
| `noms` | séquence | les noms des symboles du code, séparés par `separateur` |
| `nom_<i>` | séquence | le nom du symbole de rang `i` du code |
| `saisie` | séquence | nombre d'entrées en cours |
| `vues` | mot | nombre de pièces vues |
| `vue_<i>` | mot | `true` si la pièce de rang `i` a été vue |
| `pieces` | mot, collecte | nombre de pièces |
| `lettre_<i>` | mot | la lettre de rang `i` |
| `trouves` | collecte | nombre de pièces trouvées |
| `trouve_<i>` | collecte | `true` si la pièce de rang `i` a été trouvée |
| `restants` | collecte, présence | nombre de pièces qui restent à trouver, ou de secondes qui restent à tenir |
| `secondes` | présence | secondes tenues d'affilée en ce moment |
| `duree` | présence | secondes à tenir |

## Configuration

Fichiers par défaut du plugin, avec des valeurs d'exemple (trois énigmes neutres, des commandes vanilla seulement). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHEnigmes/
├── <a href="src/main/resources/config.yml">config.yml</a>     énigmes (types, actions, rejeu, conditions), référence en en-tête
└── <a href="src/main/resources/messages.yml">messages.yml</a>   messages de la console et des administrateurs
</pre>

Une séquence, en résumé :

```yaml
enigmes:
  lanternes:
    type: sequence
    symboles: 5
    longueur: 5
    repetitions: false
    oubli-saisie: 60
    conditions:
      - '%player_name% != Inconnu'
    actions:
      bon:
        - 'say %player% a bien placé la lanterne %rang% sur %longueur%'
      faux:
        - 'say %player% recommence'
      reussi:
        - 'say %player% a trouvé la séquence'
```

Une collecte, en résumé (l'objet cliqué lance `/shenigmes trouver <joueur> abeilles <rang>` depuis la console) :

```yaml
enigmes:
  abeilles:
    type: collecte
    pieces: 5
    conditions:
      - '%questcreator_variable_secret_miel% == 1'
    actions:
      refuse:
        - 'tellraw %player% {"text":"Tu ne cherches pas encore les abeilles.","color":"gray"}'
      trouve:
        - 'tellraw %player% {"text":"Abeille %trouves% sur %pieces%, il en reste %restants%.","color":"yellow"}'
      reussi:
        - 'say %player% a retrouvé toutes les abeilles'
      deja:
        - 'tellraw %player% {"text":"Celle-ci est déjà retrouvée.","color":"gray"}'
```

Une présence, en résumé (une minute assis avec SHSit, ou accroupi, au pied d'un arbre) :

```yaml
enigmes:
  mediter:
    type: presence
    zone: {monde: spawn, x: -74.5, y: 45.5, z: -29.5, rayon: 3.5}
    duree: 60
    tenir:
      - '%shsit_assis% == true'
      - '%player_is_sneaking% == yes'
    actions:
      progres:
        - 'title %player% actionbar {"text":"Méditation %secondes%/%duree% s"}'
      interrompu:
        - 'title %player% actionbar {"text":"Tu t\'es relevé : on recommence."}'
      reussi:
        - 'say %player% a médité une minute'
```

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
