package com.ph.prioritychecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ph.prioritychecker.model.*
import com.ph.prioritychecker.viewmodel.GameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Colors ──────────────────────────────────────────────────────────────────

val BgDark        = Color(0xFF0A1A0A)
val SurfaceDark   = Color(0xFF152215)
val CardDark      = Color(0xFF1C2E1C)
val CardDarker    = Color(0xFF142014)
val Gold          = Color(0xFFFFB700)
val GoldDark      = Color(0xFFB37F00)
val Silver        = Color(0xFFADB5BD)
val NeonGreen     = Color(0xFF00E676)
val StackBlue     = Color(0xFF42A5F5)
val StackBlueDark = Color(0xFF1565C0)
val AlertRed      = Color(0xFFEF5350)
val WarningOrange = Color(0xFFFF9800)
val Teal          = Color(0xFF26C6DA)
val TextPrimary   = Color(0xFFE8F5E9)
val TextSecondary = Color(0xFF81C784)
val TextMuted     = Color(0xFF4CAF50)

// ─── Composition Local for Strings ───────────────────────────────────────────

val LocalStr = compositionLocalOf { germanStrings() }

// ─── Theme ───────────────────────────────────────────────────────────────────

@Composable
fun PHTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Color(0xFF1A1200),
            secondary = NeonGreen, onSecondary = Color(0xFF001A07),
            background = BgDark, surface = SurfaceDark,
            onBackground = TextPrimary, onSurface = TextPrimary,
            outline = Color(0xFF3A5A3A), surfaceVariant = CardDark
        ),
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PHTheme {
                Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
                    val vm: GameViewModel = viewModel()
                    PHApp(vm)
                }
            }
        }
    }
}

// ─── Root ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PHApp(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val strings = if (state.language == Language.GERMAN) germanStrings() else englishStrings()

    CompositionLocalProvider(LocalStr provides strings) {
        var showSettings by remember { mutableStateOf(false) }
        var showAddStack by remember { mutableStateOf(false) }
        var showReference by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = BgDark,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚡", fontSize = 20.sp)
                            Text("Priority Checker", fontWeight = FontWeight.Bold, color = Gold)
                            Surface(color = CardDark, shape = RoundedCornerShape(12.dp)) {
                                Text(" ${strings.roundLabel} ${state.turnNumber} ",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showReference = true }) { Icon(Icons.Default.List, null, tint = TextSecondary) }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, null, tint = TextSecondary) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            }
        ) { padding ->
            GameScreen(state, vm, { showAddStack = true }, Modifier.padding(padding))
        }

        if (showSettings) SettingsDialog(state, vm) { showSettings = false }
        if (showAddStack) AddToStackDialog(state, vm) { showAddStack = false }
        if (showReference) ReferenceSheet(vm) { showReference = false }
    }
}

// ─── Game Screen ──────────────────────────────────────────────────────────────

@Composable
fun GameScreen(state: GameState, vm: GameViewModel, onAddToStack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PlayerHeaderCard(state) }
        item { PhaseTimeline(state, vm) }
        item { CurrentStepCard(state) }
        item { PriorityCard(state) }
        if (state.priority != PriorityHolder.NONE && !state.awaitingMandatoryAction) {
            item { CastingOptionsCard(state) }
        }
        item { StackCard(state, vm) }
        item { ActionButtons(state, vm, onAddToStack) }
        item { StatusMessage(state.statusMessage) }
        item { SpecialRulesCard(state.currentStep) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─── Player Header ────────────────────────────────────────────────────────────

@Composable
fun PlayerHeaderCard(state: GameState) {
    val str = LocalStr.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerChip(str.activePlayer, state.activePlayerName, true, state.priority == PriorityHolder.ACTIVE_PLAYER, Modifier.weight(1f))
        PlayerChip(str.nonActivePlayer, state.nonActivePlayerName, false, state.priority == PriorityHolder.NON_ACTIVE_PLAYER, Modifier.weight(1f))
    }
}

@Composable
fun PlayerChip(label: String, name: String, isActive: Boolean, isPrio: Boolean, modifier: Modifier = Modifier) {
    val str = LocalStr.current
    val accent = if (isActive) Gold else Silver
    Surface(modifier = modifier.border(if (isPrio) 2.dp else 1.dp, if (isPrio) NeonGreen else accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        color = if (isPrio) CardDark.copy(alpha = 0.9f) else CardDarker, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isActive) "⚔️" else "🛡️", fontSize = 14.sp)
                Text(label, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(name, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isPrio) { Spacer(Modifier.height(2.dp)); Text("⚡ ${str.hasPriority}", color = NeonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ─── Phase Timeline ───────────────────────────────────────────────────────────

@Composable
fun PhaseTimeline(state: GameState, vm: GameViewModel) {
    val filteredSteps = vm.filteredSteps()
    val currentIdx = vm.currentFilteredIndex()
    Surface(color = CardDark, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PhaseType.values().forEach { phase ->
                    val inPhase = filteredSteps.filter { it.phase == phase }
                    val isCurrent = inPhase.any { it.id == state.currentStep.id }
                    val isPast = inPhase.all { filteredSteps.indexOf(it) < currentIdx }
                    Surface(color = if (isCurrent) Gold else if (isPast) TextMuted.copy(alpha = 0.4f) else CardDarker,
                        shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        Text(phase.shortName, modifier = Modifier.padding(vertical = 5.dp),
                            color = if (isCurrent) Color.Black else if (isPast) TextMuted else TextSecondary.copy(alpha = 0.5f),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(filteredSteps) { idx, step ->
                    val isCurrent = step.id == state.currentStep.id
                    val isPast = idx < currentIdx
                    Surface(color = if (isCurrent) NeonGreen else if (isPast) TextMuted.copy(alpha = 0.3f) else CardDarker,
                        shape = RoundedCornerShape(4.dp), modifier = Modifier.clickable { vm.goToStep(idx) }) {
                        Text(step.shortName, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = if (isCurrent) Color.Black else if (isPast) TextMuted else TextSecondary.copy(alpha = 0.5f),
                            fontSize = 9.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

// ─── Current Step Card ────────────────────────────────────────────────────────

@Composable
fun CurrentStepCard(state: GameState) {
    val str = LocalStr.current
    val step = state.currentStep
    val phaseColor = when (step.phase) {
        PhaseType.BEGINNING -> Teal; PhaseType.MAIN_PRE, PhaseType.MAIN_POST -> Gold
        PhaseType.COMBAT -> AlertRed; PhaseType.ENDING -> WarningOrange
    }
    Surface(color = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = phaseColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                            Text(" ${step.phase.displayName} ", modifier = Modifier.padding(vertical = 2.dp), color = phaseColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (step.id == StepId.FIRST_STRIKE_DAMAGE) {
                            Surface(color = WarningOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text(" ${str.optional} ", modifier = Modifier.padding(vertical = 2.dp), color = WarningOrange, fontSize = 9.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(step.displayName, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${step.shortName} · ${step.rulesReference}", color = TextMuted, fontSize = 10.sp)
                }
                if (step.hasMandatoryAction && state.awaitingMandatoryAction) {
                    Surface(color = WarningOrange.copy(alpha = 0.2f), shape = CircleShape, border = BorderStroke(1.dp, WarningOrange)) {
                        Text("!", modifier = Modifier.padding(8.dp), color = WarningOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Divider(color = phaseColor.copy(alpha = 0.2f))
            Spacer(Modifier.height(10.dp))
            if (step.hasMandatoryAction) {
                Surface(color = WarningOrange.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.3f))) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚡", fontSize = 14.sp)
                        Column {
                            Text(str.mandatoryNoStack, color = WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(step.mandatoryActionDescription, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(step.description, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            if (step.triggerText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🔔", fontSize = 12.sp)
                    Text(step.triggerText, color = Teal, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
        }
    }
}

// ─── Priority Card ────────────────────────────────────────────────────────────

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun PriorityCard(state: GameState) {
    val str = LocalStr.current
    val priority = state.priority
    val step = state.currentStep
    val (bgColor, borderColor, icon, holderName) = when {
        state.awaitingMandatoryAction -> Quadruple(WarningOrange.copy(alpha = 0.08f), WarningOrange.copy(alpha = 0.5f), "⚡", str.mandatoryAction)
        priority == PriorityHolder.NONE && step.isConditional -> Quadruple(CardDarker, Color(0xFF3A5A3A), "⏸️", str.nobody)
        priority == PriorityHolder.NONE -> Quadruple(CardDarker, Color(0xFF3A5A3A), "⏸️", str.nobody)
        priority == PriorityHolder.ACTIVE_PLAYER -> Quadruple(Gold.copy(alpha = 0.08f), Gold.copy(alpha = 0.6f), "⚔️", state.activePlayerName)
        else -> Quadruple(Silver.copy(alpha = 0.08f), Silver.copy(alpha = 0.6f), "🛡️", state.nonActivePlayerName)
    }
    Surface(color = bgColor, shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, borderColor)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(str.priority, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(icon, fontSize = 24.sp)
                        Text(holderName, color = if (priority == PriorityHolder.ACTIVE_PLAYER) Gold else if (priority == PriorityHolder.NON_ACTIVE_PLAYER) Silver else TextSecondary,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (state.lastPassedBy != null) {
                    Surface(color = CardDarker, shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${str.passedLabel}:", color = TextMuted, fontSize = 9.sp)
                            Text(if (state.lastPassedBy == PriorityHolder.ACTIVE_PLAYER) state.activePlayerName else state.nonActivePlayerName,
                                color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${str.stillWaiting}: ${if (state.lastPassedBy == PriorityHolder.ACTIVE_PLAYER) state.nonActivePlayerName else state.activePlayerName}",
                                color = NeonGreen, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Divider(color = borderColor.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Text("${str.priorityFlow}:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(step.priorityFlowText, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

// ─── Casting Options ──────────────────────────────────────────────────────────

@Composable
fun CastingOptionsCard(state: GameState) {
    val str = LocalStr.current
    val opts = state.currentCastingOptions()
    val isMainPhase = state.currentStep.id == StepId.MAIN_PRE || state.currentStep.id == StepId.MAIN_POST
    val isAP = state.priority == PriorityHolder.ACTIVE_PLAYER
    val apShort = str.activePlayerShort

    Surface(color = CardDark, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(str.whatCanBePlayed, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            if (!opts.hasAnything) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("❌", fontSize = 14.sp); Text(str.nothingPlayable, color = TextSecondary, fontSize = 13.sp)
                }
            } else {
                if (opts.hasInstantSpeed) {
                    CastingRow("✅", str.instantSpeed, NeonGreen,
                        buildList {
                            if (opts.instants) add(str.typeInstant)
                            if (opts.activatedAbilities) add(str.typeActivatedAbility)
                            if (opts.flashCards) add("Flash")
                        }.joinToString(" · "))
                }
                if (isAP && isMainPhase) {
                    Spacer(Modifier.height(6.dp))
                    if (state.stack.isEmpty()) {
                        CastingRow("✅", str.sorcerySpeed, Gold,
                            listOf(str.typeSorcery, str.typeCreature, str.typeArtifact, str.typeEnchantment, str.typePlaneswalker, str.typeBattle).joinToString(" · "))
                    } else {
                        CastingRow("⚠️", str.sorcerySpeedBlocked, WarningOrange,
                            "${str.stackMustBeEmpty} (${state.stack.size}×)")
                    }
                    Spacer(Modifier.height(6.dp))
                    CastingRow("🌍", str.landPlay, Teal, str.landPlayDetail)
                } else if (isAP && !isMainPhase) {
                    Spacer(Modifier.height(6.dp))
                    CastingRow("❌", str.sorcerySpeedNA, TextMuted, str.onlyInMainPhase)
                }
            }
        }
    }
}

@Composable
fun CastingRow(icon: String, label: String, color: Color, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(icon, fontSize = 14.sp)
        Column {
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextSecondary.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

// ─── Stack Card ───────────────────────────────────────────────────────────────

@Composable
fun StackCard(state: GameState, vm: GameViewModel) {
    val str = LocalStr.current
    var previewCard by remember { mutableStateOf<ScryfallCard?>(null) }

    Surface(color = if (state.stack.isEmpty()) CardDarker else StackBlueDark.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (state.stack.isEmpty()) Color(0xFF2A3A2A) else StackBlue.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📚", fontSize = 16.sp)
                    Text(str.stackTitle, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Surface(color = if (state.stack.isEmpty()) CardDarker else StackBlue.copy(alpha = 0.3f), shape = CircleShape) {
                        Text(" ${state.stack.size} ", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            color = if (state.stack.isEmpty()) TextMuted else StackBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (state.stack.isNotEmpty()) Text(str.topResolvesFirst, color = StackBlue.copy(alpha = 0.7f), fontSize = 10.sp)
            }
            if (state.stack.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(str.stackEmpty, color = TextMuted, fontSize = 12.sp)
            } else {
                Spacer(Modifier.height(8.dp))
                state.stack.reversed().forEachIndexed { revIdx, item ->
                    val isTop = revIdx == 0
                    StackItemRow(item, isTop, state.stack.size - revIdx, str,
                        onRemove = { vm.removeFromStack(item.id) },
                        onLongPress = { item.card?.let { previewCard = it } }
                    )
                    if (revIdx < state.stack.size - 1) Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    if (previewCard != null) {
        CardPreviewDialog(previewCard!!) { previewCard = null }
    }
}

@Composable
fun StackItemRow(item: StackItem, isTop: Boolean, position: Int, str: AppStrings, onRemove: () -> Unit, onLongPress: () -> Unit) {
    val ctx = LocalContext.current
    Surface(color = if (isTop) StackBlueDark.copy(alpha = 0.5f) else CardDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isTop) StackBlue.copy(alpha = 0.8f) else Color(0xFF2A4A6A).copy(alpha = 0.4f)),
        modifier = Modifier.pointerInput(item.id) {
            detectTapGestures(onLongPress = { if (item.card != null) onLongPress() })
        }) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            // Position number
            Text("$position", color = if (isTop) StackBlue else TextMuted, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp), textAlign = TextAlign.Center)

            // Card thumbnail if available
            if (item.card?.imageSmall != null) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(item.card.imageSmall).crossfade(true).build(),
                    contentDescription = item.card.displayName,
                    modifier = Modifier.width(32.dp).height(45.dp).clip(RoundedCornerShape(3.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Description + meta
            Column(modifier = Modifier.weight(1f)) {
                Text(item.description, color = if (isTop) TextPrimary else TextSecondary,
                    fontSize = 13.sp, fontWeight = if (isTop) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.itemType, color = StackBlue.copy(alpha = 0.8f), fontSize = 10.sp)
                    Text("·", color = TextMuted, fontSize = 10.sp)
                    val apShort = str.activePlayerShort
                    val napShort = str.nonActivePlayerShort
                    Text(item.controllerLabel(apShort, napShort),
                        color = if (item.controlledByActive) Gold else Silver, fontSize = 10.sp)
                    if (isTop) Text(str.resolvesNext, color = StackBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                if (item.card != null && item.card.imageSmall != null) {
                    Text(str.longPressHint, color = TextMuted, fontSize = 9.sp)
                }
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Card Preview Dialog ──────────────────────────────────────────────────────

@Composable
fun CardPreviewDialog(card: ScryfallCard, onDismiss: () -> Unit) {
    val str = LocalStr.current
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(str.cardPreview, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
                }
                Spacer(Modifier.height(12.dp))
                if (card.imageNormal != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(card.imageNormal).crossfade(true).build(),
                        contentDescription = card.displayName,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.716f).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(card.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(card.displayTypeLine, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                if (card.lang != "en" && card.name != card.printedName) {
                    Text("(${card.name})", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─── Action Buttons ───────────────────────────────────────────────────────────

@Composable
fun ActionButtons(state: GameState, vm: GameViewModel, onAddToStack: () -> Unit) {
    val str = LocalStr.current
    val step = state.currentStep

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Mandatory action button
        if (state.awaitingMandatoryAction) {
            Button(onClick = { vm.confirmMandatoryAction() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                shape = RoundedCornerShape(12.dp)) {
                Text("⚡  ", fontSize = 16.sp)
                Text(when (step.id) {
                    StepId.UNTAP -> str.untapDone
                    StepId.DRAW -> str.drawDone
                    StepId.DECLARE_ATTACKERS -> str.attackersDeclared
                    StepId.DECLARE_BLOCKERS -> str.blockersDeclared
                    StepId.FIRST_STRIKE_DAMAGE -> str.firstStrikeDamageDone
                    StepId.COMBAT_DAMAGE -> str.combatDamageDone
                    StepId.CLEANUP -> str.cleanupDone
                    else -> str.confirm
                }, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (!step.hasPriority || step.isConditional) {
                OutlinedButton(onClick = { vm.confirmMandatoryAction() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, Color(0xFF3A5A3A)), shape = RoundedCornerShape(12.dp)) {
                    Text(str.noMandatoryNextStep, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Priority + Stack buttons
        if (state.priority != PriorityHolder.NONE && !state.awaitingMandatoryAction) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.passPriority() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.lastPassedBy != null) AlertRed.copy(alpha = 0.85f) else NeonGreen.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(when {
                        state.lastPassedBy != null && state.stack.isEmpty() -> str.stepEnd
                        state.lastPassedBy != null -> str.resolveStack
                        else -> str.passPriority
                    }, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onAddToStack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StackBlueDark),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📚", fontSize = 14.sp)
                        Text(str.addToStack, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (state.lastPassedBy != null) {
                Surface(color = if (state.stack.isEmpty()) AlertRed.copy(alpha = 0.08f) else StackBlueDark.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (state.stack.isEmpty()) AlertRed.copy(alpha = 0.3f) else StackBlue.copy(alpha = 0.3f))) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.stack.isEmpty()) "⚠️" else "ℹ️", fontSize = 14.sp)
                        Text(if (state.stack.isEmpty()) str.secondPassWarningEmpty else str.secondPassWarningStack,
                            color = if (state.stack.isEmpty()) AlertRed else StackBlue, fontSize = 12.sp)
                    }
                }
            }
        }

        // Next Step button
        if (!state.awaitingMandatoryAction && state.currentStep.id != StepId.CLEANUP) {
            val steps = vm.filteredSteps()
            val nextStep = steps.getOrNull(vm.currentFilteredIndex() + 1)
            if (nextStep != null) {
                OutlinedButton(onClick = { vm.goToStep(vm.currentFilteredIndex() + 1) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, Color(0xFF3A6A3A)), shape = RoundedCornerShape(12.dp)) {
                    Text("${str.nextStep}: ${nextStep.displayName}  [${nextStep.shortName}]",
                        color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Next Turn button
        if (state.currentStep.id == StepId.CLEANUP && !state.awaitingMandatoryAction && state.priority == PriorityHolder.NONE) {
            Button(onClick = { vm.nextTurn() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(12.dp)) {
                Text("${str.nextTurn} (${str.roundLabel} ${state.turnNumber + 1})",
                    color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ─── Status Message ───────────────────────────────────────────────────────────

@Composable
fun StatusMessage(message: String) {
    Surface(color = CardDarker, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF2A4A2A))) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text("📋", fontSize = 14.sp)
            Text(message, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

// ─── Special Rules ────────────────────────────────────────────────────────────

@Composable
fun SpecialRulesCard(step: StepDefinition) {
    val str = LocalStr.current
    var expanded by remember(step.id) { mutableStateOf(false) }
    Surface(color = CardDarker, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF2A3A2A))) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 14.sp)
                    Text(str.specialRulesTitle, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(if (expanded) "▲" else "▼", color = TextMuted, fontSize = 12.sp)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    step.specialRules.forEach { rule ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                            Text("•", color = NeonGreen, fontSize = 14.sp, modifier = Modifier.width(14.dp))
                            Text(rule, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${str.specialRulesTitle.take(3)}.: ${step.rulesReference}", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

// ─── Add to Stack Dialog with Scryfall Search ─────────────────────────────────

@Composable
fun AddToStackDialog(state: GameState, vm: GameViewModel, onDismiss: () -> Unit) {
    val str = LocalStr.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val isGerman = state.language == Language.GERMAN

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ScryfallCard>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchMsg by remember { mutableStateOf("") }
    var selectedCard by remember { mutableStateOf<ScryfallCard?>(null) }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(str.typeInstant) }
    var previewCard by remember { mutableStateOf<ScryfallCard?>(null) }

    val types = listOf(str.typeInstant, str.typeSorcery, str.typeCreature, str.typeArtifact,
        str.typeEnchantment, str.typePlaneswalker, str.typeBattle,
        str.typeActivatedAbility, str.typeTriggeredAbility, str.typeOther)

    // Debounced search
    LaunchedEffect(query) {
        if (query.length < 2) { searchResults = emptyList(); searchMsg = ""; return@LaunchedEffect }
        delay(400)
        isSearching = true
        searchMsg = str.searching
        selectedCard = null
        when (val result = ScryfallApi.searchBoth(query)) {
            is SearchResult.Success -> {
                searchResults = result.cards
                searchMsg = if (result.cards.isEmpty()) str.noResults else "${str.tapToSelect}:"
                isSearching = false
            }
            is SearchResult.Empty -> { searchResults = emptyList(); searchMsg = str.noResults; isSearching = false }
            is SearchResult.Error -> { searchResults = emptyList(); searchMsg = str.searchError; isSearching = false }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(str.searchCard, color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                // Search field
                OutlinedTextField(value = query, onValueChange = { query = it; selectedCard = null },
                    label = { Text(str.searchCard, color = TextMuted) },
                    placeholder = { Text(str.searchPlaceholder, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonGreen)
                        else if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null, tint = TextMuted) }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen, unfocusedBorderColor = Color(0xFF3A5A3A),
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextSecondary))

                // Search results
                if (searchMsg.isNotEmpty() && !isSearching) {
                    Spacer(Modifier.height(6.dp))
                    Text(searchMsg, color = if (searchResults.isEmpty()) TextMuted else TextSecondary, fontSize = 11.sp)
                }

                if (searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(searchResults) { card ->
                            val isSelected = selectedCard?.scryfallId == card.scryfallId
                            Surface(color = if (isSelected) NeonGreen.copy(alpha = 0.15f) else CardDarker,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) NeonGreen.copy(alpha = 0.6f) else Color(0xFF2A3A2A)),
                                modifier = Modifier.clickable {
                                    selectedCard = card
                                    description = card.displayName
                                    selectedType = card.detectType(isGerman)
                                }.pointerInput(card.scryfallId) {
                                    detectTapGestures(onLongPress = { previewCard = card })
                                }) {
                                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (card.imageSmall != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(ctx).data(card.imageSmall).crossfade(true).build(),
                                            contentDescription = card.displayName,
                                            modifier = Modifier.width(36.dp).height(50.dp).clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop)
                                    } else {
                                        Box(modifier = Modifier.width(36.dp).height(50.dp).background(CardDark, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center) { Text("🃏", fontSize = 18.sp) }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(card.displayName, color = if (isSelected) NeonGreen else TextPrimary,
                                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(card.displayTypeLine, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(color = StackBlueDark.copy(alpha = 0.5f), shape = RoundedCornerShape(3.dp)) {
                                                Text(" ${card.lang.uppercase()} ", color = StackBlue, fontSize = 9.sp)
                                            }
                                            if (card.lang != "en") Text(card.name, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (isSelected) Text("✅", fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                // Manual / selected card section
                Spacer(Modifier.height(12.dp))
                Divider(color = Color(0xFF2A3A2A))
                Spacer(Modifier.height(8.dp))
                Text(if (selectedCard != null) "✅ ${selectedCard!!.displayName}" else str.manualEntrySection,
                    color = if (selectedCard != null) NeonGreen else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                if (selectedCard == null) {
                    OutlinedTextField(value = description, onValueChange = { description = it },
                        label = { Text(str.effectDescription, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen, unfocusedBorderColor = Color(0xFF3A5A3A),
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextSecondary))
                    Spacer(Modifier.height(8.dp))
                }

                Text("${str.effectType}:", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(types) { type ->
                        FilterChip(selected = selectedType == type, onClick = { selectedType = type },
                            label = { Text(type, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                                selectedLabelColor = NeonGreen))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
                        Text(str.cancel, color = TextSecondary)
                    }
                    Button(onClick = {
                        vm.addToStack(description, selectedType, selectedCard)
                        onDismiss()
                    }, modifier = Modifier.weight(1f),
                        enabled = selectedCard != null || description.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.8f))) {
                        Text(str.addButton, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (previewCard != null) CardPreviewDialog(previewCard!!) { previewCard = null }
}

// ─── Settings Dialog ──────────────────────────────────────────────────────────

@Composable
fun SettingsDialog(state: GameState, vm: GameViewModel, onDismiss: () -> Unit) {
    val str = LocalStr.current
    var p1 by remember { mutableStateOf(state.activePlayerName) }
    var p2 by remember { mutableStateOf(state.nonActivePlayerName) }
    var firstStrike by remember { mutableStateOf(state.includeFirstStrike) }
    var lang by remember { mutableStateOf(state.language) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(str.settingsTitle, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(value = p1, onValueChange = { p1 = it },
                    label = { Text(str.playerOneName, color = Gold.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF3A5A3A), focusedTextColor = TextPrimary, unfocusedTextColor = TextSecondary))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = p2, onValueChange = { p2 = it },
                    label = { Text(str.playerTwoName, color = Silver.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Silver, unfocusedBorderColor = Color(0xFF3A5A3A), focusedTextColor = TextPrimary, unfocusedTextColor = TextSecondary))

                Spacer(Modifier.height(16.dp))

                // Language Toggle
                Text(str.languageSetting, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Language.values().forEach { l ->
                        FilterChip(selected = lang == l, onClick = { lang = l },
                            label = { Text(l.nativeName, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.2f), selectedLabelColor = Gold))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(str.firstStrikeSetting, color = TextSecondary, fontSize = 14.sp)
                        Text(str.firstStrikeDetail, color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(checked = firstStrike, onCheckedChange = { firstStrike = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = GoldDark.copy(alpha = 0.5f)))
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
                        Text(str.cancel, color = TextSecondary)
                    }
                    Button(onClick = {
                        vm.setPlayerNames(p1, p2)
                        vm.setLanguage(lang)
                        vm.toggleFirstStrike(firstStrike)
                        onDismiss()
                    }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold.copy(alpha = 0.9f))) {
                        Text(str.save, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.resetGame(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(str.resetGame, color = AlertRed.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
    }
}

// ─── Reference Sheet ──────────────────────────────────────────────────────────

@Composable
fun ReferenceSheet(vm: GameViewModel, onDismiss: () -> Unit) {
    val str = LocalStr.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxSize(0.95f), border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
            Column {
                Surface(color = CardDark) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(str.referenceTitle, color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Surface(color = CardDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(str.corePriorityRules, color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                listOf(
                                    "${str.activePlayerShort} ${if (str.activePlayerShort == "AS") "erhält immer als erster Priorität" else "always gets priority first in a new step"}",
                                    "${if (str.activePlayerShort == "AS") "Nach Auflösung: immer AS Priorität (CR 117.3c)" else "After resolution: always AP gets priority (CR 117.3c)"}",
                                    "${if (str.activePlayerShort == "AS") "Nach Aktion: Spieler behält Priorität (CR 117.3d)" else "After action: player retains priority (CR 117.3d)"}",
                                    "${if (str.activePlayerShort == "AS") "Beide passen + Stack leer → Schritt endet (CR 117.6)" else "Both pass + empty stack → step ends (CR 117.6)"}",
                                    "${if (str.activePlayerShort == "AS") "Beide passen + Stack voll → Top löst auf, AS bekommt Prio" else "Both pass + stack full → top resolves, AP gets priority"}",
                                    "${if (str.activePlayerShort == "AS") "Hexerei-Speed: nur AS, Hauptphase, Stack leer (CR 307.1)" else "Sorcery speed: only AP, main phase, empty stack (CR 307.1)"}",
                                    "${if (str.activePlayerShort == "AS") "Instant-Speed: immer wenn Priorität vorhanden (CR 116.1)" else "Instant speed: whenever you have priority (CR 116.1)"}"
                                ).forEach { rule ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("→", color = NeonGreen, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                                        Text(rule, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                    item { Text(str.allStepsTitle, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp)) }
                    items(ALL_STEPS) { step -> ReferenceStepRow(step, str) }
                }
            }
        }
    }
}

@Composable
fun ReferenceStepRow(step: StepDefinition, str: AppStrings) {
    val phaseColor = when (step.phase) {
        PhaseType.BEGINNING -> Teal; PhaseType.MAIN_PRE, PhaseType.MAIN_POST -> Gold
        PhaseType.COMBAT -> AlertRed; PhaseType.ENDING -> WarningOrange
    }
    Surface(color = CardDarker, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.2f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Surface(color = phaseColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp), modifier = Modifier.width(32.dp)) {
                Text(step.shortName, color = phaseColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(step.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(when {
                    !step.hasPriority && !step.isConditional -> str.noMandatoryLabel
                    step.isConditional -> str.conditionalLabel
                    step.hasMandatoryAction -> str.afterMandatoryLabel
                    else -> str.firstPriorityLabel
                }, color = when {
                    !step.hasPriority && !step.isConditional -> AlertRed.copy(alpha = 0.8f)
                    step.isConditional -> WarningOrange
                    else -> NeonGreen
                }, fontSize = 11.sp)
                if (step.hasPriority) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str.instantLabel, color = if (step.apCastingOptions.instants) NeonGreen else TextMuted, fontSize = 10.sp)
                        Text(str.sorceryLabel, color = if (step.apCastingOptions.hasSorcerySpeed) Gold else TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
