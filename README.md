# DammZeit

Ein visueller Timer für Android – entwickelt für die **Schule Öjendorfer Damm**,
angelehnt an den bekannten *Time Timer*: Die rote Scheibe zeigt, wie viel Zeit
noch bleibt, und schrumpft in Echtzeit bis zur 0.

## Funktionen

Gemacht für **Touch-Boards (CleverTouch, CTOUCH) im Querformat**, Full HD und 4K –
nicht für Handys.

- **Nur die Uhr und ein Zahnrad**: Die Uhr füllt den Bildschirm. An der Scheibe ziehen
  stellt die Zeit ein (wie beim echten Time Timer), die Uhr läuft danach sofort los.
  Kurz antippen: anhalten, weiter – und wenn sie klingelt, den Ton stoppen
- **Echtzeit**: Die farbige Scheibe schrumpft flüssig und sekundengenau bis zur 0
- **Im Zahnrad alles einstellbar**: Farbe der Scheibe (klassisch rot, rot mit Verlauf ins
  Dunkelrote, Ampel grün → gelb → rot oder 12 eigene Farben), Hintergrund (hell, grau,
  dunkel, schwarz), Zifferblatt 5 bis 120 Minuten, Zahlen, Überschrift, Restzeit als Zahl,
  Klingelton (Gong, Glocke, Klangschale, Piepen, Wecker, Gerätewecker, still – mit
  Probehören), Klingeldauer bis „bis zum Antippen“, Lautstärke, sofort starten,
  Bildschirm anlassen
- **Widgets 2 × 2 und 4 × 4**: nur die runde Uhr, antippen startet oder hält sie an
- **Pünktlich auch im Hintergrund** und nach einem Neustart des Boards
- **Automatische Updates** direkt aus den GitHub-Releases, mit Signaturprüfung
- Die Oberfläche wird proportional zur Bildschirmbreite gezeichnet – auf einem 4K-Board
  genauso groß wie auf Full HD, egal welche Pixeldichte das Board meldet

## Technik

Kotlin + Jetpack Compose. Das Zifferblatt wird mit `android.graphics`
gezeichnet und von App und Widgets gemeinsam genutzt. Wecker über
`AlarmManager.setAlarmClock`, Klingeln als Vordergrunddienst.

## Lizenz

DammZeit ist freie Software: Du kannst sie unter den Bedingungen der
**GNU General Public License, Version 3** (oder jeder späteren Version)
weitergeben und verändern – siehe [LICENSE](LICENSE).
