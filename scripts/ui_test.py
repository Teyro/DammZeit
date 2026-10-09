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


def mitte(r):
    return (r[0] + r[2]) // 2, (r[1] + r[3]) // 2


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))


def alle_grenzen(*namen):
    ergebnis = []
    for _ in range(3):
        if adb("shell", "uiautomator", "dump", "/sdcard/d.xml").returncode == 0 and adb("pull", "/sdcard/d.xml", "d.xml").returncode == 0:
            try:
                for k in ET.parse("d.xml").iter("node"):
                    if k.get("content-desc") in namen or k.get("text") in namen:
                        ergebnis.append([int(v) for v in k.get("bounds").replace("][", ",").strip("[]").split(",")])
                return ergebnis
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return ergebnis


def alle_grenzen_klasse(klasse):
    ergebnis = []
    if adb("shell", "uiautomator", "dump", "/sdcard/d.xml").returncode == 0 and adb("pull", "/sdcard/d.xml", "d.xml").returncode == 0:
        try:
            for k in ET.parse("d.xml").iter("node"):
                if k.get("class") == klasse:
                    ergebnis.append([int(v) for v in k.get("bounds").replace("][", ",").strip("[]").split(",")])
        except ET.ParseError:
            pass
    return ergebnis


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

    # Widgets (Vorschau-Seite aus dem Test-Build, echte RemoteViews mit echten Knöpfen)
    adb("shell", "am", "start", "-n", f"{PAKET}/.WidgetTestActivity")
    time.sleep(3)
    screenshot("13_widgets.png")
    tippe("Start oder Pause")
    time.sleep(4)
    screenshot("14_widgets_laeuft.png")
    tippe("Zeit einstellen")
    time.sleep(3)
    screenshot("15_zeit_einstellen.png")
    tippe("5 Minuten")
    time.sleep(3)
    adb("shell", "am", "start", "-n", f"{PAKET}/.WidgetTestActivity")
    time.sleep(3)
    screenshot("16_widgets_5_minuten.png")
    tippe("Zurücksetzen")
    time.sleep(2)
    screenshot("17_widgets_zurueckgesetzt.png")

    # Solange nichts läuft, Positionen merken – bei laufender Uhr wird uiautomator nie „ruhig“
    uhren = alle_grenzen("DammZeit-Uhr, antippen zum Starten oder Anhalten")
    s = knoten("Start oder Stopp")

    # Stoppuhr-Widget: starten, zwei Runden, stoppen, speichern
    if s:
        sx, sy = (s[0] + s[2]) // 2, (s[1] + s[3]) // 2
        d = (s[2] - s[0]) / 58  # Pixel je dp (Startknopf ist 58 dp breit)
        runde_x = sx + int(61 * d)  # Runde liegt rechts daneben, unsichtbar behält sie ihren Platz
        tap(sx, sy)
        time.sleep(2.5)
        tap(runde_x, sy)
        time.sleep(1.5)
        tap(runde_x, sy)
        time.sleep(1)
        screenshot("19_stoppuhr_widget_laeuft.png")
        tap(sx, sy)
        time.sleep(2)
        screenshot("19b_stoppuhr_widget_gestoppt.png")
    else:
        print("WARNUNG: Stoppuhr-Widget nicht gefunden", file=sys.stderr)
    tippe("Zeit speichern")
    time.sleep(3)
    felder = alle_grenzen_klasse("android.widget.EditText")
    if felder:
        tap(*mitte(felder[0]))
        adb("shell", "input", "text", "Lea")
    if len(felder) > 1:
        tap(*mitte(felder[1]))
        adb("shell", "input", "text", "Hampelmann")
    adb("shell", "input", "keyevent", "111")  # Tastatur zu
    time.sleep(1)
    screenshot("20_speichern_dialog.png")
    tippe("Speichern")
    time.sleep(2)
    screenshot("21_platz.png")

    # Stoppuhr in der App: drei weitere Kinder für ein volles Treppchen
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(3)
    tippe("Stoppuhr")
    time.sleep(2)
    knopf = {n: knoten(n) for n in ("Zurücksetzen", "Start", "Runde")}
    if all(knopf.values()):
        for name, sekunden in (("Ben", 2), ("Mia", 5), ("Tom", 3)):
            tap(*mitte(knopf["Zurücksetzen"]))
            time.sleep(1)
            tap(*mitte(knopf["Start"]))
            time.sleep(sekunden / 2)
            tap(*mitte(knopf["Runde"]))
            time.sleep(sekunden / 2)
            tap(*mitte(knopf["Runde"]))
            time.sleep(1)
            if name == "Mia":
                screenshot("22_stoppuhr_app_laeuft.png")
            tap(*mitte(knopf["Start"]))  # derselbe Knopf heißt jetzt „Stopp“
            time.sleep(1.5)
            tippe("Zeit speichern")
            time.sleep(2)
            felder = alle_grenzen_klasse("android.widget.EditText")
            if felder:
                tap(*mitte(felder[0]))
                adb("shell", "input", "text", name)
            adb("shell", "input", "keyevent", "111")
            time.sleep(1)
            tippe("Speichern")
            time.sleep(3)
            if name == "Tom":
                screenshot("23_bestenliste.png")
            adb("shell", "input", "keyevent", "4")  # Bestenliste schließen
            time.sleep(1.5)
    else:
        print("WARNUNG: Stoppuhr-Knöpfe nicht gefunden", knopf, file=sys.stderr)

    # Timer-Widget: auf die Zahl rechts neben der Mitte tippen
    if uhren:
        adb("shell", "am", "start", "-n", f"{PAKET}/.WidgetTestActivity")
        time.sleep(3)
        g = max(uhren, key=lambda r: (r[2] - r[0]))
        cx, cy, b = (g[0] + g[2]) // 2, (g[1] + g[3]) // 2, (g[2] - g[0])
        tap(cx + int(b * 0.41), cy)
        time.sleep(3)
        adb("shell", "am", "start", "-n", f"{PAKET}/.WidgetTestActivity")
        time.sleep(3)
        screenshot("24_widget_zahl_angetippt.png")

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

    # Widgets (Vorschau-Seite aus dem Test-Build, echte RemoteViews mit echten Knöpfen)
    adb("shell", "am", "start", "-n", f"{PAKET}/.WidgetTestActivity")
    time.sleep(3)
    screenshot("13_widgets.png")
    tippe("Start oder Pause")
    time.sleep(4)
    screenshot("14_widgets_laeuft.png")
    tippe("Zeit einstellen")
    time.sleep(3)
    screenshot("15_zeit_einstellen.png")
    tippe("5 Minuten")
    time.sleep(3)
    adb("shell", "am", "start", "-n", f"{PAKET}/.WidgetTestActivity")
    time.sleep(3)
    screenshot("16_widgets_5_minuten.png")
    tippe("Zurücksetzen")
    time.sleep(2)
    screenshot("17_widgets_zurueckgesetzt.png")

    # Timer-Widget: auf die Zahl rechts tippen (bei Uhrzeigersinn = 15 auf dem 60er-Zifferblatt)
    z = alle_grenzen("DammZeit-Uhr, antippen zum Starten oder Anhalten")
    if len(z) >= 2:
        g = max(z, key=lambda r: (r[2] - r[0]))
        cx, cy, b = (g[0] + g[2]) // 2, (g[1] + g[3]) // 2, (g[2] - g[0])
        adb("shell", "input", "tap", str(cx + int(b * 0.41)), str(cy))
        time.sleep(3)
        adb("shell", "am", "start", "-n", f"{PAKET}/.WidgetTestActivity")
        time.sleep(3)
        screenshot("18_widget_zahl_angetippt.png")

    # Stoppuhr-Widget: starten, Runde, stoppen, speichern
    tippe("Start oder Stopp")
    time.sleep(3)
    tippe("Runde")
    time.sleep(2)
    screenshot("19_stoppuhr_widget_laeuft.png")
    tippe("Start oder Stopp")
    time.sleep(2)
    tippe("Zeit speichern")
    time.sleep(3)
    felder = alle_grenzen_klasse("android.widget.EditText")
    if felder:
        f = felder[0]
        adb("shell", "input", "tap", str((f[0] + f[2]) // 2), str((f[1] + f[3]) // 2))
        adb("shell", "input", "text", "Lea")
    if len(felder) > 1:
        f = felder[1]
        adb("shell", "input", "tap", str((f[0] + f[2]) // 2), str((f[1] + f[3]) // 2))
        adb("shell", "input", "text", "Hampelmann")
    adb("shell", "input", "keyevent", "111")  # Tastatur zu
    time.sleep(1)
    screenshot("20_speichern_dialog.png")
    tippe("Speichern")
    time.sleep(2)
    screenshot("21_platz.png")

    # Stoppuhr in der App: drei weitere Kinder für ein volles Treppchen
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(3)
    tippe("Stoppuhr")
    time.sleep(2)
    for name, sekunden in (("Ben", 2), ("Mia", 5), ("Tom", 3)):
        tippe("Zurücksetzen")
        time.sleep(1)
        tippe("Start")
        time.sleep(sekunden / 2)
        tippe("Runde")
        time.sleep(sekunden / 2)
        if name == "Mia":
            screenshot("22_stoppuhr_app_laeuft.png")
        tippe("Stopp")
        time.sleep(1)
        tippe("Zeit speichern")
        time.sleep(2)
        felder = alle_grenzen_klasse("android.widget.EditText")
        if felder:
            f = felder[0]
            adb("shell", "input", "tap", str((f[0] + f[2]) // 2), str((f[1] + f[3]) // 2))
            adb("shell", "input", "text", name)
        adb("shell", "input", "keyevent", "111")
        time.sleep(1)
        tippe("Speichern")
        time.sleep(3)
    screenshot("23_bestenliste.png")

    if not laeuft():
        print("FEHLER: App ist abgestürzt", file=sys.stderr)
    with open("logcat.txt", "w") as f:
        subprocess.run(["adb", "logcat", "-d"], stdout=f)


if __name__ == "__main__":
    main()
