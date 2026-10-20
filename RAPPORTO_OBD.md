# Raccolta del rapporto OBD

Questa versione aggiunge la raccolta delle risposte dell'adattatore e delle
informazioni OBD generiche disponibili sull'auto. Il rapporto viene creato solo
quando il telefono è collegato all'adattatore e l'utente avvia la raccolta.
Non contiene dati inventati o ricavati dai profili del catalogo.

1. Installa AutoToolPro_Ford_report_2026-10-15.apk sul telefono.
2. Collega l'adattatore OBD e usa il Bluetooth classico già previsto dall'app.
3. Apri Diagnosi auto (anche dalla schermata Ford) e scegli l'adattatore abbinato.
4. In «Identifica adattatore e auto» seleziona Fiesta2010 o Puma1998.
5. Premi «Crea rapporto di identificazione» e attendi il termine.
6. Premi «Salva rapporto» e scegli dove salvare il file.txt.
7. Ripeti sull'altra auto, scegliendo il relativo profilo.

L'auto selezionata è un'etichetta fornita dall'utente: non è riconoscimento VIN.
La raccolta conserva risposte testuali con intestazioni dove l'adattatore le
supporta: versione/descrizione ELM, protocollo dichiarato, tensione, risposte a
0100,0900,0902,0904 e090A. Non decodifica arbitrariamente messaggi di più ECU.
VIN e calibrazione potrebbero mancare, specialmente su auto meno recenti.
Non è una scansione completa di tutte le centraline Ford.

Sono conservati NO DATA, ?, risposte negative e cause di interruzione.
Un rapporto parziale può essere salvato anche dopo la disconnessione.
L'esportazione usa il selettore file Android; non invia automaticamente il file.
Il file può contenere VIN o altri identificativi effettivamente restituiti.

PATS e odometro operativi restano incompleti. La raccolta non modifica chiavi,
chilometraggio o firmware e non contiene richieste di accesso protetto.
Il rapporto è un input utile per l'analisi successiva, non prova di compatibilità.

## Verifiche della versione

APK compilato, firma debug verificata, lintDebug con zero errori e nove warning.
Test JVM sui sorgenti effettivi ObdReport.kt/Elm327.kt con risposte e socket
simulati: handshake, sequenza, esclusione letture concorrenti, NO DATA, risposta
incompleta, dimensioni, errori header, disconnessione e cancellazione passati.
Nessuna verifica con telefono, esportazione Android reale, adattatore o veicolo.
Il database incluso nell'APK conserva il digest dell'asset originale.

## Fonti di riferimento

- [ELM327 datasheet](https://elmelectronics.com/wp-content/uploads/2020/05/ELM327DSL.pdf)
- [Selettore documenti Android](https://developer.android.com/training/data-storage/shared/documents-files)
