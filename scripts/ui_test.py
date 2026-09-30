#!/usr/bin/env python3
"""Oberflächentest im CI-Emulator (nur für den manuell gestarteten Workflow screenshots.yml):
bedient die Uhr wie an der Tafel und sammelt Screenshots."""
import math
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PAKET = "de.oejendorferdamm.dammzeit"
ORDNER = "screenshots"


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True)


def screenshot(name):
    daten = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    with open(os.path.join(ORDNER, name), "wb") as f:
        f.write(daten)
    print("Screenshot:", name)


def knoten(*namen):
    for _ in range(4):
        if adb("shell", "uiautomator", "dump", "/sdcard/d.xml").returncode == 0 and \
                adb("pull", "/sdcard/d.xml", "d.xml").returncode == 0:
            try:
                for k in ET.parse("d.xml").iter("node"):
                    if k.get("content-desc") in namen or k.get("text") in namen:
                        return [int(v) for v in k.get("bounds").replace("][", ",").strip("[]").split(",")]
                return None
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return None


def tippe(*namen):
    z = knoten(*namen)
    if z is None:
        print("WARNUNG: nicht gefunden:", namen, file=sys.stderr)
        return False
    adb("shell", "input", "tap", str((z[0] + z[2]) // 2), str((z[1] + z[3]) // 2))
    return True


def scrolle_zu(*namen):
    for _ in range(8):
        z = knoten(*namen)
        if z is not None and (z[1] + z[3]) // 2 < 1300:
            return tippe(*namen)
        # im Einstellungsfeld (rechts) nach oben wischen
        adb("shell", "input", "swipe", "1750", "1200", "1750", "500", "400")
        time.sleep(2.5)
    print("WARNUNG: auch nach Scrollen nicht gefunden:", namen, file=sys.stderr)
    return False


def stelle_uhr(anteil):
    """Zieht an der Uhr von 12 Uhr aus gegen den Uhrzeigersinn auf den Anteil einer Runde."""
    z = knoten("Uhr")
    if z is None:
        print("WARNUNG: Uhr nicht gefunden", file=sys.stderr)
        return
    cx, cy = (z[0] + z[2]) / 2, (z[1] + z[3]) / 2
    r = min(z[2] - z[0], z[3] - z[1]) * 0.25
    start = (cx - 30, cy - r)
    winkel = math.radians(-90 - 360 * anteil)
    ziel = (cx + r * math.cos(winkel), cy + r * math.sin(winkel))
    adb("shell", "input", "swipe", str(int(start[0])), str(int(start[1])), str(int(ziel[0])), str(int(ziel[1])), "600")


def laeuft():
    r = adb("shell", "pidof", PAKET)
    return r.returncode == 0 and r.stdout.strip() != ""


def main():
    os.makedirs(ORDNER, exist_ok=True)
    adb("shell", "pm", "grant", PAKET, "android.permission.POST_NOTIFICATIONS")
    # Den einmaligen Hinweis "Viewing full screen" abschalten, sonst verdeckt er die App.
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(5)
    if not laeuft():
        print("FEHLER: App läuft nach dem Start nicht", file=sys.stderr)
        sys.exit(1)
    screenshot("01_start.png")

    stelle_uhr(14 / 60)  # 14 Minuten, startet sofort
    time.sleep(3)
    screenshot("02_14_minuten_laeuft.png")
    tippe("Pause")  # Startknopf: Pause
    time.sleep(1.5)
    screenshot("03_pausiert.png")
    tippe("Weiter")
    time.sleep(1)

    tippe("Einstellungen")
    time.sleep(2)
    screenshot("04_einstellungen.png")
    tippe("Dunkel")
    time.sleep(1)
    tippe("Ampel: grün, gelb, rot")
    time.sleep(1)
    screenshot("05_dunkel_ampel.png")
    scrolle_zu("5 Min")  # 5-Minuten-Zifferblatt
    time.sleep(1)
    screenshot("06_einstellungen_zifferblatt.png")
    tippe("Einstellungen schließen")
    time.sleep(2)

    stelle_uhr(0.06)  # 15 Sekunden auf dem 5-Minuten-Zifferblatt
    time.sleep(2)
    screenshot("07_kurz_laeuft.png")
    time.sleep(18)
    screenshot("08_zeit_ist_um.png")
    tippe("Uhr")  # Ton stoppen
    time.sleep(2)
    screenshot("09_gestoppt.png")

    tippe("Start")
    time.sleep(2)
    screenshot("10_start_knopf.png")
    tippe("Pause")
    time.sleep(1)
    tippe("Zurücksetzen")
    time.sleep(0.25)
    screenshot("11_zuruecksetzen_gleitet.png")
    time.sleep(2)
    screenshot("12_zurueckgesetzt.png")

    if not laeuft():
        print("FEHLER: App ist abgestürzt", file=sys.stderr)
    with open("logcat.txt", "w") as f:
        subprocess.run(["adb", "logcat", "-d"], stdout=f)


if __name__ == "__main__":
    main()
