# BT Print — impression Bluetooth ESC/POS (type RawBT)

Application Android (Kotlin, **Android 15 minimum**, minSdk 35) qui imprime sur des imprimantes thermiques
Bluetooth ESC/POS (Xprinter, Goojprt, MTP, Sunmi externes, etc.) depuis :

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
4. Brancher un téléphone Android 15+ et lancer *Run*.

APK de production : *Build → Generate Signed App Bundle / APK*.

## 2. Première utilisation

1. Appairer l'imprimante dans **Réglages → Bluetooth** (code PIN souvent `0000` ou `1234`).
2. Ouvrir BT Print, accepter les autorisations (Bluetooth + notifications).
3. Choisir l'imprimante, la largeur de papier (58 / 80 mm), l'encodage.
4. Bouton **Ticket test** : vérifie alignements, accents, code-barres, QR code.
5. Bouton **Image test** : vérifie le rendu image et le tramage.

> **Accents** : si « é » ou « € » sortent mal, changer l'encodage (WPC1252 → PC858 → PC850).
> La table exacte dépend du firmware de l'imprimante.

## 3. Structure du projet

```
app/src/main/java/com/odootogo/btprint/
├── data/PrinterSettings.kt       Réglages (imprimante, papier, encodage, coupe, serveur)
├── print/
│   ├── PrintJob.kt               Types de travaux : texte, ESC/POS brut, image, PDF
│   ├── PrintQueue.kt             File d'attente globale + statut observable
│   ├── PrinterEngine.kt          Connexion Bluetooth SPP + envoi via DantSu
│   ├── ImageUtils.kt             Redimensionnement, tramage Floyd–Steinberg, rendu PDF, rognage
│   └── TestReceipts.kt           Ticket et image de test
├── service/PrintService.kt       Service au premier plan (connectedDevice) : file + serveur
├── server/LocalPrintServer.kt    Serveur HTTP NanoHTTPD sur 127.0.0.1
└── ui/
    ├── MainActivity.kt           Écran de configuration (Jetpack Compose)
    └── ShareReceiverActivity.kt  Réception des partages et des liens btprint:
```

Fonctionnement : chaque entrée (UI, partage, HTTP) crée un `PrintJob` et le pousse dans `PrintQueue`.
`PrintService` consomme la file et appelle `PrinterEngine`, qui ouvre la connexion, imprime, puis ferme.
Les images et PDF sont redimensionnés à la largeur de tête (384 px en 58 mm, 576 px en 80 mm),
tramés en noir et blanc, puis envoyés par bandes de 256 px pour ne pas saturer le buffer.

## 4. API HTTP locale

Le serveur écoute uniquement sur `127.0.0.1` (non accessible depuis le réseau).
Toutes les réponses incluent les en-têtes CORS et `Access-Control-Allow-Private-Network`.

| Méthode | Route          | Corps                                                  |
|---------|----------------|--------------------------------------------------------|
| GET     | `/status`      | —  (renvoie imprimante, papier, statut)                |
| POST    | `/print/text`  | Texte formaté DantSu, UTF-8                            |
| POST    | `/print/raw`   | Octets ESC/POS en base64                               |
| POST    | `/print/image` | Image PNG/JPEG en base64 ou data URL                   |
| POST    | `/print/pdf`   | PDF en base64 ou data URL                              |

Réponse : `202 {"ok":true,"message":"Travail ajouté à la file"}`.

### Exemple JavaScript (depuis une page web sur le téléphone)

```js
const BT_PRINT = "http://127.0.0.1:8085";

// Texte formaté
await fetch(`${BT_PRINT}/print/text`, {
  method: "POST",
  headers: { "Content-Type": "text/plain" },
  body: "[C]<b>MA BOUTIQUE</b>\n[L]Article[R]1 500 FCFA\n",
});

// Image (ex. ticket rendu dans un <canvas>)
const dataUrl = canvas.toDataURL("image/png");
await fetch(`${BT_PRINT}/print/image`, {
  method: "POST",
  headers: { "Content-Type": "text/plain" },
  body: dataUrl,
});
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
- Le serveur local accepte toute page ouverte sur le téléphone : ajouter un jeton
  (`X-Print-Token`) si l'appareil sert à autre chose qu'une caisse.
- Idées : choix du port dans l'UI, plusieurs imprimantes (cuisine / caisse), tiroir-caisse
  (`printFormattedTextAndOpenCashBox`), réimpression du dernier ticket, impression TCP (port 9100).
