package com.v2ray.angm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.angm.AppConfig
import com.v2ray.angm.R
import com.v2ray.angm.dto.entities.RulesetItem
import com.v2ray.angm.handler.MmkvManager
import com.v2ray.angm.handler.SettingsManager
import com.v2ray.angm.viewmodel.RoutingSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingSettingsScreen(
    viewModel: RoutingSettingsViewModel,
    onBack: () -> Unit,
    onEditRule: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    
    val routingDomainStrategy = stringArrayResource(R.array.routing_domain_strategy)
    val currentStrategy = MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: routingDomainStrategy.first()

    val rulesets = remember { mutableStateListOf<RulesetItem>() }
    
    LaunchedEffect(Unit) {
        viewModel.reload()
        rulesets.clear()
        rulesets.addAll(viewModel.getAll())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routing_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditRule(-1) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Rule")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            val presetRulesets = stringArrayResource(R.array.preset_rulesets)
                            
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_import_predefined_rulesets)) },
                                onClick = {
                                    showMenu = false
                                    // Implementation for preset rulesets dialog
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_clipboard)) },
                                onClick = {
                                    showMenu = false
                                    // Implementation for clipboard import
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_export_rulesets_to_clipboard)) },
                                onClick = {
                                    showMenu = false
                                    // Implementation for clipboard export
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                ListSettingItem(
                    title = stringResource(R.string.routing_settings_domain_strategy),
                    summary = currentStrategy,
                    entries = routingDomainStrategy,
                    entryValues = routingDomainStrategy,
                    selectedValue = currentStrategy,
                    onValueChange = { newValue ->
                        MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, newValue)
                        viewModel.reload() // Or just update local state if needed
                    }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.routing_settings_rule_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }

            itemsIndexed(rulesets) { index, item ->
                RoutingRuleItem(
                    item = item,
                    onEdit = { onEditRule(index) },
                    onToggleEnabled = { enabled ->
                        item.enabled = enabled
                        SettingsManager.saveRoutingRuleset(index, item)
                    }
                )
            }
        }
    }
}

@Composable
fun RoutingRuleItem(
    item: RulesetItem,
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    var enabled by remember { mutableStateOf(item.enabled) }

    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.remarks ?: "", fontWeight = FontWeight.Bold)
                if (item.locked == true) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        supportingContent = {
            Column {
                val detail = listOfNotNull(
                    item.domain?.joinToString(", ")?.takeIf { it.isNotEmpty() },
                    item.ip?.joinToString(", ")?.takeIf { it.isNotEmpty() }
                ).joinToString(" | ")
                if (detail.isNotEmpty()) {
                    Text(detail, maxLines = 1)
                }
                Text(item.outboundTag, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        onToggleEnabled(it)
                    }
                )
            }
        }
    )
}
