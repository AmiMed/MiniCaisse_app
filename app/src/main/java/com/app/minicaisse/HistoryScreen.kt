package com.app.minicaisse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.minicaisse.data.local.PrintStatus
import com.app.minicaisse.data.local.SaleDao
import com.app.minicaisse.data.local.SaleEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    saleDao: SaleDao,
    onBackClick: () -> Unit,
    onRefresh: suspend () -> Unit = {} 
) {
    // null = Room n'a pas encore répondu (chargement initial)
    val salesOrNull by saleDao.getAllSales().collectAsState(initial = null)
    val isInitialLoading = salesOrNull == null
    val sales = salesOrNull ?: emptyList()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<PrintStatus?>(null) }

    // Indicateur de rechargement (pull-to-refresh)
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val filteredSales = remember(sales, searchQuery, selectedFilter) {
        sales.filter { sale ->
            val matchesStatus = selectedFilter == null || sale.printStatus == selectedFilter
            val matchesSearch = searchQuery.isBlank() ||
                    sale.ticketNumber.toString().contains(searchQuery.trim())
            matchesStatus && matchesSearch
        }
    }

    // Pagination : reset à 5 dès que le filtre ou la recherche change
    var visibleItemsCount by remember(searchQuery, selectedFilter) { mutableStateOf(PAGE_SIZE) }
    val listState = rememberLazyListState()

    LaunchedEffect(searchQuery, selectedFilter) {
        listState.scrollToItem(0)
    }

    val hasMore = visibleItemsCount < filteredSales.size
    LaunchedEffect(listState, hasMore, visibleItemsCount, filteredSales.size) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
            .filter { it }
            .first()
        visibleItemsCount += PAGE_SIZE
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des ventes") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Rechercher par N° ticket") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp),
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("Tous") }
                )
                FilterChip(
                    selected = selectedFilter == PrintStatus.PRINTED,
                    onClick = { selectedFilter = PrintStatus.PRINTED },
                    label = { Text("Imprimé ✅") }
                )
                FilterChip(
                    selected = selectedFilter == PrintStatus.PENDING,
                    onClick = { selectedFilter = PrintStatus.PENDING },
                    label = { Text("En attente ⏳") }
                )
                FilterChip(
                    selected = selectedFilter == PrintStatus.FAILED,
                    onClick = { selectedFilter = PrintStatus.FAILED },
                    label = { Text("Échec ❌") }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            when {
                // Chargement initial : loader plein écran
                isInitialLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                try {
                                    onRefresh()
                                    delay(600) // laisse l'indicateur visible un instant
                                } finally {
                                    isRefreshing = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (filteredSales.isEmpty()) {
                            // Scrollable pour que le pull-to-refresh fonctionne aussi à vide
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aucune vente trouvée pour ces critères.")
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = filteredSales.take(visibleItemsCount),
                                    key = { it.localId }
                                ) { sale ->
                                    SaleHistoryItem(sale)
                                }

                                if (hasMore) {
                                    item(key = "pagination_loader") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaleHistoryItem(sale: SaleEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Ticket N°${sale.ticketNumber}", style = MaterialTheme.typography.titleMedium)
                Text("Total : ${sale.totalAmount} TND", style = MaterialTheme.typography.bodyMedium)
            }

            val (statusText, statusColor) = when (sale.printStatus) {
                PrintStatus.PRINTED -> "Imprimé ✅" to Color(0xFF4CAF50)
                PrintStatus.PENDING -> "En attente ⏳" to Color(0xFFFFA000)
                PrintStatus.FAILED -> "Échec ❌" to Color(0xFFD32F2F)
            }

            Text(
                text = statusText,
                color = statusColor,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}