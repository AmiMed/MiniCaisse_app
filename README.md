# MiniCaisse

Application Android de caisse enregistreuse (Kotlin / Jetpack Compose).
Architecture **offline-first** : Room est la source de vérité (SSOT), et Firebase est synchronisé en arrière-plan via WorkManager.

---

## Tech Stack

| Couche | Technologie |
|---|---|
| UI | Jetpack Compose, Coroutines / Flow |
| Local | Room (SQLite), source de vérité |
| Cloud | Firebase Realtime Database |
| Arrière-plan | WorkManager (synchronisation Firebase) |
| Injection | Instanciation manuelle des UseCases et DAOs |

---

## Architecture et flux

```
[ Compose UI ] -> [ UseCases ] -> [ Room DB ]   (écriture locale atomique et instantanée)
                                      |
                                      +--> [ WorkManager ] --(réseau ?)--> [ Firebase ]
```

Le panier est vidé et la main est rendue à l'utilisateur juste après la transaction Room, sans attendre le réseau.

---

## Structure du projet

```
app/src/main/java/com/app/minicaisse/
├── MainActivity.kt            # Navigation Caisse <-> Historique
├── HistoryScreen.kt           # Historique : recherche, filtres, pagination, pull-to-refresh
├── data/local/                # Room : AppDatabase, entités, DAOs
├── domain/
│   ├── model/                 # Product, Catalog
│   └── usecase/               # CheckoutUseCase, RetryPrintingUseCase
├── printing/                  # FakePrinter (simulation d'imprimante)
├── worker/                    # SyncWorker (envoi vers Firebase)
└── ui/theme/                  # Thème Compose
```

---

## Schéma de données

### Room (`caisse_database`)

| Table | Colonnes principales |
|---|---|
| `sales` | `localId` (UUID, PK), `ticketNumber` (Long), `totalAmount` (Double), `createdAt` (Long), `printStatus` (enum), `syncStatus` (enum) |
| `sale_items` | `id` (UUID, PK), `saleId` (FK), `productId`, `productName`, `unitPrice`, `quantity` |
| `ticket_counter` | `id` = 1, `nextTicketNumber` (Long) |

Enums : `PrintStatus` (`PENDING`, `PRINTED`, `FAILED`) et `SyncStatus` (`PENDING`, `SYNCED`).

### Firebase

```
sales/{localId}
```

Le `localId` sert de clé, ce qui rend l'envoi **idempotent** : renvoyer une vente l'écrase au lieu de la dupliquer.

---

## Garanties techniques

- **Atomicité (non-perte)** : l'encaissement s'exécute dans
  `db.withTransaction { insertSale(); insertItems(); incrementCounter() }`.
  Tout réussit ou tout est annulé (rollback).
- **Unicité du numéro de ticket** : compteur local dans Room, indépendant du réseau, séquentiel sur un terminal.
- **Retry d'impression** : au démarrage, `RetryPrintingUseCase` cible `printStatus IN ('PENDING', 'FAILED')`. Les tickets `PRINTED` ne sont jamais réimprimés.
- **Offline / reconnexion** : la vente est créée en `syncStatus = PENDING`. WorkManager exige `NetworkType.CONNECTED`. Au retour du réseau, Firebase est mis à jour, puis Room passe la vente en `SYNCED`.

---

## Prérequis

- Android Studio (SDK, émulateur) et JDK 17 ou plus
- Appareil ou émulateur sous Android 8.0 ou plus (`minSdk 26`)
- Un projet Firebase avec Realtime Database

---

## Configuration

1. Placer `google-services.json` dans le dossier `app/`.
2. Console Firebase : activer **Realtime Database** en mode test (`read` / `write` : `true`).
3. La persistance Firebase est activée dans la classe `Application` (`setPersistenceEnabled(true)`).

> Le mode test est réservé au développement. En production, utiliser l'authentification et des règles restrictives (`auth != null`).

---

## Lancer l'application

```bash
./gradlew installDebug
```