# Webhook re-notification service
Servizio che invia di nuovo, su richiesta di un operatore, gli avvisi webhook ai clienti per un elenco di documenti.
L'ho realizzato come esercizio tecnico; i requisiti sono in [`docs/requirements.md`](docs/requirements.md).

Il progetto è realizzato su Linux con tecnologia Java 25, Spring Boot 4 basato su architettura esagonale (https://alistair.cockburn.us/hexagonal-architecture).

Il progetto espone un nuovo endpoint `[POST] /v1/webhook/oneshot` che riceve una lista di oggetti secondo i [requisiti](docs/requirements.md). 

Per ognuno di questi oggetti, identifica versione e stato da notificare trovando la giusta configurazione webhook del cliente (vedi src/main/resources/dataset/dataset.json) ed accodando il messaggio su di una coda interna (simulata, in memoria).

## Architettura Esagonale
L’architettura esagonale propone un modello concentrico che pone al centro la logica di dominio, protetta da influenze esterne. 

Questa visione inside-out elimina la necessità di pensare in termini di 'sopra' e 'sotto' come nella classica struttura 'presentazione', 'business logic' e 'persistenza', 
concentrandosi invece sulla separazione netta tra il cuore dell’applicazione e il mondo esterno.

## Comandi
Essendo realizzato su Linux, i comandi sono script bash, utilizzabili su sistemi Unix-like, per un rapido avvio dell'ambiente di sviluppo, la verifica e la formattazione del codice.

I fine riga sono gestiti da .gitattributes: gli script bash e mvnw hanno sempre LF, mvnw.cmd CRLF, anche su un clone Windows. 

Su Windows gli script vanno lanciati da Git Bash, WSL o simili, perché PowerShell non esegue codice bash; in alternativa, da PowerShell o cmd con un JDK 25 attivo: mvnw.cmd clean verify.

---

### °°° Avvio del servizio locale °°°
Se il JDK attivo non è il 25, viene verificata la presenza di SDKMAN (https://sdkman.io/) sull'ambiente di lavoro, scaricata la versione di Java 25 se non ancora presente e configurata per l'uso all'interno di quel comando. 

Infine il servizio viene avviato sulla porta 8080.
```bash
./run.sh
```
```bash
curl -s -X POST http://localhost:8080/v1/webhook/oneshot \
  -H 'Content-Type: application/json' \
  -d '[ { "requestId": "D-001" }, { "requestId": "D-004", "containerStatus": "DELIVERED" }, { "requestId": "D-999" } ]'
```

In `docs/postman/` è stata preparata una collection Postman con 20 richieste (happy path, casi rari del dataset, errori su
elemento, richieste malformate, correlation id), ognuna con i controlli sulla risposta.

---

### °°° Verifica °°°
Un solo comando, che si occupa di eseguire un 'clean' e 'verify' del progetto.

Queste due fasi Maven eseguono tutto il ciclo di vita fino al packaging del jar; il JDK viene gestito come per `run.sh`.

Compilazione, controllo della formattazione (Spotless), test unitari, test slice, test end-to-end e test di architettura, packaging.

Lo stesso comando gira anche in CI tramite GitHub Actions ad ogni push:
```bash
./verify.sh
```

---


### °°° Formattazione del codice °°°
Per pulizia della codebase è stato gestito Spotless per la formattazione del codice che, se Lefthook è installato, viene eseguito localmente ad ogni pre-commit.

Tramite Lefthook in fase di pre-commit viene lanciata la formattazione tramite il comando
```bash
./format.sh
```

## Struttura

Codice in `src/main/java/it/ranierivannozzi/webhook/`:

```
webhook/
├── domain/                  DOMAIN (modelli e regole di dominio, solo Java, nessun framework come Spring)
│   ├── model/               Document, Container, ContainerSnapshot, WebhookConfiguration, NotificationMessage
│   └── resolution/          DocumentStateResolver: quale versione e quale stato notificare
│
├── application/             APPLICATION (porte e caso d'uso)
│   ├── port/in/             Porte d'ingresso: OneShotServicePort, OneShotItem, ItemResult, FailedStep
│   ├── port/out/            Porte d'uscita: repository di documenti, contenitori, configurazioni e la coda
│   └── service/             Caso d'uso: OneShotService, i tre passi per ogni elemento, in parallelo con un limite
│
├── adapter/                 ADAPTER (il collegamento con il mondo esterno, attraverso le porte)
│   ├── in/web/              Adattatori d'ingresso: OneShotController, DTO JSON, WebExceptionHandler, CorrelationIdFilter
│   ├── out/dataset/         Adattatori d'uscita: DatasetLoader e repository in memoria, con SimulatedLatency
│   └── out/queue/           Adattatori d'uscita: InMemoryNotificationQueue, FIFO, ritentativi con backoff, dead letters
│
└── config/                  CONFIG (Spring): OneShotConfiguration, il dataset letto all'avvio e l'orologio della coda
```

Il resto del progetto:

```
webhook-renotification-service/
├── src/
│   ├── main/java/           CODICE: vedi sopra
│   ├── main/resources/      RISORSE: application.yaml e dataset/dataset.json
│   └── test/java/           TEST: test unitari, slice, end-to-end e di architettura
│
├── docs/
│   ├── requirements.md      REQUISITI: scenario e contratto HTTP
│   └── postman/             POSTMAN: collection per test manuali
│
├── scripts/
│   └── jdk.sh               JDK: attivazione del JDK 25, usata da run.sh, verify.sh e format.sh
│
├── run.sh                   RUN: avvio del servizio locale
├── verify.sh                VERIFY: build completa con i test (la stessa della CI)
├── format.sh                FORMAT: formattazione del codice
├── lefthook.yml             GIT HOOK: pre-commit (formattazione)
│
└── .github/workflows/       CI: GitHub Actions
```

L'architettura esagonale ed i confini tra i livelli sono verificati dai test sull'architettura (ArchUnit). 
Il dominio è isolato e non dipende da Spring, quindi è disaccoppiato dalla tecnologia e si testa senza avviare nulla; il caso d'uso non conosce HTTP né JSON (non sa se viene chiamato da un endpoint HTTP, da un consumer Kafka o da altro); gli adapter d'uscita implementano le porte d'uscita, quello d'ingresso chiama il caso d'uso attraverso la sua porta.

## Ipotesi

### Richiesta e risposta

Il contratto non dice come trattare le richieste malformate, quindi ho distinto più casi. 

1. Se è il corpo a non essere valido (non è un array JSON, non è JSON, è vuoto) la richiesta viene rifiutata per intero con `ERROR 400` e un corpo che spiega il formato atteso;
2. Con un content type diverso da JSON la risposta è `415`. Una lista vuota invece è valida: la risposta è `200` con `[]`, cioè un risultato per ognuno degli zero elementi.

3. Se il problema riguarda un singolo elemento, fallisce solo quello e gli altri proseguono. Un elemento senza
`requestId` (assente, `null` o vuoto), o un elemento `null` nella lista, risulta `FAILED` con `DOCUMENT_LOOKUP` e
`requestId is missing`. 
4. Un `containerStatus` sconosciuto, come `PAID`, o scritto in minuscolo risulta `FAILED` con
`CONTAINER_LOOKUP`.

5. Due elementi con gli stessi `requestId` e `containerStatus`, confrontati come testo esatto, sono duplicati: vengono
elaborati una volta sola, ma ognuno riceve il suo risultato nella sua posizione.
6. Nella risposta `requestId` è sempre presente, come chiede il contratto, e vale `null` se l'elemento non lo aveva; `containerStatus` è restituito
esattamente come è arrivato. I messaggi d'errore sono in inglese come tutto il codice, anche se l'esempio del contratto è in italiano.

### Regole di risoluzione (passo 1)

I requisiti descrivono le regole per il caso normale; per i casi rari del dataset ho fatto queste scelte.

1. Senza `containerStatus`, l'"ultima versione" è la più alta presente nello storico (`versions`), non il campo
   `currentVersion`.
2. La fotografia allineata è una fotografia che si riferisce a quella versione; se ce n'è più di una, vince la più
   recente.
3. Se nessuna fotografia si riferisce all'ultima versione, uso lo stato attuale del contenitore modificato più di
   recente: per esempio D-002 viene notificato con la versione 4 e lo stato `DELIVERED` di C-002.
4. Quando un documento ha più contenitori, la fotografia si cerca in tutti e il contenitore scelto è quello che la
   contiene; gli altri vengono segnalati nei log come scartati. Per D-003 viene scelto C-003B, `REJECTED`, perché la sua
   fotografia è più recente di quella di C-003. Così un documento con più contenitori segue le stesse regole di uno con
   un contenitore solo.
5. Con `containerStatus` cerco la fotografia più recente in quello stato (D-008, per esempio, è passato due volte da
   `DELIVERED`) e poi la versione del documento a cui si riferisce.
6. Se quella versione manca dallo storico l'elemento fallisce con `DOCUMENT_LOOKUP`: è il caso di D-007 `DELIVERED`,
   che si riferisce alla versione 2, assente. Ho preferito non notificare una versione diversa da quella a cui lo stato
   si riferiva.

### Configurazione webhook (passo 2)

La configurazione del cliente decide se e dove si può notificare.

1. Se il cliente non ha una configurazione (CUST-C) o l'ha disabilitata (CUST-D), l'elemento fallisce con
   `CONFIG_RESOLUTION`: anche se la richiesta arriva esplicitamente da un operatore, non chiamo un endpoint che il
   cliente ha spento.
2. Il messaggio accodato porta solo l'URL e il tipo di autenticazione, non le credenziali: il flusso che consegna le
   recupera da sé, così la coda non contiene segreti.

### Coda e sistemi esterni

I requisiti chiedono di simulare la coda e i sistemi esterni; ho scelto di tenerli in memoria, senza infrastruttura reale.

1. Il dataset viene letto all'avvio da `dataset.json` e caricato in repository in memoria, in sola lettura. Se il file
   ha un valore mancante o non valido l'applicazione non parte, e l'errore indica in che punto del file si trova.
2. In produzione le porte diventerebbero un client HTTP dichiarativo verso il servizio documenti e repository MongoDB
   per contenitori e configurazioni; qui l'ho scartato perché avrebbe richiesto infrastruttura in più ed il tempo a disposizione era limitato.
3. Per simulare i tempi indicati nei requisiti, ogni chiamata ai repository attende un tempo casuale tra 5 ms e 2 s,
   configurabile.
4. La coda è in memoria, al posto di Redis. È FIFO, con ritentativi a backoff esponenziale (30 s, 1 m, 2 m, 4 m); dopo
   5 tentativi il messaggio passa nelle dead letters. Ogni messaggio ha un id che resta lo stesso tra un tentativo e
   l'altro.
5. Il flusso che consegna i messaggi, cioè chi chiama `poll` e `retry`, è fuori dall'esercizio: queste operazioni le
   ho lasciate solo nei test.

### Esecuzione

I requisiti descrivono i tre passi di ogni elemento in sequenza; l'ho letta come la descrizione dei passi di ogni
elemento, non come un vincolo a elaborare gli elementi uno dopo l'altro.

1. In sequenza, con centinaia di elementi e latenze fino a qualche secondo, una richiesta da 500 elementi durerebbe
   circa 25 minuti.
2. Per questo gli elementi sono elaborati in parallelo: ogni elemento distinto ha il suo virtual thread.
3. Un semaforo condiviso da tutte le richieste limita a 16 (configurabile) gli elementi in lavorazione nello stesso
   momento, per proteggere il servizio documenti e il database.
4. Ogni elemento esegue comunque i tre passi in ordine, e i risultati tornano nell'ordine della richiesta.

## Scelte e trade-off

### Il percorso di una richiesta

Cosa succede con una richiesta di tre elementi, uno con `containerStatus` e due senza:

```json
[ { "requestId": "D-001" }, { "requestId": "D-004", "containerStatus": "DELIVERED" }, { "requestId": "D-999" } ]
```

1. Prima di atterrare sul controller, tramite il filtro per il correlation id andiamo ad assegnare alla specifica richiesta un id, preso dall'header e viene inserito nel contesto del log di SLF4J, la storica libreria di logging Java, in modo che ad ogni loggata si possa risalire esattamente a quale richiesta ci stiamo riferendo. Viene restituito nell'header di risposta.
2. Il controller legge il payload e converte il json in oggetto per la porta d'ingresso, da passare poi al service. Se il payload non è un json o se non è un array il servizio va in ERROR 400.
3. Il service raggruppa i duplicati e si occupa di creare un virtual thread per ogni elemento da elaborare in modo che il lavoro sia svolto in parallelo secondo i limiti gestiti nell'application.yaml. Ogni VT prende un coin dal semaforo per capire se vi è disponibilità. Se vi è disponibilità inizia il lavoro, altrimenti attende. Dato che il VT non ha contesto dei log, viene copiato quello del thread della richiesta in modo da avere tutte le informazioni, prima di prendere il coin dal semaforo. 
4. Analisi:
 
      ***{ "requestId": "D-001" }*** - Primo oggetto, senza status. La versione che risulta più alta è la 3. Il container di riferimento è solo 1, il C-001, anch'esso con lo storico allineato a 3 elementi. La versione più alta è la 3 ed è allineata allo storico 3 pertanto lo status è ACCEPTED. Il customer è CUST-A pertanto possiamo recuperare il webhook per la notifica. Risulta essere attivo (enabled: true) pertanto viene messo in coda. Esito QUEUED.
  
      ***{ "requestId": "D-004", "containerStatus": "DELIVERED" }*** - Secondo oggetto, con status. Il D-004 ha due versioni. Il container di riferimento è solo 1, il C-004, lo status è DELIVERED perché si riferisce alla versione 2 presente nello storico del documento. Il customer è CUST-B, enabled: true, quindi anche in questo caso il messaggio va in coda. Esito QUEUED.
  
      ***{ "requestId": "D-999" }*** - Terzo oggetto, senza status. Il documento D-999 non esiste, quindi viene scartato (ma riportato nella response). Gli altri elementi non ne risentono e vanno avanti con l'elaborazione. Esito FAILED.
5. Il servizio, lavorando in parallelo, attende tutti i risultati, li rimette nell'ordine richiesto e logga un riepilogo con quanti successi, quanti fallimenti e la durata in millisecondi per capire il totale speso per l'elaborazione. La risposta è di tipo 200 OK.

I punti ambigui e le interpretazioni che ho scelto sono descritti nelle [Ipotesi](#ipotesi).

### Cosa può andare storto

La colonna "Passo in cui nasce" fa riferimento ai tre passi che il servizio esegue per ogni elemento:

1. individuare documento e contenitore, cioè la versione e lo stato da notificare;
2. risolvere la configurazione webhook del cliente;
3. accodare il messaggio.


| Caso | Passo in cui nasce | Come se ne accorge l'operatore | Gravità | Come lo tratta il codice |
|---|---|---|---|---|
| `requestId` mancante | prima del passo 1 | `FAILED`, `DOCUMENT_LOOKUP`, `requestId is missing` | bassa | fallisce solo l'elemento |
| stato sconosciuto o minuscolo | prima del passo 1 | `FAILED`, `CONTAINER_LOOKUP`, `unknown container status 'x'` | bassa | fallisce solo l'elemento |
| documento inesistente | 1 | `FAILED`, `DOCUMENT_LOOKUP`, `document not found` | bassa | fallisce solo l'elemento |
| versione della fotografia assente dallo storico (D-007 `DELIVERED`) | 1 | `FAILED`, `DOCUMENT_LOOKUP` | media: dati incoerenti a monte | nessun ripiego su un'altra versione |
| nessuna fotografia nello stato richiesto, nessun contenitore | 1 | `FAILED`, `CONTAINER_LOOKUP` | bassa | fallisce solo l'elemento |
| **più contenitori** (D-003) | 1 | **non se ne accorge**: l'esito è `QUEUED` | media: il cliente può ricevere `REJECTED` dove si aspettava `DELIVERED` | regola deterministica (fotografia più recente) e `WARN` nei log con i contenitori scartati |
| **nessuna fotografia allineata** (D-002) | 1 | **non se ne accorge**: `QUEUED` | media: si notifica la versione 4 con uno stato che si riferiva alla 3 | stato corrente del contenitore, riga `INFO` nei log |
| configurazione assente o disabilitata | 2 | `FAILED`, `CONFIG_RESOLUTION` | bassa | fallisce solo l'elemento |
| servizio documenti o database in errore | 1 o 2 | `FAILED` al passo, `document unavailable` e simili | media | eccezione registrata nei log con lo stack, all'operatore solo il messaggio breve; nessun ritentativo |
| sorgenti lente | tutti | la risposta arriva tardi o il suo client va in timeout, mentre il servizio continua ad accodare | alta: l'operatore rilancia e il cliente riceve doppioni | il semaforo protegge le sorgenti; nessun timeout per elemento (vedi [Cosa non ho fatto e cosa farei per primo](#cosa-non-ho-fatto-e-cosa-farei-per-primo)) |
| coda non disponibile | 3 | `FAILED`, `CHANNEL_SEND`, `notification queue unavailable` | media | fallisce solo l'elemento |
| riavvio del servizio dopo l'accodamento | dopo il 3 | `QUEUED`, ma il messaggio è perso | alta in produzione | limite della simulazione in memoria: con Redis la coda sopravvive al riavvio |
| consegna al cliente fallita | flusso successivo | non lo vede: per lui è `QUEUED` | media | ritentativi con backoff, poi dead letters con log `ERROR` |
| corpo malformato | prima di tutto | `400` con il formato atteso | bassa | nessun elemento elaborato |
| errore inatteso | qualunque | `500` con il correlation id da citare, senza dettagli interni | alta: alcuni elementi potrebbero essere già in coda | gli errori dei singoli elementi, bug compresi, sono confinati all'elemento, quindi un `500` dopo un accodamento è molto improbabile |
| dataset non valido | avvio | il servizio non parte | totale | errore con la posizione nel file |

### Cosa significa l'esito

**`QUEUED`**: 

- L'operatore sa che c'è stata corrispondenza fra documento, contenitore e webhook. Un messaggio è stato messo in coda. 
    
- L'operatore sa che QUEUED vuol dire "preso in carico", non "consegnato".

- L'operatore non è a conoscenza di cosa sia stato accodato, non ha visibilità dello stato nel dataset.json. 
    
- L'operatore non è a conoscenza se il cliente ha ricevuto o meno il messaggio accodato e se lo ha elaborato.
    
- L'operatore non è a conoscenza se il messaggio in coda è su Redis o altro, e quindi se sopravviverà a un riavvio del server.
    

**`FAILED`**: 

- L'operatore sa che per quell'elemento non è stato accodato nulla, in quale passo si è fermato e perché. Questo perché l'accodamento è l'ultimo passo.

- Non sa se il problema è permanente (documento inesistente) o temporaneo (sorgente non disponibile).
    

### Richieste ripetute

Il servizio non ricorda le richieste precedenti: i duplicati sono riconosciuti solo nello stesso payload.

- **Due volte per errore**: ogni elemento viene elaborato di nuovo e accodato di nuovo. Il cliente
  riceve due avvisi identici.
- **Dieci minuti dopo**: idem; in più, se nel frattempo il documento è cambiato, il secondo avviso descrive il nuovo
  stato. Questo è corretto: senza containerStatus si chiede l'ultimo stato.

Per il servizio non è un problema: le letture sono senza effetti e l'accodamento costa poco o nulla. 
Per il cliente, se il suo sistema non è idempotente (per esempio crea un record per ogni avviso), possono esserci problemi di doppioni.
Il cliente può riconoscere i doppioni da requestId, versione e stato, ma non dall'id del messaggio, che cambia tra le richieste.

Per evitarlo servirebbe una chiave di idempotenza (cliente, documento, versione, stato) ricordata per un certo tempo
sulla coda o su MongoDB a patto di scrivere il tutto insieme al messaggio inviato: un secondo accodamento della stessa chiave verrebbe ignorato.

### Volume e tempi

Con 500 elementi:

- ogni elemento fa tre chiamate (documento, contenitori, configurazione), ciascuna tra 5 ms e 2 s (latenza configurabile): consideriamo una media di circa 3 s per elemento.
- gli elementi in lavorazione simultaneamente sono al massimo 16, quindi 500 elementi sono circa 32 giri da circa 3 s (3 s per 16 VT paralleli). In media possiamo considerare circa un minuto e mezzo. Caso peggiore sui 3 minuti (se consideriamo fino a 6 s per elemento).

La durata è limitata dalla latenza (configurabile nell'esercizio, reale in produzione) e dal limite del parallelismo. Il limite è voluto: alzarlo accorcia la richiesta ma aumenta il traffico sul servizio documenti e sul database. 
Il semaforo è condiviso tra tutte le richieste e rispetta l'ordine di arrivo, quindi una richiesta piccola che arriva subito dopo una da 500 aspetta dietro di
lei.

Il costo in risorse è basso: i virtual thread in attesa non occupano il thread del sistema operativo.

Chi chiama non vede nulla finché non ha finito tutto: la risposta è sincrona e arriva intera alla fine. Se il suo client, o un proxy in mezzo, ha un timeout più corto (spesso 30–60 s), riceve un errore mentre il servizio continua ad 
accodare: l'operatore pensa che sia fallito tutto, rilancia, e il cliente riceve doppioni (vedi [Richieste ripetute](#richieste-ripetute)).

### Osservare il servizio in produzione

Per rispondere dovremmo poter ricostruire il viaggio di ogni elemento non ricevuto. I log mostreranno i dati tramite il correlation id permettendo la ricostruzione storica, poiché abbiamo:
- una riga per ogni elemento con l'esito, il passo e l'errore;
- una riga di riepilogo per richiesta: elementi, accodati, falliti, durata;
- una riga per ogni messaggio accodato con id, documento, versione, stato e URL;
- `WARN` per i contenitori scartati, `INFO` per il **ripiego** sul contenitore corrente, `WARN` con lo stack per le sorgenti in errore;
- sulla coda: `WARN` a ogni ritentativo, `ERROR` quando un messaggio va nelle dead letters.

Quindi:
- se è arrivata una richiesta one-shot che lo conteneva: quando, da chi, con quale esito, chiedendo eventualmente il payload se disponibile;
- se `QUEUED`, cosa è stato accodato (versione, stato, URL) e con quale id;
- cosa è successo nella consegna: tentativi, risposte HTTP del cliente, dead letters;
- se il rilascio ha cambiato qualcosa: per esempio un aumento dei `FAILED` per un certo passo.

Cosa manca:
- chi ha fatto la richiesta: l'endpoint non ha autenticazione, si ipotizza Keycloak;
- quanti casi di `QUEUED` e di `FAILED` per passo (doc / cont / versione -> webhook -> coda): mostrerebbero subito se dopo il rilascio i fallimenti sono
  aumentati, senza contare i log a mano;
- un id che segua l'avviso anche nel flusso di consegna: oggi abbiamo solo il correlation id, che vale solo dentro questo servizio; non c'è un id del singolo oggetto da seguire nel payload;
- i log della consegna (tentativi e risposte del cliente): potrebbe anche essere fallito il servizio webhook del cliente.

### Strategia di test

Ho scelto di coprire i livelli dell'architettura esagonale, quindi:

- le regole di dominio: sono la parte più facile da sbagliare e la più difficile da vedere dall'esterno. È la logica pura del sistema. 
  Ogni caso raro del dataset ha il suo test con gli stessi identificativi: contenitori multipli, buchi nello storico, fotografie duplicate, ripiego sul contenitore corrente;
- il caso d'uso: i test del service con un fallimento per ogni passo, l'isolamento tra elementi, i duplicati, il parallelismo entro il limite, il contesto dei log nei thread;
- gli adapter: il contratto HTTP con il caso d'uso simulato, il caricamento del dataset e i suoi errori, i repository, la latenza, la coda con un orologio finto (backoff e dead letters senza attese reali);
- test E2E: test completo con l'esempio del contratto sull'applicazione completa, verificando anche cosa finisce in coda. Viene tirato su Spring;
- test di architettura: per rispettare l'architettura esagonale.

Ho scelto questi perché coprono il contratto, le regole e i fallimenti (che un elemento non blocchi gli altri).

Cosa ho lasciato fuori, e il rischio:

- Redis, MongoDB e il servizio documenti veri: sono simulati, quindi non so come si comportano errori e lentezze reali;
- un test di carico con 500 elementi: i tempi indicati in [Volume e tempi](#volume-e-tempi) sono stime, non misure;
- il flusso di consegna: non esiste, quindi non è testato.

### Cosa non ho fatto e cosa farei per primo

Cosa ho deciso di non fare:

- coda persistente reale (Redis) e chiave di idempotenza;
- timeout per elemento e durata massima della richiesta;
- autenticazione (Keycloak) e traccia di chi ha chiesto l'invio;
- metriche, tracing e log strutturati.

**Per prima: Redis**

- Oggi la coda, essendo in memoria, viene persa a ogni riavvio del server. Basta un rilascio per perdere
  tutto. Un Redis reale con salvataggio su disco manterrebbe i messaggi. Salvando anche una chiave di idempotenza
  (cliente, documento, versione, stato) si eviterebbero i doppioni ai clienti.
- Con l'architettura ports & adapters basta scrivere un'altra implementazione della stessa porta; il resto del
  codice non cambia. In più c'è Redis da gestire e da testare.
- Lo verificherei con un test che riavvia il servizio e ritrova i messaggi in coda; in produzione, con nessun
  avviso perso dopo un rilascio.

## Uso dell'IA

Ho usato Claude Code (Opus 5.5) per il codice, i test e una prima bozza del README che ho completamente revisionato manualmente.

**Stati in minuscolo.** Ho chiesto se si potesse accettare `containerStatus` in minuscolo. Mi è capitato in passato di vedere rifiutati come malformati dei payload che in realtà erano corretti, solo per maiuscole non rispettate.
Mi ha fatto notare che `DELIVERED` e `delivered` sarebbero stati due elementi diversi, quindi due avvisi al cliente. Ho tenuto la validazione rigida, con un errore chiaro per l'operatore.

**Idempotenza.** Avevo proposto di salvare la chiave di idempotenza su una tabella del database. Mi ha fatto notare che
chiave e messaggio vanno scritti insieme, altrimenti un crash tra le due scritture perde o duplica l'avviso. Ho tenuto
entrambe le opzioni, Redis o MongoDB, con questo vincolo.
