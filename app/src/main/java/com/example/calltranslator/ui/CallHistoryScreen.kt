package com.example.calltranslator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calltranslator.model.CallHistoryItem
import com.example.calltranslator.model.Contact
import com.example.calltranslator.model.FavoriteContact
import com.example.calltranslator.repository.FavoritesRepository
import com.example.calltranslator.viewmodel.CallHistoryState
import com.example.calltranslator.viewmodel.CallHistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onNavigateBack: () -> Unit,
    onContactClick: (Contact) -> Unit,
    onCallBack: (String, String) -> Unit,
    onNavigateToPhone: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToRecents: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onAddContact: () -> Unit,
    viewModel: CallHistoryViewModel = viewModel()
) {
    val state by viewModel.historyState.collectAsState()
    val favoritesRepo = remember { FavoritesRepository() }
    var favorites by remember { mutableStateOf<List<FavoriteContact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        favoritesRepo.getFavoritesFlow().collectLatest { list ->
            favorites = list
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Phone",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Contacts") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToContacts()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Clear call history") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showClearDialog = true
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Help, contentDescription = null) },
                    label = { Text("Help and feedback") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showHelpDialog = true
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                ) {
                    // Top Search Bar (Google Phone style - NO mic)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable(onClick = onNavigateToSearch),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search contacts",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 16.sp
                                    )
                                }
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    singleLine = true
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "U",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // Filter Chips: All, Missed, Contacts, Non-spam
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Missed", "Contacts", "Non-spam").forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            },
            bottomBar = {
                AppBottomNavigation(
                    currentRoute = "recents",
                    onNavigateToPhone = onNavigateToPhone,
                    onNavigateToContacts = onNavigateToContacts,
                    onNavigateToRecents = onNavigateToRecents,
                    onNavigateToFavorites = onNavigateToFavorites,
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
                when (val historyState = state) {
                    is CallHistoryState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is CallHistoryState.Success -> {
                        val rawItems = historyState.items
                        val filteredItems = rawItems.filter { item ->
                            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) || item.phoneNumber.contains(searchQuery)
                            val matchesFilter = when(selectedFilter) {
                                "Missed" -> item.type == "Missed"
                                "Contacts" -> item.name.isNotEmpty()
                                "Non-spam" -> item.type != "Spam"
                                else -> true
                            }
                            matchesSearch && matchesFilter
                        }

                        if (filteredItems.isEmpty()) {
                            CallHistoryEmptyState()
                        } else {
                            val grouped = filteredItems.groupBy { it.phoneNumber.ifEmpty { it.name } }
                            val callEntries = grouped.map { (_, group) ->
                                Pair(group.first(), group.size)
                            }

                            // Split into Today, Yesterday, Older
                            val todayCalendar = Calendar.getInstance()
                            val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

                            val todayList = mutableListOf<Pair<CallHistoryItem, Int>>()
                            val yesterdayList = mutableListOf<Pair<CallHistoryItem, Int>>()
                            val olderList = mutableListOf<Pair<CallHistoryItem, Int>>()

                            callEntries.forEach { entry ->
                                val date = entry.first.timestamp?.toDate()
                                if (date != null) {
                                    val cal = Calendar.getInstance().apply { time = date }
                                    when {
                                        isSameDay(cal, todayCalendar) -> todayList.add(entry)
                                        isSameDay(cal, yesterdayCalendar) -> yesterdayList.add(entry)
                                        else -> olderList.add(entry)
                                    }
                                } else {
                                    olderList.add(entry)
                                }
                            }

                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Favorites Section at top if "All" selected and no active search
                                if (selectedFilter == "All" && searchQuery.isEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Favourites",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            TextButton(onClick = onNavigateToContacts) {
                                                Text("View contacts", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Add Contact button
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable(onClick = onAddContact)
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(56.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Add",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            }

                                            favorites.forEach { fav ->
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.clickable { onContactClick(Contact(id = fav.phoneNumber, name = fav.name, phoneNumber = fav.phoneNumber)) }
                                                ) {
                                                    Surface(
                                                        modifier = Modifier.size(56.dp),
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = fav.name.firstOrNull()?.uppercase() ?: "?",
                                                                fontSize = 22.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = fav.name,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 1,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }

                                if (todayList.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Today",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    items(
                                        items = todayList,
                                        key = { it.first.id }
                                    ) { (item, count) ->
                                        CallHistoryCard(
                                            item = item,
                                            callCount = count,
                                            onRowClick = { onContactClick(Contact(id = item.id, name = item.name, phoneNumber = item.phoneNumber)) },
                                            onCallBack = { onCallBack(item.name, item.phoneNumber) }
                                        )
                                    }
                                }

                                if (yesterdayList.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Yesterday",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    items(
                                        items = yesterdayList,
                                        key = { it.first.id }
                                    ) { (item, count) ->
                                        CallHistoryCard(
                                            item = item,
                                            callCount = count,
                                            onRowClick = { onContactClick(Contact(id = item.id, name = item.name, phoneNumber = item.phoneNumber)) },
                                            onCallBack = { onCallBack(item.name, item.phoneNumber) }
                                        )
                                    }
                                }

                                if (olderList.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Older",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    items(
                                        items = olderList,
                                        key = { it.first.id }
                                    ) { (item, count) ->
                                        CallHistoryCard(
                                            item = item,
                                            callCount = count,
                                            onRowClick = { onContactClick(Contact(id = item.id, name = item.name, phoneNumber = item.phoneNumber)) },
                                            onCallBack = { onCallBack(item.name, item.phoneNumber) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is CallHistoryState.Error -> {
                        Text(
                            text = historyState.message,
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear call history?") },
            text = { Text("All recent calls will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearHistory()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help and feedback") },
            text = { Text("AI Call Translator v1.0\n\nFor support, feedback, or inquiries regarding WebRTC and AI Live Translation, contact support@aicalltranslator.example.com.") },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun CallHistoryCard(
    item: CallHistoryItem,
    callCount: Int,
    onRowClick: () -> Unit,
    onCallBack: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (callCount > 1) "${item.name.ifEmpty { item.phoneNumber }} ($callCount)" else item.name.ifEmpty { item.phoneNumber },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.type == "Missed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val directionIcon = when(item.type) {
                        "Outgoing" -> "↗"
                        "Missed" -> "↘"
                        else -> "↙"
                    }
                    Text(
                        text = directionIcon,
                        color = when(item.type) {
                            "Missed" -> MaterialTheme.colorScheme.error
                            "Outgoing" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Mobile • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    val date = item.timestamp?.toDate()
                    val timeStr = if (date != null) {
                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
                    } else ""
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (item.aiEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🤖 AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(onClick = onCallBack) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CallHistoryEmptyState() {
    CallHistoryEmptyStatePreview()
}

@Composable
fun CallHistoryEmptyStatePreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Phone,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No recent calls",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
