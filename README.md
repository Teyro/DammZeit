# DammZeit

Ein visueller Timer für Android – entwickelt für die **Schule Öjendorfer Damm**,
angelehnt an den bekannten *Time Timer*: Die rote Scheibe zeigt, wie viel Zeit
noch bleibt, und schrumpft in Echtzeit bis zur 0.

## Funktionen

Gemacht für **Touch-Boards (CleverTouch, CTOUCH) im Querformat**, Full HD und 4K –
nicht für Handys.

- **Die Uhr, ein Startknopf und ein Zahnrad**: Die Uhr füllt den Bildschirm. An der Scheibe
  ziehen stellt die Zeit ein (wie beim echten Time Timer). Der große Knopf darunter zeigt,
  was als Nächstes geht: Start, Pause, Weiter – und wenn die Zeit um ist, Stopp. Daneben
  erscheint bei Bedarf „Zurücksetzen“. Antippen der Uhr wirkt wie der Knopf.
- **Flüssige Optik**: Scheibe mit Tiefe, Glanz und weichem Schatten; beim Zurücksetzen
  gleitet sie federnd an ihren Platz, beim Klingeln pulsiert ein Leuchten hinter der Uhr
- **Echtzeit**: Die farbige Scheibe schrumpft flüssig und sekundengenau bis zur 0
- **Im Zahnrad alles einstellbar**: Farbe der Scheibe (klassisch rot, rot mit Verlauf ins
  Dunkelrote, Ampel grün → gelb → rot oder 12 eigene Farben), Hintergrund (hell, grau,
  dunkel, schwarz), Zifferblatt 5 bis 120 Minuten, Zahlen, Überschrift, Restzeit als Zahl,
  Klingelton (Gong, Glocke, Klangschale, Piepen, Wecker, Gerätewecker, still – mit
  Probehören), Klingeldauer bis „bis zum Antippen“, Lautstärke, sofort starten,
  Bildschirm anlassen
- **Widgets 2 × 2 und 4 × 4**: die Uhr wie in der App mit großen Zahlen (auch von hinten im Raum lesbar), die Restzeit läuft sekundengenau auf der Scheibe mit; unten links Zurücksetzen, unten rechts Start/Pause/Stopp, oben rechts „Zeit einstellen“ (Auswahlfenster mit 1–60 Minuten oder eigener Zeit)
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
