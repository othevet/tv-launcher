package fr.olivierthevet.tvlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Background = Color(0xFF0B0F17)
private val CardColor = Color(0xFF161C28)
// Effet verre (façon Apple TV) : voile blanc translucide et liseré clair en haut.
private val GlassFill = Color.White.copy(alpha = 0.08f)
private val GlassFocused = Color.White.copy(alpha = 0.18f)
private val GlassStroke = BorderStroke(
    1.dp,
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.06f))),
)
private val Accent = Color(0xFF60A5FA)
private val TextMuted = Color(0xFF9CA3AF)
private val ScreenMargin = 48.dp
// Colonne de droite : la météo et la rangée VPN / Sources / Paramètres ont la même largeur.
private val SideWidth = 300.dp
private val SideButtonHeight = 40.dp
private val VpnOn = Color(0xFF34D399)
private val VpnOff = Color(0xFF6B7280)
private val Warning = Color(0xFFFBBF24)

private const val KEY_HEADER = "header"
private const val KEY_ADD = "add"

@Composable
fun HomeScreen(refreshKey: Int) {
    val context = LocalContext.current
    // Chargé dès la première composition : si la grille démarre vide, une tuile
    // "action" prend le focus par défaut et la grille reste ancrée dessus.
    var allApps by remember { mutableStateOf(loadAllApps(context)) }
    var pinned by remember { mutableStateOf(loadPinned(context)) }
    var picking by remember { mutableStateOf(false) }
    // Appui long sur une app : menu (Déplacer / Retirer), puis éventuellement mode déplacement.
    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    // Entrées HDMI listées dans le menu "Sources" (null = menu fermé).
    var sources by remember { mutableStateOf<List<TvSource>?>(null) }
    val weather = rememberWeather()
    val homeApps = pinned.mapNotNull { pkg -> allApps.find { it.component.packageName == pkg } }
    val gridState = rememberLazyGridState()

    // Tuile à focaliser au prochain affichage de l'accueil (null = première tuile).
    val focusRequester = remember { FocusRequester() }
    var focusKey by remember { mutableStateOf<String?>(null) }
    var focusPending by remember { mutableStateOf(true) }
    val targetKey = focusKey ?: homeApps.firstOrNull()?.component?.packageName ?: KEY_ADD

    // Sur l'accueil, "Retour" ne doit rien faire (sinon écran noir).
    BackHandler { }
    BackHandler(enabled = picking) {
        picking = false
        focusKey = KEY_ADD
        focusPending = true
    }
    BackHandler(enabled = moving != null) {
        moving = null
    }

    fun updatePinned(packages: List<String>) {
        pinned = packages
        savePinned(context, packages)
    }

    // Décale l'app déplacée de `offset` places (±1 gauche/droite, ±colonnes haut/bas).
    fun moveBy(pkg: String, offset: Int) {
        val visible = homeApps.map { it.component.packageName }
        val from = visible.indexOf(pkg)
        val to = (from + offset).coerceIn(0, visible.lastIndex)
        if (from < 0 || to == from) return
        val reordered = visible.toMutableList().apply { add(to, removeAt(from)) }
        // Les apps épinglées mais désinstallées restent à la fin, pour réapparaître si réinstallées.
        updatePinned(reordered + pinned.filter { it !in visible })
        focusKey = pkg
        focusPending = true
    }

    LaunchedEffect(refreshKey) {
        allApps = loadAllApps(context)
    }
    // La tuile n'existe qu'après le layout de la grille (et seulement si elle est
    // visible) : on y fait défiler puis on réessaie quelques frames. Et seulement une
    // fois la fenêtre active, sinon Android réattribue le focus par défaut juste après.
    // Au retour d'une app, focusPending est faux : on garde la tuile quittée.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focusPending, windowFocused, picking) {
        if (!focusPending || !windowFocused || picking) return@LaunchedEffect
        val index = when (targetKey) {
            KEY_ADD -> homeApps.size + 1
            else -> homeApps.indexOfFirst { it.component.packageName == targetKey } + 1
        }
        repeat(10) {
            withFrameNanos { }
            gridState.scrollToItem(index.coerceAtLeast(0))
            withFrameNanos { }
            if (focusRequester.requestFocus()) {
                focusPending = false
                return@LaunchedEffect
            }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = CardColor)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            Wallpaper(weather.weather, bottom = Background)

            if (picking) {
                AppPicker(
                    apps = allApps.filter { it.component.packageName !in pinned },
                    onPick = { app ->
                        val pkg = app.component.packageName
                        updatePinned(pinned + pkg)
                        picking = false
                        focusKey = pkg
                        focusPending = true
                    },
                )
                return@Box
            }

            // La grille va d'un bord à l'autre (marge dans contentPadding) pour que
            // la tuile agrandie au focus ne soit pas rognée.
            Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenMargin),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Clock()
                    Column(Modifier.width(SideWidth)) {
                        WeatherCard(weather, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            VpnStatus(Modifier.weight(1f)) { launchSurfshark(context) }
                            SideButton("⇄") { sources = loadHdmiSources(context) }
                            SideButton("⚙") { launchSettings(context) }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Comme sur Apple TV : les tuiles passent à la ligne quand la largeur est pleine.
                // Tant qu'il y a peu de lignes, elles restent calées en bas de l'écran.
                AppGrid(gridState, Modifier.weight(1f), bottomAligned = true) {
                    item(key = KEY_HEADER, span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(
                            if (moving != null) "Déplacement : flèches pour bouger l'app · OK pour valider"
                            else "Mes apps"
                        )
                    }
                    items(homeApps, key = { it.component.packageName }) { app ->
                        val pkg = app.component.packageName
                        val isMoving = pkg == moving
                        AppTile(
                            app,
                            Modifier
                                .focusTarget(pkg == targetKey, focusRequester)
                                .then(
                                    if (isMoving) Modifier.onPreviewKeyEvent { event ->
                                        val columns = gridState.layoutInfo.maxSpan.coerceAtLeast(1)
                                        val offset = when (event.key) {
                                            Key.DirectionLeft -> -1
                                            Key.DirectionRight -> 1
                                            Key.DirectionUp -> -columns
                                            Key.DirectionDown -> columns
                                            else -> return@onPreviewKeyEvent false
                                        }
                                        // On consomme aussi le KeyUp, sinon le focus bouge quand même.
                                        if (event.type == KeyEventType.KeyDown) moveBy(pkg, offset)
                                        true
                                    } else Modifier
                                ),
                            highlighted = isMoving,
                            onLongClick = { if (moving == null) menuFor = app },
                        ) {
                            if (isMoving) moving = null else launchApp(context, app)
                        }
                    }
                    item(key = KEY_ADD) {
                        ActionTile("+", "Ajouter", Modifier.focusTarget(targetKey == KEY_ADD, focusRequester)) {
                            picking = true
                        }
                    }
                }
            }

            sources?.let { list ->
                val close = { sources = null }
                MenuDialog(
                    title = "Sources",
                    message = if (list.isEmpty()) "Aucune entrée HDMI branchée." else null,
                    onDismiss = close,
                    items = list.map { source ->
                        source.label to {
                            close()
                            launchSource(context, source)
                        }
                    } + ("Annuler" to close),
                )
            }

            menuFor?.let { app ->
                val pkg = app.component.packageName
                AppMenu(
                    app = app,
                    onDismiss = { menuFor = null },
                    onMove = {
                        menuFor = null
                        moving = pkg
                        focusKey = pkg
                        focusPending = true
                    },
                    onRemove = {
                        menuFor = null
                        // Focus sur la voisine (suivante, sinon précédente, sinon "+").
                        val index = homeApps.indexOf(app)
                        val neighbour = homeApps.getOrNull(index + 1) ?: homeApps.getOrNull(index - 1)
                        updatePinned(pinned - pkg)
                        focusKey = neighbour?.component?.packageName ?: KEY_ADD
                        focusPending = true
                    },
                )
            }
        }
    }
}

private fun Modifier.focusTarget(isTarget: Boolean, requester: FocusRequester) =
    if (isTarget) focusRequester(requester) else this

/** Liste des apps installées pas encore sur l'accueil. "Retour" ferme sans rien ajouter. */
@Composable
private fun AppPicker(apps: List<TvApp>, onPick: (TvApp) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            withFrameNanos { }
            if (firstFocus.requestFocus()) return@LaunchedEffect
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text(
            "Ajouter une app",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = ScreenMargin),
        )
        Text(
            "Choisis une app à mettre sur l'accueil · Retour pour annuler",
            color = TextMuted,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = ScreenMargin, vertical = 8.dp),
        )
        if (apps.isEmpty()) {
            Text(
                "Toutes les apps sont déjà sur l'accueil.",
                color = TextMuted,
                fontSize = 18.sp,
                modifier = Modifier.padding(ScreenMargin),
            )
            return@Column
        }
        AppGrid(rememberLazyGridState(), Modifier.weight(1f), bottomAligned = false) {
            items(apps, key = { it.component.packageName }) { app ->
                AppTile(app, Modifier.focusTarget(app == apps.first(), firstFocus)) { onPick(app) }
            }
        }
    }
}

@Composable
private fun AppMenu(app: TvApp, onDismiss: () -> Unit, onMove: () -> Unit, onRemove: () -> Unit) {
    MenuDialog(
        title = app.label,
        onDismiss = onDismiss,
        items = listOf(
            "Déplacer" to onMove,
            "Retirer de l'accueil" to onRemove,
            "Annuler" to onDismiss,
        ),
    )
}

/** Petite fenêtre avec un titre et une liste de choix (focus sur le premier). */
@Composable
private fun MenuDialog(
    title: String,
    onDismiss: () -> Unit,
    items: List<Pair<String, () -> Unit>>,
    message: String? = null,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            withFrameNanos { }
            if (firstFocus.requestFocus()) return@LaunchedEffect
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(360.dp)
                .background(CardColor.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                .border(GlassStroke, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            message?.let { Text(it, color = TextMuted, fontSize = 16.sp) }
            Spacer(Modifier.height(4.dp))
            items.forEachIndexed { index, (label, onClick) ->
                MenuItem(label, if (index == 0) Modifier.focusRequester(firstFocus) else Modifier, onClick)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GlassFill,
            contentColor = Color.White,
            focusedContainerColor = Accent,
            focusedContentColor = Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Text(label, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
    }
}

@Composable
private fun AppGrid(
    state: LazyGridState,
    modifier: Modifier,
    bottomAligned: Boolean,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.FixedSize(TileWidth),
        state = state,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        verticalArrangement = Arrangement.spacedBy(8.dp, if (bottomAligned) Alignment.Bottom else Alignment.Top),
        contentPadding = PaddingValues(horizontal = ScreenMargin, vertical = 24.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    return now
}

private fun LocalDateTime.hourText() = format(DateTimeFormatter.ofPattern("HH:mm"))

private fun LocalDateTime.dateText() = format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
    .replaceFirstChar { it.titlecase(Locale.FRENCH) }

@Composable
private fun Clock() {
    val now = rememberNow()
    Column {
        Text(now.hourText(), color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
        Text(now.dateText(), color = TextMuted, fontSize = 20.sp)
    }
}

@Composable
private fun WeatherCard(state: WeatherState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(GlassFill, RoundedCornerShape(16.dp))
            .border(GlassStroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val w = state.weather
        if (w == null) {
            val problem = when {
                !state.online -> "Pas de réseau"
                state.failed -> "Météo indisponible"
                else -> null
            }
            if (problem != null) {
                Box(Modifier.size(10.dp).background(Warning, RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
            }
            Text(problem ?: "Météo…", color = TextMuted, fontSize = 18.sp)
        } else {
            Text(w.symbol, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("${w.temperature}°", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                Text("${w.label} · ${w.min}° / ${w.max}°", color = TextMuted, fontSize = 14.sp)
                Text("Demain ${w.tomorrowSymbol} ${w.tomorrowMin}° / ${w.tomorrowMax}°", color = TextMuted, fontSize = 14.sp)
                Text(BuildConfig.WEATHER_CITY, color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

/** Indicateur + bouton : ouvre Surfshark (atteignable avec "Haut" depuis la rangée d'apps). */
@Composable
private fun VpnStatus(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val connected = rememberVpnConnected()
    Surface(
        onClick = onClick,
        modifier = modifier.height(SideButtonHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = tileColors(),
        border = pillBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(if (connected) VpnOn else VpnOff, RoundedCornerShape(50)))
            Spacer(Modifier.width(10.dp))
            Text(
                if (connected) "VPN connecté" else "VPN déconnecté",
                color = if (connected) Color.White else TextMuted,
                fontSize = 14.sp,
            )
        }
    }
}

/** Petit bouton rond à droite du VPN : Sources (entrées HDMI) et Paramètres. */
@Composable
private fun SideButton(symbol: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(SideButtonHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = tileColors(),
        border = pillBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color.White, fontSize = 18.sp)
        }
    }
}

// 5 tuiles par ligne sur la largeur utile (960 dp à 1080p, moins les marges).
private val TileWidth = 160.dp
private val TileHeight = 90.dp
private val TileSpacing = 14.dp

@Composable
private fun tileColors() = ClickableSurfaceDefaults.colors(
    containerColor = GlassFill,
    focusedContainerColor = GlassFocused,
)

// Liseré de verre aussi autour des bannières d'apps, contour bleu au focus.
@Composable
private fun tileBorder() = ClickableSurfaceDefaults.border(
    border = Border(GlassStroke, shape = RoundedCornerShape(12.dp)),
    focusedBorder = Border(BorderStroke(3.dp, Accent), shape = RoundedCornerShape(12.dp)),
)

@Composable
private fun pillBorder() = ClickableSurfaceDefaults.border(
    border = Border(GlassStroke, shape = RoundedCornerShape(50)),
    focusedBorder = Border(BorderStroke(2.dp, Accent), shape = RoundedCornerShape(50)),
)

@Composable
private fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(TileWidth, TileHeight),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = tileColors(),
            // Tuile en cours de déplacement : contour blanc et plus grande.
            border = if (highlighted) ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(4.dp, Color.White), shape = RoundedCornerShape(12.dp)),
            ) else tileBorder(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (highlighted) 1.18f else 1.1f),
        ) {
            content()
        }
        Text(
            label,
            color = TextMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(TileWidth).padding(top = 10.dp),
        )
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val banner = remember(app) { app.banner?.toBitmap(320, 180)?.asImageBitmap() }
    val icon = remember(app) { app.icon.toBitmap(128, 128).asImageBitmap() }

    Tile(app.label, modifier, highlighted, onLongClick, onClick) {
        if (banner != null) {
            Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(icon, contentDescription = app.label, modifier = Modifier.size(56.dp))
            }
        }
    }
}

/** Tuile sans app derrière : "+" (ajouter). */
@Composable
private fun ActionTile(symbol: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tile(label, modifier, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color.White, fontSize = 40.sp)
        }
    }
}
