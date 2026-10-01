package com.app.minicaisse

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.app.minicaisse.data.local.AppDatabase
import com.app.minicaisse.domain.model.Catalog
import com.app.minicaisse.domain.model.Product
import com.app.minicaisse.domain.usecase.CheckoutUseCase
import com.app.minicaisse.domain.usecase.RetryPrintingUseCase
import com.app.minicaisse.printing.FakePrinter
import com.app.minicaisse.ui.theme.MiniCaisseTheme
import com.app.minicaisse.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Programme l'envoi vers Firebase (s'exécute dès que le réseau est disponible). */
fun enqueueSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueue(syncWork)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiniCaisseTheme {
                val scope = rememberCoroutineScope()
                val db = AppDatabase.getDatabase(applicationContext)
                val checkoutUseCase = CheckoutUseCase(db, db.saleDao(), db.ticketCounterDao())
                val printUseCase = RetryPrintingUseCase(db.saleDao(), FakePrinter())

                // Gestion de la navigation entre les écrans
                var showHistory by remember { mutableStateOf(false) }

                if (showHistory) {
                    HistoryScreen(
                        saleDao = db.saleDao(),
                        onBackClick = { showHistory = false },
                        onRefresh = {
                            // Pull-to-refresh : relance impressions en attente + synchro Firebase
                            withContext(Dispatchers.IO) {
                                try {
                                    printUseCase.retryAllPending()
                                } catch (e: Exception) {
                                    Log.e("REFRESH", "Erreur réimpression", e)
                                }
                            }
                            enqueueSync(applicationContext)
                            delay(800) // laisse le temps à la synchro de démarrer
                        }
                    )
                } else {
                    CaisseScreen(
                        scope = scope,
                        checkoutUseCase = checkoutUseCase,
                        printUseCase = printUseCase,
                        context = applicationContext,
                        onHistoryClick = { showHistory = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaisseScreen(
    scope: kotlinx.coroutines.CoroutineScope,
    checkoutUseCase: CheckoutUseCase,
    printUseCase: RetryPrintingUseCase,
    context: Context,
    onHistoryClick: () -> Unit
) {
    val cart = remember { mutableStateMapOf<Product, Int>() }
    var statusText by remember { mutableStateOf("Prêt") }
    val totalPrice = cart.entries.sumOf { it.key.price * it.value }

    // Au démarrage de l'écran, on relance les impressions en attente/échec
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("STARTUP_PRINT", "Vérification des tickets non imprimés au démarrage...")
                printUseCase.retryAllPending()
                Log.d("STARTUP_PRINT", "Vérification terminée. Lancement synchro Firebase...")
                enqueueSync(context)
            } catch (e: Exception) {
                Log.e("STARTUP_PRINT", "Erreur lors de la réimpression au démarrage", e)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Caisse") },
                actions = {
                    TextButton(onClick = onHistoryClick) {
                        Text("Historique")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = statusText, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(Catalog.products) { product ->
                    ProductRow(
                        product = product,
                        quantity = cart[product] ?: 0,
                        onAddClick = { cart[product] = (cart[product] ?: 0) + 1 },
                        onRemoveClick = {
                            val current = cart[product] ?: 0
                            if (current > 1) cart[product] = current - 1 else cart.remove(product)
                        }
                    )
                    HorizontalDivider()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total : $totalPrice TND", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (cart.isEmpty()) return@Button
                            statusText = "Encaissement..."

                            // Copie du panier AVANT le lancement de la coroutine
                            val cartItems = cart.entries.map {
                                CheckoutUseCase.CartItem(it.key.id, it.key.name, it.key.price, it.value)
                            }

                            scope.launch(Dispatchers.IO) {
                                try {
                                    Log.d("TICKET_TRACE", "1. Bouton Encaisser cliqué. Préparation des données.")
                                    Log.d("TICKET_TRACE", "2. Début transaction Room (Atomicité)...")
                                    val sale = checkoutUseCase.execute(cartItems)
                                    Log.d("TICKET_TRACE", "3. Vente N°${sale.ticketNumber} enregistrée en base. ID: ${sale.localId}")

                                    withContext(Dispatchers.Main) {
                                        cart.clear()
                                        statusText = "Ticket N°${sale.ticketNumber} encaissé ✅"
                                    }
                                    Log.d("TICKET_TRACE", "4. UI mise à jour, panier vidé.")

                                    Log.d("TICKET_TRACE", "5. Lancement impression du nouveau ticket...")
                                    printUseCase.printSingleSale(sale)
                                    Log.d("TICKET_TRACE", "6. Impression terminée.")

                                    Log.d("TICKET_TRACE", "7. Programmation de la synchronisation Firebase...")
                                    enqueueSync(context)
                                    Log.d("TICKET_TRACE", "8. Worker enregistré.")
                                } catch (e: Exception) {
                                    Log.e("TICKET_TRACE_ERROR", "Erreur pendant l'encaissement : ", e)
                                    withContext(Dispatchers.Main) {
                                        statusText = "Erreur lors de l'encaissement ❌"
                                    }
                                }
                            }
                        },
                        enabled = cart.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Encaisser")
                    }
                }
            }
        }
    }
}

@Composable
fun ProductRow(product: Product, quantity: Int, onAddClick: () -> Unit, onRemoveClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge)
            Text("${product.price} TND", color = MaterialTheme.colorScheme.secondary)
        }
        if (quantity > 0) {
            TextButton(onClick = onRemoveClick) { Text("-") }
            Text("$quantity", modifier = Modifier.padding(horizontal = 8.dp))
        }
        Button(onClick = onAddClick) { Text("+") }
    }
}