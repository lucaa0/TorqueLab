# AUTO TOOL PRO 1000 – App Android, fase 1

Consultazione offline del database tecnico `autotool.db`, in sola lettura.

## Cosa fa questa versione (0.1.0-fase1)
- Copia `autotool.db` dall'APK nello storage privato dell'app, senza conversioni; lo ricopia solo quando l'app viene aggiornata.
- Apre il database in sola lettura con SQLite incluso nell'app (`androidx.sqlite:sqlite-bundled`), quindi FTS5 e JSON non dipendono dalla versione di Android.
- Elenca tabelle, viste e indici di ricerca con il numero di righe (lo schema è letto dal database, non è scritto nel codice).
- Sfoglia ogni tabella o vista a pagine da 50 righe, con filtro testuale su tutte le colonne.
- Ricerca globale sugli indici FTS (ogni parola cercata come prefisso).
- Dettaglio riga: tutte le colonne, valori selezionabili, etichetta di provenienza "Dal database", campi vuoti mostrati come tali.
- Schermata Informazioni: versione SQLite, FTS5 e JSON disponibili sul dispositivo, dimensione e SHA-256 del database.
- Diagnosi OBD generica via Bluetooth, con relativo permesso su Android12 e successivi.

## Come ottenere l'APK
1. Copiare il database in `app/src/main/assets/autotool.db`.
2. Una delle due strade:
   - **GitHub**: caricare la cartella in un repository privato. Il workflow `Build APK` compila a ogni push; l'APK si scarica dalla scheda *Actions → ultima esecuzione → Artifacts*. Se il database supera 100 MB usare Git LFS.
   - **Android Studio** (Ladybug o successivo): *Open* sulla cartella, poi *Build → Build App Bundle(s)/APK(s) → Build APK(s)*. Da terminale: `./gradlew assembleDebug`.
3. L'APK si trova in `app/build/outputs/apk/debug/app-debug.apk`.

## Struttura
```
app/src/main/java/it/autotoolpro/obd/
  MainActivity.kt          avvio, tema
  AppViewModel.kt          apertura database, navigazione
  data/TechDatabase.kt     copia dagli asset, apertura sola lettura, query, ricerca FTS
  ui/                      schermate Compose (Home, Tabella, Dettaglio, Ricerca, Info)
```

## Da verificare su dispositivo reale
- FTS5 e JSON attivi (schermata Informazioni).
- Tempi di apertura e conteggio righe su dispositivi lenti.

## Configurazione
Kotlin 2.0.21, AGP 8.7.3, Gradle 8.11.1, Compose BOM 2024.12, compileSdk/targetSdk 35, minSdk 26 (da confermare), JDK 17.

## Funzioni Ford — 20 ottobre 2026

Home → Ford include i profili Fiesta e Puma e il prototipo interattivo di PATS
e odometro. I dati sono simulati; sono disponibili scenari di successo, errore
e annullamento. Dettagli in [PROTOTIPO.md](PROTOTIPO.md) e
[FORD_INTEGRAZIONE.md](FORD_INTEGRAZIONE.md).

## Rapporto OBD

In Diagnosi auto seleziona Fiesta/Puma, crea il rapporto e salvalo dal telefono.
Dettagli in [RAPPORTO_OBD.md](RAPPORTO_OBD.md). La funzione raccoglie informazioni
OBD generiche disponibili; PATS e odometro operativi restano incompleti.
