# Requisiti

Il servizio fa parte di un sistema che avvisa i clienti, tramite webhook, quando un loro documento cambia stato.
Gli avvisi partono di solito in automatico; questo servizio copre il caso in cui un operatore chiede di inviarli di
nuovo, a mano, per un elenco di documenti: per esempio quando il sistema del cliente è rimasto irraggiungibile per un
po' e ha perso degli avvisi.

## I dati

Il servizio legge tre tipi di dati, che appartengono a sistemi esterni e non vengono mai modificati.

- **Documento.** È identificato da un `requestId` e appartiene a un cliente. Ogni modifica crea una nuova versione,
  con un numero crescente; le versioni precedenti restano nello storico.
- **Contenitore.** Descrive a che punto è la lavorazione di un documento, con uno stato come `SENT`, `DELIVERED` o
  `REJECTED`. Ogni cambio di stato salva una fotografia del contenitore, che indica anche a quale versione del
  documento si riferiva in quel momento.
- **Configurazione webhook.** Ogni cliente ha la sua: l'indirizzo da chiamare, il tipo di autenticazione e se gli
  avvisi sono attivi.

I dati di esempio sono in `src/main/resources/dataset/dataset.json` e comprendono anche alcuni casi rari, scelti
apposta.

## Cosa deve fare il servizio

L'endpoint `POST /v1/webhook/oneshot` riceve una lista di elementi. Ogni elemento indica un documento e, se serve,
lo stato del contenitore da notificare. Per ogni elemento il servizio esegue tre passi, in quest'ordine.

1. **Trovare cosa notificare.**
   - Se l'elemento non indica uno stato, si notifica l'ultima versione del documento, insieme alla fotografia del
     contenitore che si riferisce a quella versione. Se una fotografia così non esiste, si usa lo stato attuale del
     contenitore.
   - Se l'elemento indica uno stato, si cerca nello storico la fotografia del contenitore in quello stato, e si
     notifica la versione del documento a cui quella fotografia si riferiva.
2. **Trovare la configurazione webhook** del cliente a cui appartiene il documento.
3. **Accodare un messaggio** su una coda interna, con la possibilità di ritentare. La chiamata HTTP al cliente non è
   compito di questo servizio: la fa un altro processo, più tardi, leggendo la coda.

Un elemento che fallisce non deve fermare gli altri.

## Contratto HTTP

### Richiesta

`POST /v1/webhook/oneshot`, con `Content-Type: application/json`. Il corpo è un array di oggetti.

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `requestId` | string | sì | il documento da notificare |
| `containerStatus` | string | no | lo stato del contenitore da notificare; senza, si usa l'ultima versione del documento |

```json
[
  { "requestId": "D-001" },
  { "requestId": "D-004", "containerStatus": "DELIVERED" },
  { "requestId": "D-999" }
]
```

### Risposta

`200 OK`, con `Content-Type: application/json`. Il corpo è un array con un risultato per ogni elemento ricevuto, nello
stesso ordine della richiesta.

| Campo | Tipo | Quando c'è | Descrizione |
|---|---|---|---|
| `requestId` | string | sempre | ripetuto dalla richiesta |
| `containerStatus` | string | se c'era nella richiesta | ripetuto dalla richiesta |
| `outcome` | string | sempre | `QUEUED` se il messaggio è stato accodato, `FAILED` altrimenti |
| `failedStep` | string | solo con `FAILED` | il passo che è fallito: `DOCUMENT_LOOKUP`, `CONTAINER_LOOKUP`, `CONFIG_RESOLUTION` o `CHANNEL_SEND` |
| `error` | string | solo con `FAILED` | una spiegazione comprensibile per l'operatore |

```json
[
  { "requestId": "D-001", "outcome": "QUEUED" },
  { "requestId": "D-004", "containerStatus": "DELIVERED", "outcome": "QUEUED" },
  { "requestId": "D-999", "outcome": "FAILED", "failedStep": "DOCUMENT_LOOKUP", "error": "document not found" }
]
```

### Il messaggio in coda

Il formato è libero, ma il messaggio deve bastare da solo al processo che chiama il cliente: deve contenere la
versione del documento scelta, lo stato del contenitore e la configurazione webhook, così che quel processo non debba
rileggere i dati.

## Condizioni di lavoro

- Capita, raramente, che un documento abbia più di un contenitore: il servizio ne sceglie uno e lo scrive nei log.
- Le sorgenti dei dati rispondono con tempi molto variabili, da pochi millisecondi a qualche secondo.
- Le richieste arrivano da strumenti per gli operatori e possono contenere centinaia di elementi.
- La coda e i sistemi esterni sono simulati: come simularli è una scelta di progetto.
- Il comportamento con richieste malformate (corpo vuoto, lista vuota, elementi senza `requestId`, elementi ripetuti)
  non è fissato: le scelte fatte sono descritte nel README.
- Il progetto si compila e passa i test con un solo comando.
