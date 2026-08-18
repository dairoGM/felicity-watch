package com.dairoroberto.felicitywatch.ui.appliances

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.ApplianceEntity
import com.dairoroberto.felicitywatch.domain.usecase.ApplianceBackup
import com.dairoroberto.felicitywatch.domain.usecase.ApplianceEvent
import com.dairoroberto.felicitywatch.domain.usecase.RoomConsumption
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val TABS = listOf("Inventario", "Actividad", "Por local")

/** Filas que se renderizan por página en las listas con scroll infinito. */
private const val ACTIVITY_PAGE_SIZE = 20
private const val CATALOG_PAGE_SIZE = 20

/**
 * Inventario de equipos de la casa, clasificados por local, con bitácora de
 * actividad y estadística de consumo estimado por local.
 *
 * Vive en "Más" y no en Reporte porque es principalmente configuración: el
 * usuario registra su casa una vez y luego consulta la actividad.
 */
@Composable
fun AppliancesScreen(viewModel: AppliancesViewModel = hiltViewModel()) {
    val appliances by viewModel.appliances.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    val events by viewModel.events.collectAsState()
    val roomStats by viewModel.roomStats.collectAsState()
    val thresholdWatts by viewModel.alertThresholdWatts.collectAsState()
    val similarGroups by viewModel.similarGroups.collectAsState()
    val learningMessage by viewModel.learningMessage.collectAsState()
    val colors = LocalFelicityColors.current

    val pendingExport by viewModel.pendingExport.collectAsState()
    val context = LocalContext.current

    // Se usan los selectores de archivo del SISTEMA (Storage Access
    // Framework): no necesitan permisos de almacenamiento y el usuario elige
    // dónde guardar o de dónde leer, incluyendo Drive o una tarjeta SD.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExport
        if (uri != null && json != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                } ?: error("sin stream de escritura")
            }.isSuccess
            if (ok) viewModel.onExportWritten(appliances.size) else viewModel.onExportFailed()
        }
        viewModel.clearPendingExport()
    }

    // El JSON se genera antes de abrir el selector, y el lanzamiento espera a
    // tenerlo: así el callback nunca se encuentra sin contenido que escribir.
    LaunchedEffect(pendingExport) {
        pendingExport?.let {
            exportLauncher.launch("${ApplianceBackup.FILE_PREFIX}-${System.currentTimeMillis()}.json")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        // Se aceptan varios MIME porque muchos gestores de archivos y Drive
        // reportan un .json como octet-stream o text/plain, y filtrar solo
        // por application/json dejaría el archivo invisible al elegirlo.
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (json.isNullOrBlank()) viewModel.onImportReadFailed()
            else viewModel.importInventory(json)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    // Aviso de lo aprendido: confirmar un equipo puede haber ajustado varios
    // del mismo grupo, y eso conviene decirlo en vez de cambiarlo en silencio.
    LaunchedEffect(learningMessage) {
        learningMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearLearningMessage()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<ApplianceEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ApplianceEntity?>(null) }
    var confirmClearActivity by remember { mutableStateOf(false) }
    var eventDetail by remember { mutableStateOf<ApplianceEvent?>(null) }
    var pendingResetLearning by remember { mutableStateOf<ApplianceEntity?>(null) }

    editing?.let { appliance ->
        ApplianceEditDialog(
            appliance = appliance,
            knownRooms = rooms,
            knownGroups = similarGroups,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            }
        )
    }

    eventDetail?.let { event ->
        EventMatchDialog(
            event = event,
            appliances = appliances,
            onConfirm = { appliance ->
                viewModel.confirmEventAppliance(event, appliance)
                eventDetail = null
            },
            onDismiss = { eventDetail = null }
        )
    }

    if (confirmClearActivity) {
        AlertDialog(
            onDismissRequest = { confirmClearActivity = false },
            title = { Text("¿Borrar toda la actividad?") },
            text = {
                Text(
                    "Se ocultarán los ${events.size} cambios de consumo detectados. No se borra el " +
                        "historial de lecturas, solo esta bitácora."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissAllEvents()
                    confirmClearActivity = false
                }) { Text("Borrar todo") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearActivity = false }) { Text("Cancelar") }
            }
        )
    }

    pendingResetLearning?.let { appliance ->
        // Si el equipo pertenece a un grupo, se ofrece borrar el grupo
        // completo: el aprendizaje se propagó a todos, así que deshacerlo uno
        // por uno sería tedioso y fácil de dejar a medias.
        val groupSize = if (appliance.hasSimilarGroup) {
            appliances.count { it.similarGroup.equals(appliance.similarGroup, ignoreCase = true) }
        } else 0

        AlertDialog(
            onDismissRequest = { pendingResetLearning = null },
            title = { Text("¿Borrar el consumo aprendido?") },
            text = {
                Column {
                    Text(
                        "${appliance.name} volverá a usar solo el consumo que declaraste " +
                            "(${appliance.minWatts}–${appliance.watts} W). Se pierden las " +
                            "${appliance.confirmationCount} confirmaciones acumuladas."
                    )
                    if (groupSize > 1) {
                        Text(
                            "Pertenece al grupo \"${appliance.similarGroup}\" con $groupSize equipos. " +
                                "Puedes borrar solo este o todo el grupo. Para cancelar, toca fuera " +
                                "de este cuadro.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMid,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (groupSize > 1) {
                    TextButton(onClick = {
                        viewModel.resetSimilarGroupLearning(appliance.similarGroup)
                        pendingResetLearning = null
                    }) { Text("Todo el grupo") }
                } else {
                    TextButton(onClick = {
                        viewModel.resetLearning(appliance)
                        pendingResetLearning = null
                    }) { Text("Borrar") }
                }
            },
            dismissButton = {
                if (groupSize > 1) {
                    TextButton(onClick = {
                        viewModel.resetLearning(appliance)
                        pendingResetLearning = null
                    }) { Text("Solo este") }
                } else {
                    TextButton(onClick = { pendingResetLearning = null }) { Text("Cancelar") }
                }
            }
        )
    }

    pendingDelete?.let { appliance ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Eliminar ${appliance.name}?") },
            text = { Text("Se perderá su consumo declarado y lo aprendido, y dejará de proponerse en la actividad.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(appliance)
                    pendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { editing = ApplianceEntity(name = "", watts = 0) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar equipo")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = colors.surface2) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            when (selectedTab) {
                0 -> Column(Modifier.fillMaxSize()) {
                    // Respaldo del inventario: pasar la casa registrada a otro
                    // teléfono sin volver a escribir cada equipo.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.prepareExport() },
                            enabled = appliances.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "Exportar",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 5.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                // Varios MIME: un .json puede llegar declarado
                                // como octet-stream o text/plain según el
                                // gestor de archivos o Drive.
                                importLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream")
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "Importar",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 5.dp)
                            )
                        }
                    }

                    ApplianceCatalogList(
                        appliances = appliances,
                        onEdit = { editing = it },
                        onDelete = { pendingDelete = it },
                        onResetLearning = { pendingResetLearning = it }
                    )
                }
                1 -> ApplianceActivityList(
                    events = events,
                    hasCatalog = appliances.isNotEmpty(),
                    onDismiss = { viewModel.dismissEvent(it) },
                    onClearAll = { confirmClearActivity = true },
                    onOpenDetail = { eventDetail = it },
                    thresholdWatts = thresholdWatts
                )
                2 -> RoomStatsList(stats = roomStats, hasCatalog = appliances.isNotEmpty())
            }
        }
    }
}

/** Inventario agrupado por local; los sin asignar van al final. */
@Composable
private fun ApplianceCatalogList(
    appliances: List<ApplianceEntity>,
    onEdit: (ApplianceEntity) -> Unit,
    onDelete: (ApplianceEntity) -> Unit,
    onResetLearning: (ApplianceEntity) -> Unit
) {
    val colors = LocalFelicityColors.current

    if (appliances.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Todavía no registraste ningún equipo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textHi,
                modifier = Modifier.padding(top = 40.dp)
            )
            Text(
                "Registra los equipos grandes de tu casa con su local y consumo aproximado. Luego " +
                    "confirma cada uno midiendo con la app: enciéndelo y la app calcula su consumo real.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    // Locales existentes, para las pastillas del filtro.
    val rooms = remember(appliances) {
        appliances.map { it.room }
            .distinctBy { it.trim().lowercase() }
            .sortedWith(compareBy({ it.isBlank() }, { it.lowercase() }))
    }

    // null = "Todos". Se compara normalizado para que el filtro no se rompa
    // por diferencias de mayúsculas.
    var roomFilter by remember { mutableStateOf<String?>(null) }
    // Se descarta el filtro si su local desaparece (último equipo borrado),
    // porque dejaría la lista vacía sin motivo visible.
    if (roomFilter != null && rooms.none { it.trim().lowercase() == roomFilter!!.trim().lowercase() }) {
        roomFilter = null
    }

    // Se numera DESPUÉS de filtrar, para que la numeración sea siempre
    // continua (1..n) sobre lo que se está viendo: al filtrar por un local
    // mostrar los números que esos equipos tienen en el inventario completo
    // daría una secuencia con huecos (36, 25, 41…) que no ayuda a nada.
    // Descendente: el último equipo registrado del grupo lleva el número
    // más alto.
    val filtered = remember(appliances, roomFilter) {
        val target = roomFilter?.trim()?.lowercase()
        val visible = if (target == null) appliances
        else appliances.filter { it.room.trim().lowercase() == target }
        val count = visible.size
        visible.mapIndexed { index, appliance -> (count - index) to appliance }
    }

    var visibleCount by remember(roomFilter) { mutableIntStateOf(CATALOG_PAGE_SIZE) }
    val shown = remember(filtered, visibleCount) { filtered.take(visibleCount) }

    // Filas expandidas, por id. Colapsadas por defecto: el objetivo de
    // colapsar es que quepan más equipos en pantalla. Se usa un mapa como
    // conjunto porque mutableStateSetOf no existe en el BOM de Compose que
    // usa el proyecto.
    val expandedIds = remember { mutableStateMapOf<Long, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        if (rooms.size > 1) {
            RoomFilterRow(
                rooms = rooms,
                selected = roomFilter,
                totalCount = appliances.size,
                countFor = { room ->
                    appliances.count { it.room.trim().lowercase() == room.trim().lowercase() }
                },
                onSelect = { roomFilter = it }
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shown, key = { "appliance-${it.second.id}" }) { (number, appliance) ->
                ApplianceRow(
                    number = number,
                    appliance = appliance,
                    expanded = expandedIds[appliance.id] == true,
                    onToggleExpanded = {
                        if (expandedIds[appliance.id] == true) expandedIds.remove(appliance.id)
                        else expandedIds[appliance.id] = true
                    },
                    onEdit = { onEdit(appliance) },
                    onDelete = { onDelete(appliance) },
                    onResetLearning = { onResetLearning(appliance) }
                )
            }

            if (shown.size < filtered.size) {
                item(key = "catalog-load-more") {
                    LaunchedEffect(shown.size) { visibleCount += CATALOG_PAGE_SIZE }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "Cargando más…",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Pastillas de filtro por local, cada una con el color de su habitación. */
@Composable
private fun RoomFilterRow(
    rooms: List<String>,
    selected: String?,
    totalCount: Int,
    countFor: (String) -> Int,
    onSelect: (String?) -> Unit
) {
    val colors = LocalFelicityColors.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "filter-all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Todos ($totalCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.tealDim
                )
            )
        }
        items(rooms, key = { "filter-${it.trim().lowercase()}" }) { room ->
            val tint = roomColor(room)
            val isSelected = selected?.trim()?.lowercase() == room.trim().lowercase()
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else room) },
                label = { Text("${room.ifBlank { "Sin local" }} (${countFor(room)})") },
                leadingIcon = {
                    // Punto del color del local: ata la pastilla a la barra
                    // lateral de sus equipos.
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tint)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = tint.copy(alpha = 0.28f)
                )
            )
        }
    }
}

/**
 * Fila de equipo, colapsable.
 *
 * Colapsada muestra solo lo que sirve para encontrarlo (número, nombre,
 * local y acciones); expandida agrega el consumo declarado. El color de la
 * barra lateral y del local vienen de [roomColor], así los equipos de una
 * misma habitación se reconocen sin leer.
 */
@Composable
private fun ApplianceRow(
    number: Int,
    appliance: ApplianceEntity,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResetLearning: () -> Unit
) {
    val colors = LocalFelicityColors.current
    val roomTint = roomColor(appliance.room)

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Barra lateral del color del local: identifica la habitación
            // aunque la fila esté colapsada.
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(roomTint)
            )

            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Número de orden del equipo en el inventario.
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(roomTint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            number.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = roomTint
                        )
                    }

                    // Toda la zona de texto alterna colapsado/expandido; para
                    // editar están el icono de lápiz y el detalle expandido.
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                            .clickable(onClick = onToggleExpanded)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                appliance.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textHi,
                                maxLines = 1
                            )
                            if (appliance.isConfirmed) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Consumo confirmado",
                                    tint = colors.green,
                                    modifier = Modifier.padding(start = 5.dp).size(14.dp)
                                )
                            }
                        }
                        Text(
                            appliance.room.ifBlank { "Sin local" },
                            style = MaterialTheme.typography.labelSmall,
                            color = roomTint,
                            maxLines = 1
                        )
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar ${appliance.name}",
                            tint = colors.textMid,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar ${appliance.name}",
                            tint = colors.textLow,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Contraer" else "Ver detalle",
                            tint = colors.textMid,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (expanded) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = colors.hairline
                    )
                    Text(
                        buildString {
                            if (appliance.hasRange) {
                                append("${appliance.minWatts}–${appliance.watts} W declarados")
                            } else {
                                append("${appliance.watts} W declarados")
                            }
                            append(" · ±${appliance.toleranceWatts} W de tolerancia")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid
                    )
                    // Cuando no hay nada aprendido se dice explícitamente, en
                    // vez de no mostrar nada: así se entiende por qué no
                    // aparece el botón de borrar la medición.
                    if (!appliance.isConfirmed) {
                        Text(
                            "Sin consumo aprendido todavía · confírmalo desde la pestaña Actividad " +
                                "cuando la app detecte que se encendió.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (appliance.isConfirmed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    buildString {
                                        append("${appliance.confirmedWatts} W aprendidos")
                                        if (appliance.confirmationCount > 1) {
                                            append(" · ${appliance.confirmationCount} confirmaciones")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.green
                                )
                                if (appliance.hasSimilarGroup) {
                                    Text(
                                        "Grupo: ${appliance.similarGroup}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textLow
                                    )
                                }
                            }
                            // Salida cuando el aprendizaje se contaminó: sin
                            // esto, un valor mal aprendido queda arrastrándose
                            // en el promedio de todas las confirmaciones
                            // siguientes.
                            TextButton(onClick = onResetLearning) {
                                Text(
                                    "Borrar medición",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.error
                                )
                            }
                        }
                    }
                    if (appliance.hasSimilarGroup) {
                        Text(
                            "Confirmar este equipo ajusta a todos los de su grupo.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bitácora de escalones de consumo de las últimas 24 h, del más reciente al
 * más viejo, con scroll infinito: se renderiza por páginas y se agrega otra
 * al llegar al final, para no montar cientos de tarjetas de golpe.
 */
@Composable
private fun ApplianceActivityList(
    events: List<ApplianceEvent>,
    hasCatalog: Boolean,
    onDismiss: (ApplianceEvent) -> Unit,
    onClearAll: () -> Unit,
    onOpenDetail: (ApplianceEvent) -> Unit,
    thresholdWatts: Int
) {
    val colors = LocalFelicityColors.current
    val hourFormatter = remember { DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES")) }
    val zone = remember { ZoneId.systemDefault() }

    var visibleCount by remember { mutableIntStateOf(ACTIVITY_PAGE_SIZE) }
    // Si la lista cambia (se borró un evento, llegaron lecturas nuevas), el
    // tope se recorta para no quedar apuntando más allá del final.
    val shown = remember(events, visibleCount) { events.take(visibleCount) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "activity-header") {
            Column {
                Text(
                    "Últimas 24 horas · saltos de más de $thresholdWatts W. " +
                        "La app no puede identificar equipos con certeza: propone el más parecido de tu inventario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid
                )
                if (events.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(
                            "${events.size} cambio${if (events.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearAll) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "Borrar todo",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Text(
                    if (hasCatalog) "No se detectaron cambios bruscos de consumo en las últimas 24 horas."
                    else "Registra tus equipos en la pestaña Inventario para que la app pueda proponer coincidencias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
            return@LazyColumn
        }

        items(shown, key = { it.epochMillis }) { event ->
            // Encendido en verde, apagado en rojo: el color acompana al hecho
            // (equipo activo vs. equipo detenido), no al signo del salto de
            // consumo, que es lo que confundia antes.
            val accent = if (event.turnedOn) colors.green else colors.error
            val zoned = Instant.ofEpochMilli(event.epochMillis).atZone(zone)
            val ampm = if (zoned.hour < 12) "am" else "pm"

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface2),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(event) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            // Símbolo universal de encendido vs. enchufe desconectado:
                            // comunica el hecho directamente, mientras que una flecha
                            // arriba/abajo solo indica que el consumo subió o bajó y
                            // deja al usuario deducir el resto.
                            if (event.turnedOn) Icons.Default.PowerSettingsNew else Icons.Default.PowerOff,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        val match = event.matchedAppliance
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildString {
                                    append(if (event.turnedOn) "Se encendió" else "Se apagó")
                                    if (match != null) {
                                        append(" · ")
                                        append(match.name)
                                    } else {
                                        append(" algo")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textHi
                            )
                            // Un equipo confirmado da más confianza a la
                            // propuesta que uno con consumo solo declarado.
                            if (match?.isConfirmed == true) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Consumo confirmado",
                                    tint = colors.green,
                                    modifier = Modifier.padding(start = 4.dp).size(12.dp)
                                )
                            }
                        }
                        if (match != null && match.room.isNotBlank()) {
                            Text(
                                match.room,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent
                            )
                        }
                        Text(
                            "${if (event.deltaWatts > 0) "+" else ""}${event.deltaWatts} W · total ${event.totalWattsAfter} W",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMid,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (event.candidateCount > 1) {
                            Text(
                                "Coincide con ${event.candidateCount} equipos — no se puede distinguir",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                        } else if (event.matchedAppliance == null && hasCatalog) {
                            Text(
                                "Ningún equipo de tu inventario coincide",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${hourFormatter.format(zoned)}$ampm",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textMid
                        )
                        IconButton(onClick = { onDismiss(event) }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Borrar este registro",
                                tint = colors.textLow,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Centinela de scroll infinito: al componerse significa que el
        // usuario llegó al final de lo renderizado, así que se pide otra
        // página. No hay carga de red — solo se amplía el corte local.
        if (shown.size < events.size) {
            item(key = "activity-load-more") {
                LaunchedEffect(shown.size) { visibleCount += ACTIVITY_PAGE_SIZE }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "Cargando más…",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Detalle de un evento: con qué equipo(s) coincide el escalón de consumo.
 *
 * Muestra TODOS los candidatos, no solo el propuesto, porque cuando varios
 * equipos tienen consumo parecido la propuesta es una entre varias
 * igualmente posibles, y ocultar las demás daría una falsa sensación de
 * certeza.
 */
@Composable
private fun EventMatchDialog(
    event: ApplianceEvent,
    appliances: List<ApplianceEntity>,
    onConfirm: (ApplianceEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFelicityColors.current
    val magnitude = abs(event.deltaWatts)
    // Se recalculan los candidatos con la misma regla del detector: el
    // evento solo guarda cuántos eran, no cuáles.
    val candidates = remember(event, appliances) {
        appliances.filter { it.matches(magnitude) }.sortedBy { it.distanceTo(magnitude) }
    }
    val zone = remember { ZoneId.systemDefault() }
    val zoned = Instant.ofEpochMilli(event.epochMillis).atZone(zone)
    val hourFormatter = remember { DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES")) }
    val ampm = if (zoned.hour < 12) "am" else "pm"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (event.turnedOn) "Se encendió algo" else "Se apagó algo") },
        text = {
            Column {
                Text(
                    "${if (event.deltaWatts > 0) "+" else ""}${event.deltaWatts} W " +
                        "a las ${hourFormatter.format(zoned)}$ampm · total ${event.totalWattsAfter} W",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colors.hairline)

                if (candidates.isEmpty()) {
                    Text(
                        "Ningún equipo de tu inventario tiene un consumo cercano a $magnitude W.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textHi
                    )
                    Text(
                        "Puede ser un equipo que no registraste, o varios encendidos a la vez " +
                            "(el escalón sería la suma de todos).",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    Text(
                        if (candidates.size == 1) "¿Fue este equipo?"
                        else "¿Cuál de estos ${candidates.size} equipos fue?",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.textHi
                    )
                    Text(
                        "Al confirmarlo, la app aprende su consumo real y mejora las " +
                            "próximas detecciones.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    candidates.forEachIndexed { index, candidate ->
                        val tint = roomColor(candidate.room)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onConfirm(candidate) }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(tint)
                            )
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        candidate.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textHi
                                    )
                                    if (candidate.isConfirmed) {
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = "Consumo aprendido",
                                            tint = colors.green,
                                            modifier = Modifier.padding(start = 4.dp).size(12.dp)
                                        )
                                    }
                                    // El primero es el que la app propone en
                                    // la bitácora; se marca para que se vea
                                    // por qué se eligió ese y no otro.
                                    if (index == 0 && candidates.size > 1) {
                                        Text(
                                            "más cercano",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accent,
                                            modifier = Modifier.padding(start = 6.dp)
                                        )
                                    }
                                }
                                Text(
                                    buildString {
                                        if (candidate.room.isNotBlank()) {
                                            append(candidate.room)
                                            append(" · ")
                                        }
                                        if (candidate.isConfirmed) {
                                            append("${candidate.confirmedWatts} W aprendidos")
                                            if (candidate.confirmationCount > 1) {
                                                append(" (${candidate.confirmationCount} veces)")
                                            }
                                        } else if (candidate.hasRange) {
                                            append("${candidate.minWatts}–${candidate.watts} W declarados")
                                        } else {
                                            append("${candidate.watts} W declarados")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMid
                                )
                                // Avisa que confirmar este equipo ajustará
                                // también a sus similares, para que el
                                // usuario no se sorprenda del cambio.
                                if (candidate.hasSimilarGroup) {
                                    val groupSize = appliances.count {
                                        it.similarGroup.equals(candidate.similarGroup, ignoreCase = true)
                                    }
                                    if (groupSize > 1) {
                                        Text(
                                            "Ajustará los $groupSize equipos de \"${candidate.similarGroup}\"",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accent
                                        )
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { onConfirm(candidate) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Fue este", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text(
                        "Si no fue ninguno de estos, no confirmes nada: un dato equivocado " +
                            "empeoraría las detecciones siguientes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/** Consumo estimado por local en las últimas 24 h. */
@Composable
private fun RoomStatsList(stats: List<RoomConsumption>, hasCatalog: Boolean) {
    val colors = LocalFelicityColors.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Consumo ESTIMADO por local en las últimas 24 h, calculado con el tiempo que cada equipo " +
                    "estuvo encendido según los cambios detectados. Los que siguen encendidos cuentan " +
                    "su tiempo en curso. Es una aproximación para comparar locales entre sí, no una " +
                    "medición exacta.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (stats.isEmpty()) {
            item {
                Text(
                    if (hasCatalog) "Todavía no hay suficiente actividad detectada para estimar el consumo por local."
                    else "Registra tus equipos con su local en la pestaña Inventario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
            return@LazyColumn
        }

        val maxKwh = stats.maxOf { it.totalKwh }.coerceAtLeast(0.001)

        items(stats, key = { it.room }) { roomStat ->
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface2),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Default.MeetingRoom,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            roomStat.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textHi,
                            modifier = Modifier.padding(start = 6.dp).weight(1f)
                        )
                        Text(
                            String.format(Locale("es", "ES"), "~%.2f kWh", roomStat.totalKwh),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }

                    // Barra proporcional al local que más consumió, para
                    // comparar de un vistazo.
                    LinearProgressIndicator(
                        progress = { (roomStat.totalKwh / maxKwh).toFloat().coerceIn(0f, 1f) },
                        color = colors.accent,
                        trackColor = colors.hairline.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 8.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.hairline)

                    roomStat.appliances.forEach { usage ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    usage.appliance.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textHi
                                )
                                Text(
                                    buildString {
                                        val h = usage.minutesOn / 60
                                        val m = usage.minutesOn % 60
                                        if (h > 0) append("${h}h ${m}min") else append("${m}min")
                                        append(" · ${usage.timesOn} vez${if (usage.timesOn == 1) "" else "ces"}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textLow
                                )
                            }
                            Text(
                                String.format(Locale("es", "ES"), "~%.2f kWh", usage.estimatedKwh),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = colors.textMid
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formulario de equipo. El campo de local usa AUTOCOMPLETADO sobre los
 * locales ya existentes, para que no convivan "Sala" y "sala" como locales
 * distintos.
 */
@Composable
private fun ApplianceEditDialog(
    appliance: ApplianceEntity,
    knownRooms: List<String>,
    knownGroups: List<String>,
    onDismiss: () -> Unit,
    onSave: (ApplianceEntity) -> Unit
) {
    var name by remember { mutableStateOf(appliance.name) }
    var room by remember { mutableStateOf(appliance.room) }
    var similarGroup by remember { mutableStateOf(appliance.similarGroup) }
    var maxWatts by remember { mutableStateOf(if (appliance.watts > 0) appliance.watts.toString() else "") }
    var variableConsumption by remember { mutableStateOf(appliance.hasRange) }
    var minWatts by remember {
        mutableStateOf(if (appliance.hasRange) appliance.minWatts.toString() else "")
    }
    var tolerance by remember { mutableStateOf(appliance.toleranceWatts.toString()) }

    val maxValue = maxWatts.toIntOrNull() ?: 0
    val minValue = if (variableConsumption) (minWatts.toIntOrNull() ?: 0) else maxValue
    val toleranceValue = tolerance.toIntOrNull() ?: ApplianceEntity.DEFAULT_TOLERANCE_WATTS
    val rangeInvalid = variableConsumption && minValue > 0 && maxValue > 0 && minValue > maxValue
    val canSave = name.isNotBlank() && maxValue > 0 &&
        (!variableConsumption || minValue > 0) && !rangeInvalid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (appliance.id == 0L) "Nuevo equipo" else "Editar equipo") },
        text = {
            LazyColumn(modifier = Modifier.imePadding()) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    RoomAutocompleteField(
                        value = room,
                        knownRooms = knownRooms,
                        onValueChange = { room = it },
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Grupo de equipos similares: lo que permite que
                    // confirmar uno ajuste el consumo de todos los del mismo
                    // tipo (varios splits convencionales, por ejemplo).
                    RoomAutocompleteField(
                        value = similarGroup,
                        knownRooms = knownGroups,
                        onValueChange = { similarGroup = it },
                        modifier = Modifier.padding(top = 8.dp),
                        label = "Tipo de equipo similar",
                        helperText = "Opcional · equipos con el MISMO consumo (ej. \"split convencional\"). " +
                            "Al confirmar uno, se ajustan todos los del grupo."
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = variableConsumption,
                            onCheckedChange = { variableConsumption = it }
                        )
                        Column(Modifier.padding(start = 4.dp)) {
                            Text("Consumo variable (inverter)", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Splits y neveras inverter modulan su consumo",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (variableConsumption) {
                        OutlinedTextField(
                            value = minWatts,
                            onValueChange = { minWatts = it.filter { c -> c.isDigit() } },
                            label = { Text("Consumo mínimo (W)") },
                            supportingText = { Text("Manteniendo temperatura") },
                            singleLine = true,
                            isError = rangeInvalid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = maxWatts,
                        onValueChange = { maxWatts = it.filter { c -> c.isDigit() } },
                        label = { Text(if (variableConsumption) "Consumo máximo (W)" else "Consumo (W)") },
                        supportingText = {
                            Text(
                                if (variableConsumption) "Arranque o enfriamiento fuerte"
                                else "Aproximado — luego lo confirmas midiendo"
                            )
                        },
                        singleLine = true,
                        isError = rangeInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )

                    if (rangeInvalid) {
                        Text(
                            "El mínimo no puede ser mayor que el máximo.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    OutlinedTextField(
                        value = tolerance,
                        onValueChange = { tolerance = it.filter { c -> c.isDigit() } },
                        label = { Text("Holgura (± W)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        appliance.copy(
                            name = name.trim(),
                            room = room.trim(),
                            similarGroup = similarGroup.trim(),
                            watts = maxValue,
                            minWatts = if (variableConsumption) minValue else maxValue,
                            toleranceWatts = toleranceValue.coerceAtLeast(10)
                        )
                    )
                },
                enabled = canSave
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Campo de local con autocompletado. Muestra un desplegable con los
 * locales existentes que coinciden con lo escrito; al elegir uno se usa su
 * grafía EXACTA, que es lo que evita duplicados por mayúsculas o acentos.
 */
@Composable
private fun RoomAutocompleteField(
    value: String,
    knownRooms: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Local / habitación",
    helperText: String = "Opcional · se autocompleta con los que ya usaste"
) {
    val colors = LocalFelicityColors.current
    var expanded by remember { mutableStateOf(false) }

    val suggestions = remember(value, knownRooms) {
        if (value.isBlank()) knownRooms
        else knownRooms.filter {
            it.contains(value.trim(), ignoreCase = true) && !it.equals(value.trim(), ignoreCase = true)
        }
    }

    // Aviso de duplicado por grafía: el usuario escribió "sala" y ya existe
    // "Sala". Se ofrece usar el existente en vez de crear otro local.
    val conflicting = remember(value, knownRooms) {
        knownRooms.firstOrNull { it.equals(value.trim(), ignoreCase = true) && it != value.trim() }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = true
                },
                label = { Text(label) },
                supportingText = { Text(helperText) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = expanded && suggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
                // Sin robar el foco al campo de texto mientras se escribe.
                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
            ) {
                suggestions.take(6).forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (conflicting != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = colors.textMid,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    "Ya existe \"$conflicting\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(start = 4.dp).weight(1f)
                )
                TextButton(onClick = { onValueChange(conflicting) }) {
                    Text("Usar ese", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
