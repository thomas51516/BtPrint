# BT Print — impression Bluetooth ESC/POS (type RawBT)

Application Android (Kotlin, **Android 8 minimum**, minSdk 26) qui imprime sur des imprimantes thermiques
Bluetooth ESC/POS (Xprinter, Goojprt, MTP, Sunmi externes, etc.) depuis :

- la fenêtre **Imprimer** d'Android (Chrome, lecteurs PDF, Odoo dans le navigateur) via un
  service d'impression système, comme RawBT ;
- le menu **Partager** d'Android (PDF, images, texte) et « Ouvrir avec » pour les PDF ;
- des liens **`btprint:`** cliqués dans une page web ;
- un **serveur HTTP local** (`http://127.0.0.1:8085`) appelé par un navigateur ou une PWA (Odoo POS).

Librairie d'impression : [DantSu/ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) 3.3.0.

---

## 1. Ouvrir et compiler

1. Android Studio récent (Narwhal ou plus) avec le SDK Android 36.
2. *File → Open* → dossier `BtPrint`.
3. Laisser Gradle synchroniser (télécharge AGP, Compose, DantSu via JitPack, NanoHTTPD).
   Si Android Studio propose de mettre à jour AGP / Kotlin / Compose BOM, vous pouvez accepter.
4. Brancher un téléphone Android 8+ et lancer *Run*.

### APK de production (signé)

La release est signée automatiquement si un fichier `keystore.properties` existe à la racine
du projet (il est ignoré par git) :

```properties
storeFile=/chemin/vers/btprint-release.jks
storePassword=…
keyAlias=btprint
keyPassword=…
```

Puis `./gradlew :app:assembleRelease` produit `app/build/outputs/apk/release/app-release.apk`
(environ 2 Mo, minifié par R8). Sans ce fichier, passer par *Build → Generate Signed App Bundle / APK*.

> **Conserver le keystore et ses mots de passe en lieu sûr.** Toute mise à jour de l'app doit
> être signée avec la même clé, sinon Android refuse d'installer par-dessus la version existante.

En ligne de commande (JDK 17 ou 21 requis, par ex. celui d'Android Studio) :

```sh
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew :app:assembleDebug      # APK debug
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew :app:testDebugUnitTest  # tests unitaires (JVM)
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew :app:lintDebug          # lint Android
```

## 2. Première utilisation

1. Appairer l'imprimante dans **Réglages → Bluetooth** (code PIN souvent `0000` ou `1234`).
2. Ouvrir BT Print, accepter les autorisations (Bluetooth + notifications).
3. Choisir l'imprimante, la largeur de papier (58 / 80 mm), l'encodage.
4. Bouton **Ticket test** : vérifie alignements, accents, code-barres, QR code.
5. Bouton **Image test** : vérifie le rendu image et le tramage.
6. Pour imprimer depuis le menu **Imprimer** des autres apps : bouton **Services d'impression**
   (ou *Paramètres → Impression*), activer **BT Print**. L'imprimante sélectionnée dans l'app
   apparaît alors dans la fenêtre d'impression, avec des formats « Ticket 58 mm × 100/200/300 mm ».
   Si le contenu est coupé à droite (reçu Odoo), choisir un format « réduit depuis 80 mm » ou
   « réduit depuis 100 mm » : la page est plus large, puis réduite à l'impression.

> **Accents** : si « é » ou « € » sortent mal, changer l'encodage (WPC1252 → PC858 → PC850).
> La table exacte dépend du firmware de l'imprimante.
>
> **Idéogrammes à la place des accents** (« Caf妹e ») : l'imprimante est en mode caractères chinois.
> L'app envoie automatiquement la commande `FS .` qui l'annule au début de chaque ticket.
>
> **Lignes coupées en deux** : la largeur choisie (80 mm) est supérieure à celle de l'imprimante (58 mm).

## 3. Structure du projet

```
app/src/main/java/com/odootogo/btprint/
├── data/PrinterSettings.kt       Réglages (imprimante, papier, encodage, coupe, serveur)
├── print/
│   ├── PrintJob.kt               Types de travaux : texte, ESC/POS brut, image, PDF
│   ├── PrintQueue.kt             File d'attente globale + statut observable
│   ├── PrinterEngine.kt          Connexion Bluetooth SPP + envoi via DantSu
│   ├── ImageUtils.kt             Redimensionnement, tramage Floyd–Steinberg, rendu PDF, rognage
│   ├── Payloads.kt               Décodage base64 / data URL et liens btprint: (logique pure)
│   └── TestReceipts.kt           Ticket et image de test
├── service/
│   ├── PrintService.kt           Service au premier plan (connectedDevice) : file + serveur
│   └── SystemPrintService.kt     Service d'impression système (fenêtre « Imprimer » d'Android)
├── server/LocalPrintServer.kt    Serveur HTTP NanoHTTPD sur 127.0.0.1
└── ui/
    ├── MainActivity.kt           Écran de configuration (Jetpack Compose)
    └── ShareReceiverActivity.kt  Réception des partages et des liens btprint:
```

Fonctionnement : chaque entrée (UI, partage, HTTP) crée un `PrintJob` et le pousse dans `PrintQueue`.
`PrintService` consomme la file et appelle `PrinterEngine`, qui ouvre la connexion, imprime, puis ferme.
Le service d'impression système reçoit du système un PDF déjà mis en page à la largeur du ticket
et l'envoie directement à `PrinterEngine` (les deux chemins sont sérialisés sur la même imprimante).
Les images et PDF sont redimensionnés à la largeur de tête (384 px en 58 mm, 576 px en 80 mm),
tramés en noir et blanc, puis envoyés par bandes de 256 px pour ne pas saturer le buffer.
Les pages PDF sont d'abord rognées de leurs marges blanches (haut, bas, côtés) : un reçu web
mis en page pour 80 mm occupe ainsi toute la largeur d'une tête 58 mm, en réduction.

## 4. API HTTP locale

Le serveur écoute uniquement sur `127.0.0.1` (non accessible depuis le réseau).
Toutes les réponses incluent les en-têtes CORS et `Access-Control-Allow-Private-Network`.

**Jeton** : les routes `POST` exigent le jeton affiché dans l'écran de réglages (bouton *Copier*),
dans l'en-tête `X-Print-Token` ou le paramètre `?token=`. Réponse sans jeton : `401 {"ok":false,…}`.
L'exigence peut être désactivée dans les réglages (déconseillé hors d'une caisse dédiée).

| Méthode | Route          | Corps                                                  |
|---------|----------------|--------------------------------------------------------|
| GET     | `/status`      | —  (renvoie imprimante, papier, statut)                |
| POST    | `/print/text`  | Texte formaté DantSu, UTF-8                            |
| POST    | `/print/raw`   | Octets ESC/POS en base64                               |
| POST    | `/print/image` | Image PNG/JPEG en base64 ou data URL                   |
| POST    | `/print/pdf`   | PDF en base64 ou data URL                              |

Réponse : `202 {"ok":true,"message":"Travail ajouté à la file"}`.
`GET /status` renvoie aussi `"tokenRequired": true|false`.

### Exemple JavaScript (depuis une page web sur le téléphone)

```js
const BT_PRINT = "http://127.0.0.1:8085";
const TOKEN = "…"; // jeton copié depuis l'app
const headers = { "Content-Type": "text/plain", "X-Print-Token": TOKEN };

// Texte formaté
await fetch(`${BT_PRINT}/print/text`, {
  method: "POST",
  headers,
  body: "[C]<b>MA BOUTIQUE</b>\n[L]Article[R]1 500 FCFA\n",
});

// Image (ex. ticket rendu dans un <canvas>)
const dataUrl = canvas.toDataURL("image/png");
await fetch(`${BT_PRINT}/print/image`, { method: "POST", headers, body: dataUrl });
```

### Syntaxe du texte formaté (DantSu)

- Alignement en début de colonne : `[L]` gauche, `[C]` centre, `[R]` droite
  (plusieurs colonnes par ligne : `[L]Article[R]Prix`).
- Style : `<b>gras</b>`, `<u>souligné</u>`, `<font size='big'>…</font>` (`normal`, `wide`, `tall`, `big`).
- Codes : `<barcode type='128' height='10'>ABC123</barcode>`, `<qrcode size='20'>https://…</qrcode>`.
- Image : `<img>…hex…</img>` (générée par l'app, inutile à écrire à la main).

## 5. Liens `btprint:`

Depuis un navigateur (clic utilisateur requis) :

```html
<a href="btprint:text,%5BC%5D%3Cb%3EBonjour%3C%2Fb%3E%0A">Imprimer</a>
<a href="btprint:base64,G0AbYQFCb25qb3VyCh1WQgA=">Imprimer ESC/POS brut</a>
```

- `btprint:text,<texte formaté encodé en URL>`
- `btprint:base64,<octets ESC/POS en base64>`

Pratique pour les petits tickets ; au-delà de quelques Ko, préférer le serveur HTTP.

## 6. Intégration Odoo POS (piste)

Odoo POS génère déjà le ticket en image pour les imprimantes ePOS / IoT.
Un petit module Odoo 19 peut surcharger l'imprimante du POS pour envoyer cette image
en `POST /print/image` vers `http://127.0.0.1:8085`. Le POS étant en HTTPS :

- Chrome considère `127.0.0.1` comme une origine sûre : pas de blocage « mixed content ».
- Les versions récentes de Chrome demandent une autorisation **« Accès au réseau local »**
  la première fois ; l'utilisateur doit l'accepter pour le site Odoo.

## 7. Limites et améliorations possibles

- Imprimantes Bluetooth **Classic (SPP)** uniquement ; les modèles BLE-only demandent un transport GATT dédié.
- Les imprimantes **Star** (protocole StarPRNT) ne sont pas compatibles ESC/POS.
- Le jeton protège contre les pages et applications tierces du téléphone, pas contre
  quelqu'un qui a l'appareil en main (le jeton est lisible dans les réglages).
- Idées : choix du port dans l'UI, plusieurs imprimantes (cuisine / caisse), tiroir-caisse
  (`printFormattedTextAndOpenCashBox`), réimpression du dernier ticket, impression TCP (port 9100).
