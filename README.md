# Ordner – Dateimanager & Transfer für Android und Android TV

Kotlin + Jetpack Compose, eine Codebasis für Handy und Smart-TV (Fernbedienung/D-Pad wird unterstützt).

## Funktionen (Stand 0.1.0)

- **Dateimanager**: Ordner durchsuchen, Mehrfachauswahl, Kopieren, Ausschneiden, Einfügen, Umbenennen, Löschen, neuer Ordner, Dateien öffnen
- **Transfer WLAN/LAN**: Gerät-zu-Gerät mit eigener App auf beiden Seiten. Geräte werden automatisch gefunden (mDNS), alternativ Eingabe der IP-Adresse. Der Empfänger muss jede Übertragung bestätigen. Empfangene Dateien landen in `Download/Ordner`.
- **Bluetooth**: Versand über die Android-Freigabe, Kopplung über die Systemeinstellungen
- **Externe Laufwerke**: USB-Sticks, SD-Karten und Festplatten werden erkannt und erscheinen automatisch, sobald Android sie eingebunden hat. Speicherplatz wird angezeigt.

## Grenzen von Android

- Apps dürfen Laufwerke **nicht selbst formatieren oder aushängen**. Ordner öffnet dafür die Speicher-Einstellungen des Systems.
- Welche Dateisysteme (FAT32, exFAT, NTFS, ext4) gelesen werden, bestimmt das Gerät, nicht die App.
- Bluetooth-Versand hängt vom Hersteller ab; viele TVs können Dateien nur empfangen.
- Der Empfang läuft, solange die App geöffnet ist (noch kein Hintergrunddienst).

## APK bauen

**Variante A – Android Studio:** Ordner öffnen, warten bis Gradle synchronisiert hat, dann *Build → Build APK(s)*.
Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`

**Variante B – GitHub Actions:** Projekt in ein GitHub-Repository pushen (Branch `main`). Der Workflow `.github/workflows/build-apk.yml` baut automatisch und stellt das APK unter *Actions → Artifacts* bereit.

**Variante C – Kommandozeile** (JDK 17 + Android SDK nötig): `./gradlew assembleDebug`

## Installation

- **Handy**: APK übertragen, „Unbekannte Quellen" erlauben, installieren.
- **Android TV**: `adb connect <TV-IP>` und `adb install app-debug.apk`, oder APK per USB-Stick und einen Dateimanager installieren.
- Beim ersten Start „Zugriff auf alle Dateien" erlauben. Fehlt der Dialog auf dem TV:
  `adb shell appops set com.ordner.app MANAGE_EXTERNAL_STORAGE allow`

## Projektaufbau

| Datei | Aufgabe |
|---|---|
| `MainActivity.kt` | Einstieg, Berechtigung, Laufwerks-Broadcasts |
| `AppViewModel.kt` | Zustand, Dateiaktionen |
| `Storage.kt` | Speicher und USB-Geräte erkennen |
| `FileOps.kt` | Kopieren, Verschieben, sichere Dateinamen |
| `TransferManager.kt` | TCP-Transfer, mDNS-Erkennung, Bestätigungsdialog |
| `BluetoothShare.kt` | Bluetooth über System-Freigabe |
| `FilesScreen.kt`, `TransferScreen.kt`, `StorageScreen.kt` | Oberfläche (Tabs: Dateien · Übertragen · Speicher) |

## Nächste Schritte (Vorschlag)

1. Empfang als Vordergrunddienst mit Benachrichtigung
2. Verschlüsselte Übertragung und Kopplungs-PIN
3. Zugriff auf NAS/PC-Freigaben (SMB)
4. Bluetooth-Transfer mit eigenem Protokoll
5. Vorschaubilder, Suche, Sortierung
