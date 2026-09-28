# DammZeit

Ein visueller Timer für Android – entwickelt für die **Schule Öjendorfer Damm**,
angelehnt an den bekannten *Time Timer*: Die rote Scheibe zeigt, wie viel Zeit
noch bleibt, und schrumpft in Echtzeit bis zur 0.

## Funktionen

- **Zifferblatt wie beim Time Timer**: 0 oben, Minuten gegen den Uhrzeigersinn,
  die farbige Scheibe läuft flüssig und sekundengenau herunter. Zeit einstellen
  durch Ziehen am Zifferblatt, mit Vorgaben (1–90 Minuten) oder ±1 Minute/±10
  Sekunden
- **Mehrere Timer** mit eigenem Namen, eigener Dauer, eigenem Zifferblatt
  (5 bis 120 Minuten) – mehrere können gleichzeitig laufen
- **Farben**: klassisch Rot, Rot mit Verlauf ins Dunkelrote in den letzten
  Minuten, Ampel (grün → gelb → rot), Blau, Grün, Lila
- **Signaltöne**: Gong, Glocke, Klangschale, Piepen, Wecker, Wecker-Ton des
  Geräts oder still – alle Töne werden von der App selbst erzeugt, zum Probehören
  im Bearbeiten-Menü. Klingeldauer und Vibration einstellbar
- **Pünktlich auch im Hintergrund**: Der Timer läuft über die Uhrzeit des Geräts,
  klingelt auch bei geschlossener App und übersteht einen Neustart
- **Widgets 2 × 2 und 4 × 4** für den Startbildschirm: Zifferblatt mit live
  herunterzählender Restzeit, das große Widget mit Start/Pause und Zurücksetzen
- **Automatische Updates** direkt aus den GitHub-Releases, mit Prüfung der
  Signatur vor der Installation
- Komplett auf Deutsch, ohne Werbung, ohne Konto, ohne Datensammlung

## Installation

Die aktuelle `DammZeit.apk` liegt unter
[Releases](https://github.com/Teyro/DammZeit/releases/latest). Ab Android 8.0,
optimiert für Android 12 und neuer. Updates kommen danach über die App selbst
(roter Punkt am Zahnrad).

## Technik

Kotlin + Jetpack Compose. Das Zifferblatt wird mit `android.graphics`
gezeichnet und von App und Widgets gemeinsam genutzt. Wecker über
`AlarmManager.setAlarmClock`, Klingeln als Vordergrunddienst.

## Lizenz

DammZeit ist freie Software: Du kannst sie unter den Bedingungen der
**GNU General Public License, Version 3** (oder jeder späteren Version)
weitergeben und verändern – siehe [LICENSE](LICENSE).
