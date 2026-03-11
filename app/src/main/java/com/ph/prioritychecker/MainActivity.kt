package com.ph.prioritychecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ph.prioritychecker.model.*
import com.ph.prioritychecker.viewmodel.GameViewModel

// ─── Colors ──────────────────────────────────────────────────────────────────

val BgDark = Color(0xFF0A1A0A)
val SurfaceDark = Color(0xFF152215)
val CardDark = Color(0xFF1C2E1C)
val CardDarker = Color(0xFF142014)
val Gold = Color(0xFFFFB700)
val GoldDark = Color(0xFFB37F00)
val Silver = Color(0xFFADB5BD)
val NeonGreen = Color(0xFF00E676)
val StackBlue = Color(0xFF42A5F5)
val StackBlueDark = Color(0xFF1565C0)
val AlertRed = Color(0xFFEF5350)
val WarningOrange = Color(0xFFFF9800)
val Teal = Color(0xFF26C6DA)
val TextPrimary = Color(0xFFE8F5E9)
val TextSecondary = Color(0xFF81C784)
val TextMuted = Color(0xFF4CAF50)

// ─── Theme ───────────────────────────────────────────────────────────────────

@Composable
fun PHTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Gold,
        onPrimary = Color(0xFF1A1200),
        primaryContainer = GoldDark,
        secondary = NeonGreen,
        onSecondary = Color(0xFF001A07),
        background = BgDark,
        surface = SurfaceDark,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        outline = Color(0xFF3A5A3A),
        surfaceVariant = CardDark
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// ─── Main Activity ────────────────────────────────────────────────────────────

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

// ─── Root App ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PHApp(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showAddStack by remember { mutableStateOf(false) }
    var showReferenceSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚡", fontSize = 20.sp)
                        Text("Priority Checker", fontWeight = FontWeight.Bold, color = Gold)
                        Spacer(Modifier.width(4.dp))
                        Surface(color = CardDark, shape = RoundedCornerShape(12.dp)) {
                            Text(
                                " Runde ${state.turnNumber} ",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = TextSecondary, fontSize = 12.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showReferenceSheet = true }) {
                        Icon(Icons.Default.List, "Referenz", tint = TextSecondary)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Einstellungen", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        GameScreen(
            state = state,
            vm = vm,
            onAddToStack = { showAddStack = true },
            modifier = Modifier.padding(padding)
        )
    }

    if (showSettings) {
        SettingsDialog(state = state, vm = vm, onDismiss = { showSettings = false })
    }
    if (showAddStack) {
        AddToStackDialog(onConfirm = { desc, type ->
            vm.addToStack(desc, type); showAddStack = false
        }, onDismiss = { showAddStack = false })
    }
    if (showReferenceSheet) {
        ReferenceSheet(vm = vm, onDismiss = { showReferenceSheet = false })
    }
}

// ─── Main Game Screen ─────────────────────────────────────────────────────────

@Composable
fun GameScreen(state: GameState, vm: GameViewModel, onAddToStack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Player Header
        item { PlayerHeaderCard(state) }

        // Phase Timeline
        item { PhaseTimeline(state, vm) }

        // Current Step Card
        item { CurrentStepCard(state) }

        // Priority Indicator
        item { PriorityCard(state) }

        // Casting Options
        if (state.priority != PriorityHolder.NONE && !state.awaitingMandatoryAction) {
            item { CastingOptionsCard(state) }
        }

        // Stack Area
        item { StackCard(state, vm) }

        // Action Buttons
        item { ActionButtons(state, vm, onAddToStack) }

        // Status Message
        item { StatusMessage(state.statusMessage) }

        // Special Rules
        item { SpecialRulesCard(state.currentStep) }

        // Bottom spacing
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─── Player Header ────────────────────────────────────────────────────────────

@Composable
fun PlayerHeaderCard(state: GameState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Active Player
        PlayerChip(
            label = "Aktiver Spieler",
            name = state.activePlayerName,
            isActive = true,
            isPriorityHolder = state.priority == PriorityHolder.ACTIVE_PLAYER,
            modifier = Modifier.weight(1f)
        )
        // Non-Active Player
        PlayerChip(
            label = "Passiver Spieler",
            name = state.nonActivePlayerName,
            isActive = false,
            isPriorityHolder = state.priority == PriorityHolder.NON_ACTIVE_PLAYER,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun PlayerChip(
    label: String,
    name: String,
    isActive: Boolean,
    isPriorityHolder: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isActive) Gold else Silver
    val borderColor = if (isPriorityHolder) NeonGreen else accentColor.copy(alpha = 0.3f)
    val borderWidth = if (isPriorityHolder) 2.dp else 1.dp

    Surface(
        modifier = modifier.border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        color = if (isPriorityHolder) CardDark.copy(alpha = 0.9f) else CardDarker,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isActive) "⚔️" else "🛡️", fontSize = 14.sp)
                Text(label, color = accentColor.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                name,
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isPriorityHolder) {
                Spacer(Modifier.height(2.dp))
                Text("⚡ HAT PRIORITÄT", color = NeonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Phase Timeline ───────────────────────────────────────────────────────────

@Composable
fun PhaseTimeline(state: GameState, vm: GameViewModel) {
    val filteredSteps = vm.filteredSteps()
    val currentFilteredIdx = vm.currentFilteredIndex()

    // Group by phase
    val phases = PhaseType.values()

    Surface(color = CardDark, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Phase row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                phases.forEach { phase ->
                    val stepsInPhase = filteredSteps.filter { it.phase == phase }
                    val isCurrentPhase = stepsInPhase.any { it.id == state.currentStep.id }
                    val isPastPhase = stepsInPhase.all {
                        filteredSteps.indexOf(it) < currentFilteredIdx
                    }

                    val bgColor = when {
                        isCurrentPhase -> Gold
                        isPastPhase -> TextMuted.copy(alpha = 0.4f)
                        else -> CardDarker
                    }
                    val textColor = when {
                        isCurrentPhase -> Color.Black
                        isPastPhase -> TextMuted
                        else -> TextSecondary.copy(alpha = 0.5f)
                    }

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text(
                            phase.shortName,
                            modifier = Modifier.padding(vertical = 5.dp),
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Steps row (mini chips)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(filteredSteps) { idx, step ->
                    val isCurrent = step.id == state.currentStep.id
                    val isPast = idx < currentFilteredIdx
                    val chipColor = when {
                        isCurrent -> NeonGreen
                        isPast -> TextMuted.copy(alpha = 0.3f)
                        else -> CardDarker
                    }
                    Surface(
                        color = chipColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { vm.goToStep(idx) }
                    ) {
                        Text(
                            step.shortName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = if (isCurrent) Color.Black else if (isPast) TextMuted else TextSecondary.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ─── Current Step Card ────────────────────────────────────────────────────────

@Composable
fun CurrentStepCard(state: GameState) {
    val step = state.currentStep
    val phaseColor = when (step.phase) {
        PhaseType.BEGINNING -> Teal
        PhaseType.MAIN_PRE, PhaseType.MAIN_POST -> Gold
        PhaseType.COMBAT -> AlertRed
        PhaseType.ENDING -> WarningOrange
    }

    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = phaseColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                " ${step.phase.displayName} ",
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = phaseColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (step.id == StepId.FIRST_STRIKE_DAMAGE) {
                            Surface(color = WarningOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text(" OPTIONAL ", modifier = Modifier.padding(vertical = 2.dp),
                                    color = WarningOrange, fontSize = 9.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(step.displayName, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Kürzel: ${step.shortName} · ${step.rulesReference}", color = TextMuted, fontSize = 10.sp)
                }
                // Mandatory action badge
                if (step.hasMandatoryAction && state.awaitingMandatoryAction) {
                    Surface(color = WarningOrange.copy(alpha = 0.2f), shape = CircleShape,
                        border = BorderStroke(1.dp, WarningOrange)) {
                        Text("!", modifier = Modifier.padding(8.dp),
                            color = WarningOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = phaseColor.copy(alpha = 0.2f))
            Spacer(Modifier.height(10.dp))

            // Mandatory action notice
            if (step.hasMandatoryAction) {
                Surface(
                    color = WarningOrange.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚡", fontSize = 14.sp)
                        Column {
                            Text("Pflichtaktion (kein Stack)",
                                color = WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(step.mandatoryActionDescription,
                                color = TextSecondary, fontSize = 12.sp)
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

@Composable
fun PriorityCard(state: GameState) {
    val priority = state.priority
    val step = state.currentStep

    val (bgColor, borderColor, icon, holderName) = when {
        state.awaitingMandatoryAction -> Quadruple(
            WarningOrange.copy(alpha = 0.08f), WarningOrange.copy(alpha = 0.5f),
            "⚡", "Pflichtaktion läuft"
        )
        priority == PriorityHolder.NONE && step.isConditional -> Quadruple(
            CardDarker, Color(0xFF3A5A3A), "⏸️", "Normalerweise keine Priorität"
        )
        priority == PriorityHolder.NONE -> Quadruple(
            CardDarker, Color(0xFF3A5A3A), "⏸️", "Kein Spieler hat Priorität"
        )
        priority == PriorityHolder.ACTIVE_PLAYER -> Quadruple(
            Gold.copy(alpha = 0.08f), Gold.copy(alpha = 0.6f),
            "⚔️", state.activePlayerName
        )
        else -> Quadruple(
            Silver.copy(alpha = 0.08f), Silver.copy(alpha = 0.6f),
            "🛡️", state.nonActivePlayerName
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PRIORITÄT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(icon, fontSize = 24.sp)
                        Text(holderName,
                            color = if (priority == PriorityHolder.ACTIVE_PLAYER) Gold
                                    else if (priority == PriorityHolder.NON_ACTIVE_PLAYER) Silver
                                    else TextSecondary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Last passed indicator
                if (state.lastPassedBy != null) {
                    Surface(color = CardDarker, shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gepasst:", color = TextMuted, fontSize = 9.sp)
                            Text(
                                if (state.lastPassedBy == PriorityHolder.ACTIVE_PLAYER) state.activePlayerName else state.nonActivePlayerName,
                                color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("wartet noch: ${if (state.lastPassedBy == PriorityHolder.ACTIVE_PLAYER) state.nonActivePlayerName else state.activePlayerName}",
                                color = NeonGreen, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Priority Flow Description
            Spacer(Modifier.height(10.dp))
            Divider(color = borderColor.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            Text("Prioritätsfluss:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(step.priorityFlowText, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ─── Casting Options Card ──────────────────────────────────────────────────────

@Composable
fun CastingOptionsCard(state: GameState) {
    val opts = state.currentCastingOptions()
    val isMainPhase = state.currentStep.id == StepId.MAIN_PRE || state.currentStep.id == StepId.MAIN_POST
    val isAP = state.priority == PriorityHolder.ACTIVE_PLAYER

    Surface(color = CardDark, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("WAS KANN GESPIELT WERDEN?", color = TextMuted, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))

            if (!opts.hasAnything) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("❌", fontSize = 14.sp)
                    Text("Keine Karten spielbar in diesem Zustand.", color = TextSecondary, fontSize = 13.sp)
                }
            } else {
                // Instant Speed
                if (opts.hasInstantSpeed) {
                    CastingRow("✅", "Instant-Geschwindigkeit", NeonGreen,
                        buildList {
                            if (opts.instants) add("Spontanzauber (Instants)")
                            if (opts.activatedAbilities) add("Aktivierte Fähigkeiten")
                            if (opts.flashCards) add("Karten mit Eile (Flash)")
                        }.joinToString(" · "))
                }

                // Sorcery Speed (only if applicable)
                if (isAP && isMainPhase && state.stack.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    CastingRow("✅", "Hexerei-Geschwindigkeit (Stack leer!)", Gold,
                        buildList {
                            if (opts.sorceries) add("Hexereien")
                            if (opts.creatures) add("Kreaturen")
                            if (opts.artifacts) add("Artefakte")
                            if (opts.enchantments) add("Verzauberungen")
                            if (opts.planeswalkers) add("Planeswalker")
                            if (opts.battles) add("Schlachten")
                        }.joinToString(" · "))
                } else if (isAP && isMainPhase && state.stack.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    CastingRow("⚠️", "Hexerei-Geschwindigkeit gesperrt", WarningOrange,
                        "Stack muss leer sein für Hexerei-Speed (${state.stack.size} Effekt(e) auf Stack)")
                } else if (isAP && !isMainPhase) {
                    Spacer(Modifier.height(6.dp))
                    CastingRow("❌", "Hexerei-Geschwindigkeit N/A", TextMuted,
                        "Nur in Hauptphasen möglich")
                }

                // Land reminder
                if (isAP && isMainPhase) {
                    Spacer(Modifier.height(6.dp))
                    CastingRow("🌍", "Land spielen", Teal,
                        "Max. 1 Land pro Zug (keine Stack-Aktion, kann nicht reagiert werden)")
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
    Surface(
        color = if (state.stack.isEmpty()) CardDarker else StackBlueDark.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (state.stack.isEmpty()) Color(0xFF2A3A2A) else StackBlue.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📚", fontSize = 16.sp)
                    Text("STACK", color = TextMuted, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Surface(
                        color = if (state.stack.isEmpty()) CardDarker else StackBlue.copy(alpha = 0.3f),
                        shape = CircleShape
                    ) {
                        Text(
                            " ${state.stack.size} ",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            color = if (state.stack.isEmpty()) TextMuted else StackBlue,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (state.stack.isNotEmpty()) {
                    Text("↑ oben löst zuerst auf", color = StackBlue.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }

            if (state.stack.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Stack ist leer.", color = TextMuted, fontSize = 12.sp)
            } else {
                Spacer(Modifier.height(8.dp))
                // Show stack items (top item first = last in list)
                state.stack.reversed().forEachIndexed { revIdx, item ->
                    val isTop = revIdx == 0
                    StackItemRow(
                        item = item,
                        isTop = isTop,
                        position = state.stack.size - revIdx,
                        onRemove = { vm.removeFromStack(item.id) }
                    )
                    if (revIdx < state.stack.size - 1) Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun StackItemRow(item: StackItem, isTop: Boolean, position: Int, onRemove: () -> Unit) {
    Surface(
        color = if (isTop) StackBlueDark.copy(alpha = 0.5f) else CardDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isTop) StackBlue.copy(alpha = 0.8f) else Color(0xFF2A4A6A).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)) {
                Text(
                    "$position",
                    color = if (isTop) StackBlue else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(18.dp),
                    textAlign = TextAlign.Center
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.description, color = if (isTop) TextPrimary else TextSecondary,
                        fontSize = 13.sp, fontWeight = if (isTop) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.itemType, color = StackBlue.copy(alpha = 0.8f), fontSize = 10.sp)
                        Text("·", color = TextMuted, fontSize = 10.sp)
                        Text(item.controllerLabel, color = if (item.controlledByActive) Gold else Silver, fontSize = 10.sp)
                        if (isTop) Text("← LÖST ALS NÄCHSTES AUF", color = StackBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Entfernen", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Action Buttons ───────────────────────────────────────────────────────────

@Composable
fun ActionButtons(state: GameState, vm: GameViewModel, onAddToStack: () -> Unit) {
    val step = state.currentStep

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Mandatory Action Button
        if (state.awaitingMandatoryAction) {
            Button(
                onClick = { vm.confirmMandatoryAction() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("⚡  ", fontSize = 16.sp)
                Text(
                    when (step.id) {
                        StepId.UNTAP -> "Enttappen abgeschlossen"
                        StepId.DRAW -> "Karte gezogen → Priorität vergeben"
                        StepId.DECLARE_ATTACKERS -> "Angreifer erklärt"
                        StepId.DECLARE_BLOCKERS -> "Blocker erklärt + Reihenfolge"
                        StepId.FIRST_STRIKE_DAMAGE -> "Erstangriff-Schaden zugeteilt"
                        StepId.COMBAT_DAMAGE -> "Kampfschaden zugeteilt"
                        StepId.CLEANUP -> "Aufräumen abgeschlossen"
                        else -> "Pflichtaktion bestätigen"
                    },
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // Skip to next step (Untap / Cleanup with no priority)
            if (!step.hasPriority || step.isConditional) {
                OutlinedButton(
                    onClick = {
                        vm.confirmMandatoryAction()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, Color(0xFF3A5A3A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("→ Nächster Schritt (keine Priorität)", fontSize = 13.sp)
                }
            }
        }

        // Priority / Stack Buttons (only when someone has priority)
        if (state.priority != PriorityHolder.NONE && !state.awaitingMandatoryAction) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pass Priority
                Button(
                    onClick = { vm.passPriority() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.lastPassedBy != null) AlertRed.copy(alpha = 0.8f) else NeonGreen.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.lastPassedBy != null) "⏭️" else "→", fontSize = 14.sp)
                        Text(
                            if (state.lastPassedBy != null && state.stack.isEmpty()) "Schritt beenden"
                            else if (state.lastPassedBy != null) "Stack auflösen"
                            else "Priorität abgeben",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Add to Stack
                Button(
                    onClick = onAddToStack,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StackBlueDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📚", fontSize = 14.sp)
                        Text("Auf Stack legen", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Second pass warning
            if (state.lastPassedBy != null) {
                Surface(
                    color = if (state.stack.isEmpty()) AlertRed.copy(alpha = 0.08f) else StackBlueDark.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (state.stack.isEmpty()) AlertRed.copy(alpha = 0.3f) else StackBlue.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.stack.isEmpty()) "⚠️" else "ℹ️", fontSize = 14.sp)
                        Text(
                            if (state.stack.isEmpty())
                                "Zweiter Pass → Schritt endet (Stack leer)"
                            else
                                "Zweiter Pass → Oberstes Element auf Stack wird aufgelöst",
                            color = if (state.stack.isEmpty()) AlertRed else StackBlue,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Next Turn Button (only at turn end)
        if (state.currentStep.id == StepId.CLEANUP && !state.awaitingMandatoryAction && state.priority == PriorityHolder.NONE) {
            Button(
                onClick = { vm.nextTurn() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🔄  Nächster Zug (Runde ${state.turnNumber + 1})",
                    color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ─── Status Message ───────────────────────────────────────────────────────────

@Composable
fun StatusMessage(message: String) {
    Surface(color = CardDarker, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF2A4A2A))) {
        Row(modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top) {
            Text("📋", fontSize = 14.sp)
            Text(message, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

// ─── Special Rules Card ───────────────────────────────────────────────────────

@Composable
fun SpecialRulesCard(step: StepDefinition) {
    var expanded by remember(step.id) { mutableStateOf(false) }

    Surface(color = CardDarker, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2A3A2A))) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 14.sp)
                    Text("Spezialregeln & Hinweise", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(if (expanded) "▲" else "▼", color = TextMuted, fontSize = 12.sp)
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    step.specialRules.forEachIndexed { idx, rule ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                            Text("•", color = NeonGreen, fontSize = 14.sp, modifier = Modifier.width(14.dp))
                            Text(rule, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Referenz: ${step.rulesReference}", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

// ─── Add To Stack Dialog ──────────────────────────────────────────────────────

@Composable
fun AddToStackDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Spontanzauber") }
    val types = listOf("Spontanzauber", "Hexerei", "Aktivierte Fähigkeit", "Trigger-Effekt", "Sonstiges")

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Effekt auf Stack legen", color = TextPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung", color = TextMuted) },
                    placeholder = { Text("z.B. Naturwuchs, Tap-Fähigkeit...", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = Color(0xFF3A5A3A),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextSecondary
                    )
                )

                Spacer(Modifier.height(12.dp))
                Text("Typ:", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(types) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                                selectedLabelColor = NeonGreen
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
                        Text("Abbrechen", color = TextSecondary)
                    }
                    Button(onClick = { onConfirm(description, selectedType) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.8f))) {
                        Text("Auf Stack", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Settings Dialog ──────────────────────────────────────────────────────────

@Composable
fun SettingsDialog(state: GameState, vm: GameViewModel, onDismiss: () -> Unit) {
    var p1 by remember { mutableStateOf(state.activePlayerName) }
    var p2 by remember { mutableStateOf(state.nonActivePlayerName) }
    var firstStrike by remember { mutableStateOf(state.includeFirstStrike) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Einstellungen", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(value = p1, onValueChange = { p1 = it },
                    label = { Text("⚔️ Aktiver Spieler", color = Gold.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF3A5A3A),
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextSecondary))

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = p2, onValueChange = { p2 = it },
                    label = { Text("🛡️ Passiver Spieler", color = Silver.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Silver, unfocusedBorderColor = Color(0xFF3A5A3A),
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextSecondary))

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Erstangriff-Schadensschritt", color = TextSecondary, fontSize = 14.sp)
                        Text("Einblenden wenn First/Double Strike", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(checked = firstStrike, onCheckedChange = { firstStrike = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = GoldDark.copy(alpha = 0.5f)))
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
                        Text("Abbrechen", color = TextSecondary)
                    }
                    Button(onClick = {
                        vm.setPlayerNames(p1, p2)
                        vm.toggleFirstStrike(firstStrike)
                        onDismiss()
                    }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold.copy(alpha = 0.9f))) {
                        Text("Speichern", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.resetGame(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("🔄 Spiel zurücksetzen", color = AlertRed.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
    }
}

// ─── Reference Sheet ──────────────────────────────────────────────────────────

@Composable
fun ReferenceSheet(vm: GameViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxSize(0.95f),
            border = BorderStroke(1.dp, Color(0xFF3A5A3A))) {
            Column {
                // Header
                Surface(color = CardDark) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Prioritäts-Übersicht", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Schließen", tint = TextSecondary)
                        }
                    }
                }

                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Quick Rules
                    item {
                        Surface(color = CardDark, shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Kern-Prioritätsregeln (CR 117)", color = NeonGreen,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                listOf(
                                    "AP erhält immer als erster Priorität in einem neuen Schritt",
                                    "Nach einer Auflösung erhält immer AP Priorität (CR 117.3c)",
                                    "Nach einer Aktion behält der Spieler Priorität (CR 117.3d)",
                                    "Beide passen + Stack leer → Schritt endet (CR 117.6)",
                                    "Beide passen + Stack voll → Top löst auf, AP erhält Priorität",
                                    "Hexerei-Speed: Nur AP, Hauptphase, Stack leer (CR 307.1)",
                                    "Instant-Speed: Immer wenn man Priorität hat (CR 116.1)"
                                ).forEach { rule ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("→", color = NeonGreen, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                                        Text(rule, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    }

                    // All steps summary
                    item { Text("Alle Schritte", color = TextMuted, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp)) }

                    items(ALL_STEPS) { step ->
                        ReferenceStepRow(step)
                    }
                }
            }
        }
    }
}

@Composable
fun ReferenceStepRow(step: StepDefinition) {
    val phaseColor = when (step.phase) {
        PhaseType.BEGINNING -> Teal
        PhaseType.MAIN_PRE, PhaseType.MAIN_POST -> Gold
        PhaseType.COMBAT -> AlertRed
        PhaseType.ENDING -> WarningOrange
    }

    Surface(color = CardDarker, shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.2f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top) {
            // Step badge
            Surface(color = phaseColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(32.dp)) {
                Text(step.shortName, color = phaseColor, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(step.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))

                // Priority indicator
                val prioText = when {
                    !step.hasPriority && !step.isConditional -> "❌ Keine Priorität"
                    step.isConditional -> "⚠️ Bedingt (bei Trigger)"
                    step.hasMandatoryAction -> "⚡ Nach Pflichtaktion → AP"
                    else -> "✅ AP erhält Priorität zuerst"
                }
                Text(prioText, color = when {
                    !step.hasPriority && !step.isConditional -> AlertRed.copy(alpha = 0.8f)
                    step.isConditional -> WarningOrange
                    else -> NeonGreen
                }, fontSize = 11.sp)

                // Instant/Sorcery options
                if (step.hasPriority) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚡ Instant", color = if (step.apCastingOptions.instants) NeonGreen else TextMuted,
                            fontSize = 10.sp)
                        Text("📜 Sorcery (AP)", color = if (step.apCastingOptions.hasSorcerySpeed) Gold else TextMuted,
                            fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
