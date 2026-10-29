package dev.sshmobile.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sshmobile.R
import dev.sshmobile.core.session.SessionStatus
import dev.sshmobile.domain.model.AuthType
import dev.sshmobile.domain.model.Server
import dev.sshmobile.ui.LocalAppContainer
import dev.sshmobile.ui.SimpleViewModelFactory
import dev.sshmobile.ui.components.AccentTag
import dev.sshmobile.ui.components.ConfirmDialog
import dev.sshmobile.ui.components.EmptyState
import dev.sshmobile.ui.components.SectionLabel
import dev.sshmobile.ui.components.StatusDot
import dev.sshmobile.ui.components.statusLabel
import dev.sshmobile.ui.theme.GreenAccent
import dev.sshmobile.ui.theme.GreenSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onConnect: (String) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: DashboardViewModel = viewModel(
        factory = SimpleViewModelFactory {
            DashboardViewModel(container.serverRepository, container.sessionManager)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Server?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when {
                                state.loading -> stringResource(R.string.dashboard_loading_servers)
                                state.totalServers == 0 -> stringResource(R.string.dashboard_no_saved_servers)
                                else -> pluralStringResource(
                                    R.plurals.dashboard_saved_servers,
                                    state.totalServers,
                                    state.totalServers,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = GreenAccent,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddServer,
                containerColor = GreenAccent,
                contentColor = Color(0xFF00210F),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.dashboard_add_server))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.dashboard_search_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            if (state.isEmpty) {
                EmptyState(
                    title = stringResource(R.string.dashboard_empty_title),
                    message = stringResource(R.string.dashboard_empty_message),
                    actionLabel = stringResource(R.string.dashboard_add_server),
                    onAction = onAddServer,
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            if (state.noResults) {
                EmptyState(
                    title = stringResource(R.string.dashboard_no_results_title),
                    message = stringResource(R.string.dashboard_no_results_message, state.query),
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            ) {
                if (state.favorites.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.dashboard_favorites)) }
                    items(state.favorites, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            sessionStatus = state.activeSessions[server.id]?.status,
                            onClick = { onConnect(viewModel.openSession(server.id)) },
                            onToggleFavorite = { viewModel.toggleFavorite(server) },
                            onEdit = { onEditServer(server.id) },
                            onDelete = { deleteTarget = server },
                        )
                    }
                }

                state.groups.forEach { group ->
                    if (group.servers.isNotEmpty()) {
                        item(key = "header-${group.name ?: "ungrouped"}") {
                            SectionLabel(group.name ?: stringResource(R.string.dashboard_ungrouped))
                        }
                        items(group.servers, key = { it.id }) { server ->
                            ServerCard(
                                server = server,
                                sessionStatus = state.activeSessions[server.id]?.status,
                                onClick = { onConnect(viewModel.openSession(server.id)) },
                                onToggleFavorite = { viewModel.toggleFavorite(server) },
                                onEdit = { onEditServer(server.id) },
                                onDelete = { deleteTarget = server },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.dashboard_delete_title),
            message = stringResource(R.string.dashboard_delete_message, target.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.delete(target) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ServerCard(
    server: Server,
    sessionStatus: SessionStatus?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GreenAccent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = GreenAccent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${server.username}@${server.host}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (server.favorite) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                        contentDescription = stringResource(
                            if (server.favorite) R.string.dashboard_remove_favorite
                            else R.string.dashboard_add_favorite,
                        ),
                        tint = if (server.favorite) GreenSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.dashboard_server_actions),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (server.favorite) R.string.dashboard_remove_favorite
                                        else R.string.dashboard_add_favorite,
                                    ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (server.favorite) Icons.Outlined.StarOutline else Icons.Rounded.Star,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onToggleFavorite()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (server.groupName != null) AccentTag(server.groupName)
                AccentTag(
                    if (server.authType == AuthType.PRIVATE_KEY) {
                        stringResource(R.string.auth_key_tag)
                    } else {
                        stringResource(R.string.auth_password_tag)
                    },
                    Modifier,
                )
                Spacer(Modifier.weight(1f))
                if (sessionStatus != null) {
                    StatusDot(sessionStatus)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusLabel(sessionStatus),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (server.authType == AuthType.PRIVATE_KEY) {
                                Icons.Outlined.Key
                            } else {
                                Icons.Outlined.Lock
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.dashboard_tap_to_connect),
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenSoft,
                        )
                    }
                }
            }
        }
    }
}
