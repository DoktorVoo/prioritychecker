package com.ph.prioritychecker.viewmodel

import androidx.lifecycle.ViewModel
import com.ph.prioritychecker.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    // ─── Player Name Settings ────────────────────────────────────────────

    fun setPlayerNames(p1: String, p2: String) {
        _state.value = _state.value.copy(
            activePlayerName = p1.ifBlank { "Spieler 1" },
            nonActivePlayerName = p2.ifBlank { "Spieler 2" }
        )
    }

    // ─── Mandatory Action Confirmation ───────────────────────────────────

    fun confirmMandatoryAction() {
        val s = _state.value
        val step = s.currentStep
        if (!s.awaitingMandatoryAction) return

        val newPriority = when {
            !step.hasPriority -> PriorityHolder.NONE
            step.isConditional -> PriorityHolder.NONE
            else -> PriorityHolder.ACTIVE_PLAYER
        }

        val msg = when (step.id) {
            StepId.UNTAP -> "Enttapp abgeschlossen. Weiter zum Versorgungsschritt."
            StepId.DRAW -> "${s.activePlayerName} hat eine Karte gezogen. ${s.activePlayerName} erhält Priorität."
            StepId.DECLARE_ATTACKERS -> "Angreifer erklärt. ${s.activePlayerName} erhält Priorität."
            StepId.DECLARE_BLOCKERS -> "Blocker erklärt + Schadensreihenfolge festgelegt. ${s.activePlayerName} erhält Priorität."
            StepId.FIRST_STRIKE_DAMAGE -> "Erstangriff-Schaden zugeteilt. Zustandsbasierte Aktionen geprüft. ${s.activePlayerName} erhält Priorität."
            StepId.COMBAT_DAMAGE -> "Kampfschaden zugeteilt. Zustandsbasierte Aktionen geprüft. ${s.activePlayerName} erhält Priorität."
            StepId.CLEANUP -> "Handgröße angepasst, Schaden entfernt, Zugabende-Effekte beendet."
            else -> "${s.activePlayerName} erhält Priorität."
        }

        _state.value = s.copy(
            awaitingMandatoryAction = false,
            priority = newPriority,
            lastPassedBy = null,
            statusMessage = msg
        )
    }

    // ─── Pass Priority (CR 117.4 / 117.5 / 117.6) ────────────────────────

    fun passPriority() {
        val s = _state.value
        if (s.priority == PriorityHolder.NONE) return
        if (s.awaitingMandatoryAction) return

        val passer = s.priority

        if (s.bothPassedJustNow(passer)) {
            // Both players passed in succession (CR 117.6)
            if (s.stack.isEmpty()) {
                // Empty stack → advance to next step
                advanceToNextStep()
            } else {
                // Stack has items → resolve top item (CR 117.6a)
                resolveTopOfStack()
            }
        } else {
            // Give priority to the other player (CR 117.4)
            val next = passer.flip()
            _state.value = s.copy(
                priority = next,
                lastPassedBy = passer,
                statusMessage = "${s.priorityHolderName()} gibt Priorität ab → ${
                    if (next == PriorityHolder.ACTIVE_PLAYER) s.activePlayerName 
                    else s.nonActivePlayerName
                } erhält Priorität."
            )
        }
    }

    // ─── Add Spell / Ability to Stack ─────────────────────────────────────

    fun addToStack(description: String, itemType: String) {
        val s = _state.value
        if (s.priority == PriorityHolder.NONE) return
        if (s.awaitingMandatoryAction) return

        val newItem = StackItem(
            id = System.currentTimeMillis(),
            description = description.ifBlank { "Unbenannter Effekt" },
            controlledByActive = s.priority == PriorityHolder.ACTIVE_PLAYER,
            itemType = itemType
        )

        // After casting: same player retains priority (CR 117.3d)
        _state.value = s.copy(
            stack = s.stack + newItem,
            lastPassedBy = null, // Reset "both passed" tracking
            // Same priority holder retains priority after playing
            statusMessage = "«${newItem.description}» auf den Stack gelegt. ${s.priorityHolderName()} behält Priorität."
        )
    }

    fun removeFromStack(itemId: Long) {
        val s = _state.value
        _state.value = s.copy(
            stack = s.stack.filter { it.id != itemId },
            statusMessage = "Effekt manuell vom Stack entfernt."
        )
    }

    // ─── Toggle First Strike Step ─────────────────────────────────────────

    fun toggleFirstStrike(include: Boolean) {
        _state.value = _state.value.copy(includeFirstStrike = include)
    }

    // ─── Manually navigate steps (for learning/reference) ─────────────────

    fun goToStep(index: Int) {
        if (index < 0 || index >= filteredSteps().size) return
        val stepDef = filteredSteps()[index]
        val realIndex = ALL_STEPS.indexOf(stepDef)
        val newPriority = when {
            !stepDef.hasPriority -> PriorityHolder.NONE
            stepDef.hasMandatoryAction -> PriorityHolder.NONE
            else -> PriorityHolder.ACTIVE_PLAYER
        }
        _state.value = _state.value.copy(
            currentStepIndex = realIndex,
            priority = newPriority,
            lastPassedBy = null,
            stack = emptyList(),
            awaitingMandatoryAction = stepDef.hasMandatoryAction,
            statusMessage = "Manuell zu '${stepDef.displayName}' gesprungen."
        )
    }

    // ─── New Turn ─────────────────────────────────────────────────────────

    fun nextTurn() {
        val s = _state.value
        _state.value = GameState(
            turnNumber = s.turnNumber + 1,
            // Swap active / non-active player
            activePlayerName = s.nonActivePlayerName,
            nonActivePlayerName = s.activePlayerName,
            currentStepIndex = 0, // Start at UNTAP
            priority = PriorityHolder.NONE,
            stack = emptyList(),
            lastPassedBy = null,
            awaitingMandatoryAction = true,
            includeFirstStrike = s.includeFirstStrike,
            statusMessage = "Runde ${s.turnNumber + 1}: ${s.nonActivePlayerName} ist jetzt aktiver Spieler. Enttapp-Schritt."
        )
    }

    fun resetGame() {
        _state.value = buildInitialState()
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────

    private fun resolveTopOfStack() {
        val s = _state.value
        val resolved = s.stack.last()
        val newStack = s.stack.dropLast(1)

        // After resolution: AP always gets priority first (CR 117.3c)
        _state.value = s.copy(
            stack = newStack,
            priority = PriorityHolder.ACTIVE_PLAYER,
            lastPassedBy = null,
            statusMessage = "«${resolved.description}» aufgelöst. ${s.activePlayerName} erhält Priorität."
        )
    }

    private fun advanceToNextStep() {
        val s = _state.value
        val steps = filteredSteps()
        val currentInFiltered = steps.indexOfFirst { it.id == s.currentStep.id }
        val nextInFiltered = currentInFiltered + 1

        if (nextInFiltered >= steps.size) {
            // End of turn
            _state.value = s.copy(
                statusMessage = "Zugabende erreicht. Drücke 'Nächster Zug' um fortzufahren.",
                priority = PriorityHolder.NONE,
                lastPassedBy = null,
                stack = emptyList()
            )
            return
        }

        val nextStep = steps[nextInFiltered]
        val realIndex = ALL_STEPS.indexOf(nextStep)
        val hasMandatory = nextStep.hasMandatoryAction

        val newPriority = when {
            hasMandatory -> PriorityHolder.NONE
            !nextStep.hasPriority -> PriorityHolder.NONE
            nextStep.isConditional -> PriorityHolder.NONE
            else -> PriorityHolder.ACTIVE_PLAYER
        }

        val msg = buildStepTransitionMessage(s, nextStep, hasMandatory)

        _state.value = s.copy(
            currentStepIndex = realIndex,
            priority = newPriority,
            lastPassedBy = null,
            stack = emptyList(),
            awaitingMandatoryAction = hasMandatory,
            statusMessage = msg
        )
    }

    private fun buildStepTransitionMessage(s: GameState, next: StepDefinition, hasMandatory: Boolean): String {
        return when {
            hasMandatory -> "→ ${next.displayName}: ${next.mandatoryActionDescription}"
            next.id == StepId.CLEANUP -> "→ Aufräumschritt: Handgröße anpassen, Schaden entfernen."
            !next.hasPriority -> "→ ${next.displayName}: Keine Priorität."
            else -> "→ ${next.displayName}: ${s.activePlayerName} erhält Priorität."
        }
    }

    fun filteredSteps(): List<StepDefinition> {
        val s = _state.value
        return ALL_STEPS.filter { step ->
            step.id != StepId.FIRST_STRIKE_DAMAGE || s.includeFirstStrike
        }
    }

    fun currentFilteredIndex(): Int {
        val s = _state.value
        return filteredSteps().indexOfFirst { it.id == s.currentStep.id }
    }

    private fun buildInitialState() = GameState(
        currentStepIndex = 0, // UNTAP
        priority = PriorityHolder.NONE,
        awaitingMandatoryAction = true,
        statusMessage = "Runde 1: Enttapp-Schritt. Aktiver Spieler enttappt seine Permanents."
    )
}
