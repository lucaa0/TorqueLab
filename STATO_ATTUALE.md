# Stato di AutoToolPro — 20 ottobre 2026

- Consultazione del database tecnico e ricerca offline.
- Diagnosi OBD generica tramite adattatore Bluetooth compatibile.
- Creazione e salvataggio del rapporto OBD.
- Prototipo Ford con PATS e odometro simulati, esiti di prova e annullamento.

APK: AutoToolPro_prototipo_attivo_2026-10-20.apk, nella cartella app diego.
Compilazione e controlli Android superati; firma APK verificata.
PATS e odometro usano dati fittizi. Prove su telefono e veicoli da eseguire.

Test JVM nel progetto: `gradlew testDebugUnitTest` (parsing ELM327 e FordCore,
22 test su risposte sintetiche). Tensione nei dati live: PID 42, altrimenti ATRV.

Dettagli in PROTOTIPO.md, FORD_INTEGRAZIONE.md e RAPPORTO_OBD.md.
