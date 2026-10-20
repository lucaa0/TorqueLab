# Funzioni Ford di AutoToolPro

In Home → Ford sono disponibili i profili Fiesta 1.6 diesel 2010 e
Puma coupé Mk1 1.4 benzina 1998.

## Prototipo

La schermata permette di selezionare il veicolo, simulare la lettura odometro
e il flusso PATS, scegliere un esito positivo o un errore e annullare l’operazione.
I chilometri e il numero di chiavi mostrati sono dati fittizi.
Queste funzioni non comunicano con le centraline.
Istruzioni e percorso di prova in [PROTOTIPO.md](PROTOTIPO.md).

## Collegamento reale

La diagnosi OBD via Bluetooth permette letture generiche supportate dal veicolo
e dall’adattatore. È disponibile la creazione e il salvataggio del rapporto.
Istruzioni in [RAPPORTO_OBD.md](RAPPORTO_OBD.md).

PATS e odometro reali richiedono ancora implementazione e validazione sui moduli
corretti. Non sono stati verificati su telefono o auto.

## Limiti fissi

L'odometro è solo in lettura. L'app non modificherà mai il chilometraggio:
alterarlo è illegale (frode in commercio), anche se presentato come correzione.

## Adattatore: da verificare prima di proseguire

Ipotesi da confermare con il rapporto OBD reale di ciascuna auto:
- Puma 1998: probabile protocollo J1850 PWM (Ford EEC-V). Molti ELM327
  economici/cloni non lo supportano; in quel caso la diagnosi generica fallisce.
- Fiesta 2010: diagnosi generica su CAN standard. Quadro strumenti e PATS
  probabilmente su MS-CAN, che richiede un adattatore con commutatore HS/MS-CAN.
Il campo «protocollo dichiarato» (ATDP/ATDPN) nel rapporto aiuta a chiarirlo.
