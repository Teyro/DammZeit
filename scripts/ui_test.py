#!/usr/bin/env python3
"""Oberflächentest im CI-Emulator (nur für den manuell gestarteten Workflow screenshots.yml):
startet DammZeit, bedient die wichtigsten Funktionen und sammelt Screenshots."""
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PAKET = "de.oejendorferdamm.dammzeit"
ORDNER = "screenshots"


def adb(*args, check=False):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True)


def screenshot(name):
    daten = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    with open(os.path.join(ORDNER, name), "wb") as f:
        f.write(daten)
    print("Screenshot:", name)


def baum():
    for _ in range(4):
        if adb("shell", "uiautomator", "dump", "/sdcard/d.xml").returncode == 0 and \
                adb("pull", "/sdcard/d.xml", "d.xml").returncode == 0:
            try:
                return ET.parse("d.xml")
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return None


def tippe(*namen):
    """Tippt auf das erste Element, dessen Text oder Beschreibung einem der Namen entspricht."""
    b = baum()
    if b is None:
        print("WARNUNG: Oberfläche nicht lesbar", file=sys.stderr)
        return False
    for knoten in b.iter("node"):
        if (knoten.get("content-desc") in namen) or (knoten.get("text") in namen):
            z = [int(v) for v in knoten.get("bounds").replace("][", ",").strip("[]").split(",")]
            adb("shell", "input", "tap", str((z[0] + z[2]) // 2), str((z[1] + z[3]) // 2))
            return True
    print("WARNUNG: nicht gefunden:", namen, file=sys.stderr)
    return False


def hoehe():
    ausgabe = adb("shell", "wm", "size").stdout
    zeilen = [z for z in ausgabe.splitlines() if ":" in z]
    return int(zeilen[-1].split(":")[1].strip().split("x")[1])


def tippe_scrollend(*namen):
    """Wie tippe(), scrollt aber (am linken Rand, nicht übers Zifferblatt) nach unten, bis das
    Element vollständig oberhalb der Navigationsleiste sichtbar ist."""
    h = hoehe()
    for _ in range(8):
        b = baum()
        treffer = None
        if b is not None:
            for k in b.iter("node"):
                if (k.get("content-desc") in namen) or (k.get("text") in namen):
                    treffer = k
                    break
        if treffer is not None:
            z = [int(v) for v in treffer.get("bounds").replace("][", ",").strip("[]").split(",")]
            mitte = (z[1] + z[3]) // 2
            if mitte < h * 0.9:
                adb("shell", "input", "tap", str((z[0] + z[2]) // 2), str(mitte))
                return True
        adb("shell", "input", "swipe", "40", str(int(h * 0.75)), "40", str(int(h * 0.35)), "400")
        time.sleep(2.5)  # Nachlauf abwarten – ein Tippen während des Scrollens stoppt nur
    print("WARNUNG: auch nach Scrollen nicht gefunden:", namen, file=sys.stderr)
    screenshot("fehlt_" + namen[0].replace(" ", "_").replace("×", "x") + ".png")
    return False


def laeuft():
    r = adb("shell", "pidof", PAKET)
    return r.returncode == 0 and r.stdout.strip() != ""


def logcat():
    with open("logcat.txt", "w") as f:
        subprocess.run(["adb", "logcat", "-d"], stdout=f)


def main():
    os.makedirs(ORDNER, exist_ok=True)
    adb("shell", "pm", "grant", PAKET, "android.permission.POST_NOTIFICATIONS")
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(5)
    if not laeuft():
        print("FEHLER: App läuft nach dem Start nicht", file=sys.stderr)
        logcat()
        sys.exit(1)
    screenshot("01_liste.png")

    # Großansicht und Start
    tippe("Timer 5 Minuten-Timer")
    time.sleep(1.5)
    screenshot("02_ansicht.png")
    tippe("Start")
    time.sleep(4)
    screenshot("03_laeuft.png")
    tippe("Bearbeiten")
    time.sleep(1.5)
    screenshot("04_bearbeiten.png")
    tippe("Abbrechen")
    time.sleep(1)
    tippe("Zurück")
    time.sleep(1)

    # Neuer 10-Sekunden-Timer, bis zum Klingeln laufen lassen
    tippe_scrollend("Neuer Timer")
    time.sleep(1.5)
    tippe_scrollend("1 Min")
    time.sleep(0.5)
    adb("shell", "input", "swipe", "40", "900", "40", "1700", "300")
    time.sleep(2.5)
    for _ in range(5):
        tippe_scrollend("− 10 Sek")
        time.sleep(0.3)
    screenshot("05_neu.png")
    tippe_scrollend("Speichern")
    time.sleep(1.5)
    tippe("Start")
    time.sleep(14)
    screenshot("06_zeit_ist_um.png")
    tippe("Ton stoppen")
    time.sleep(1)
    screenshot("07_gestoppt.png")

    # Den 5-Minuten-Timer wieder anzeigen (für die Widgets) und Einstellungen öffnen
    tippe("Zurück")
    time.sleep(1)
    tippe("Timer 5 Minuten-Timer")
    time.sleep(1)
    tippe("Zurück")
    time.sleep(1)
    tippe("Einstellungen")
    time.sleep(1.5)
    screenshot("08_einstellungen.png")

    # Widgets auf den Startbildschirm legen
    for knopf in ("Widget 2 × 2", "Widget 4 × 4"):
        if tippe_scrollend(knopf):
            time.sleep(2)
            screenshot("09_widget_dialog.png")
            tippe("Add to Home screen", "ADD TO HOME SCREEN", "Add automatically", "ADD AUTOMATICALLY", "Add", "ADD", "Automatisch hinzufügen", "Hinzufügen")
            time.sleep(2)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(4)
    screenshot("10_startbildschirm.png")
    time.sleep(12)
    screenshot("11_startbildschirm_spaeter.png")

    if not laeuft():
        print("FEHLER: App ist abgestürzt", file=sys.stderr)
        logcat()
        sys.exit(1)
    logcat()


if __name__ == "__main__":
    main()
