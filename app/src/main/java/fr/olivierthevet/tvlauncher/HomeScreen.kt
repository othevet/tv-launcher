package fr.olivierthevet.tvlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
private val Accent = Color(0xFF60A5FA)
private val TextMuted = Color(0xFF9CA3AF)
private val ScreenMargin = 48.dp
private val VpnOn = Color(0xFF34D399)
private val VpnOff = Color(0xFF6B7280)

private const val KEY_HEADER = "header"
private const val KEY_ADD = "add"
private const val KEY_SETTINGS = "settings"

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
                    Column(horizontalAlignment = Alignment.End) {
                        WeatherCard()
                        Spacer(Modifier.height(12.dp))
                        VpnStatus { launchSurfshark(context) }
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
                    item(key = KEY_SETTINGS) {
                        ActionTile("⚙", "Paramètres") { launchSettings(context) }
                    }
                }
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
                .background(CardColor, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(app.label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            MenuItem("Déplacer", Modifier.focusRequester(firstFocus), onMove)
            MenuItem("Retirer de l'accueil", onClick = onRemove)
            MenuItem("Annuler", onClick = onDismiss)
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
            containerColor = Background,
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
private fun Clock() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
        .replaceFirstChar { it.titlecase(Locale.FRENCH) }

    Column {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
        )
        Text(date, color = TextMuted, fontSize = 20.sp)
    }
}

@Composable
private fun WeatherCard() {
    var weather by remember { mutableStateOf<Weather?>(null) }
    LaunchedEffect(Unit) {
        // Au démarrage de la TV, le réseau (ou l'heure, nécessaire au HTTPS) n'est pas
        // forcément prêt : en cas d'échec, on réessaie vite au lieu d'attendre 15 min.
        while (true) {
            val result = fetchWeather()
            if (result != null) weather = result
            delay(if (result != null) 15 * 60 * 1_000L else 30 * 1_000L)
        }
    }

    Row(
        Modifier
            .background(CardColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val w = weather
        if (w == null) {
            Text("Météo…", color = TextMuted, fontSize = 18.sp)
        } else {
            Text(w.symbol, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("${w.temperature}°", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                Text("${w.label} · ${w.min}° / ${w.max}°", color = TextMuted, fontSize = 14.sp)
                Text(BuildConfig.WEATHER_CITY, color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

/** Indicateur + bouton : ouvre Surfshark (atteignable avec "Haut" depuis la rangée d'apps). */
@Composable
private fun VpnStatus(onClick: () -> Unit) {
    val connected = rememberVpnConnected()
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = tileColors(),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = RoundedCornerShape(50)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

// 5 tuiles par ligne sur la largeur utile (960 dp à 1080p, moins les marges).
private val TileWidth = 160.dp
private val TileHeight = 90.dp
private val TileSpacing = 14.dp

@Composable
private fun tileColors() = ClickableSurfaceDefaults.colors(
    containerColor = CardColor,
    focusedContainerColor = CardColor,
)

@Composable
private fun tileBorder() = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, Accent), shape = RoundedCornerShape(12.dp)),
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

/** Tuile sans app derrière : "+" (ajouter) ou Paramètres. */
@Composable
private fun ActionTile(symbol: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tile(label, modifier, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color.White, fontSize = 40.sp)
        }
    }
}
