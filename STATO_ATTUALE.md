# Stato di AutoToolPro — 20 ottobre 2026

- Consultazione del database tecnico e ricerca offline.
- Diagnosi OBD generica tramite adattatore Bluetooth compatibile.
- Creazione e salvataggio del rapporto OBD.
- Prototipo Ford con PATS e odometro simulati, esiti di prova e annullamento.

APK: AutoToolPro_storico_2026-10-20.apk, nella cartella app diego
(SHA256 6530606e5233276d3f7d8ba6becb9879b7c425d17e8a767ba39869455a01c1b3).
Compilazione e controlli Android superati; firma APK verificata.
PATS e odometro usano dati fittizi. Prove su telefono e veicoli da eseguire.

Diagnosi OBD, «Leggi codici e stato» (tutto in sola lettura, OBD standard):
spia motore e monitor di prontezza (PID 01), km con spia accesa e dalla
cancellazione codici (PID 21/31/30/4E), codici memorizzati e in attesa,
freeze frame con i valori al momento del guasto (mode 02).
Dati live: solo i PID dichiarati dall'auto (0100/0120/0140), fino a 16 valori;
tensione da PID 42, altrimenti ATRV. Errori di singole letture non chiudono più
il collegamento. Cancellazione codici volutamente assente.

Storico letture: ogni «Leggi codici e stato» è salvato sul telefono (ultime 200),
con l'auto indicata dall'utente e i codici comparsi/scomparsi rispetto alla
lettura precedente della stessa auto. Cancellazione dello storico con conferma.

Test JVM nel progetto: `gradlew testDebugUnitTest` (parsing ELM327, decodifica
OBD, storico e FordCore, 39 test su risposte sintetiche). Nessuna prova su auto reale.

Dettagli in PROTOTIPO.md, FORD_INTEGRAZIONE.md e RAPPORTO_OBD.md.
