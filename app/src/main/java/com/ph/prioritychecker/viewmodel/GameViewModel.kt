package com.ph.prioritychecker.viewmodel

import androidx.lifecycle.ViewModel
import com.ph.prioritychecker.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private fun str() = if (_state.value.language == Language.GERMAN) germanStrings() else englishStrings()

    fun setPlayerNames(p1: String, p2: String) {
        _state.value = _state.value.copy(
            activePlayerName = p1.ifBlank { "Spieler 1" },
            nonActivePlayerName = p2.ifBlank { "Spieler 2" }
        )
    }

    fun setLanguage(lang: Language) {
        _state.value = _state.value.copy(language = lang)
    }

    fun toggleFirstStrike(include: Boolean) {
        _state.value = _state.value.copy(includeFirstStrike = include)
    }

    fun confirmMandatoryAction() {
        val s = _state.value
        val step = s.currentStep
        if (!s.awaitingMandatoryAction) return
        val st = str()

        val newPriority = when {
            !step.hasPriority -> PriorityHolder.NONE
            step.isConditional -> PriorityHolder.NONE
            else -> PriorityHolder.ACTIVE_PLAYER
        }

        val msg = when (step.id) {
            StepId.UNTAP -> st.untapDone
            StepId.DRAW -> "${st.drawDone}. ${s.activePlayerName}."
            StepId.DECLARE_ATTACKERS -> st.attackersDeclared
            StepId.DECLARE_BLOCKERS -> st.blockersDeclared
            StepId.FIRST_STRIKE_DAMAGE -> st.firstStrikeDamageDone
            StepId.COMBAT_DAMAGE -> st.combatDamageDone
            StepId.CLEANUP -> st.cleanupDone
            else -> "${s.activePlayerName} ${st.hasPriority.lowercase()}."
        }

        _state.value = s.copy(
            awaitingMandatoryAction = false,
            priority = newPriority,
            lastPassedBy = null,
            statusMessage = msg
        )
    }

    fun passPriority() {
        val s = _state.value
        if (s.priority == PriorityHolder.NONE || s.awaitingMandatoryAction) return
        val st = str()
        val passer = s.priority

        if (s.bothPassedJustNow(passer)) {
            if (s.stack.isEmpty()) advanceToNextStep()
            else resolveTopOfStack()
        } else {
            val next = passer.flip()
            val passerName = if (passer == PriorityHolder.ACTIVE_PLAYER) s.activePlayerName else s.nonActivePlayerName
            val nextName = if (next == PriorityHolder.ACTIVE_PLAYER) s.activePlayerName else s.nonActivePlayerName
            _state.value = s.copy(
                priority = next,
                lastPassedBy = passer,
                statusMessage = "$passerName ${st.passedLabel} → $nextName ${st.hasPriority.lowercase()}."
            )
        }
    }

    fun addToStack(description: String, itemType: String, card: ScryfallCard? = null) {
        val s = _state.value
        if (s.priority == PriorityHolder.NONE || s.awaitingMandatoryAction) return
        val displayName = card?.displayName ?: description.ifBlank { "Effect" }
        val newItem = StackItem(
            id = System.currentTimeMillis(),
            description = displayName,
            controlledByActive = s.priority == PriorityHolder.ACTIVE_PLAYER,
            itemType = itemType,
            card = card
        )
        val added = if (s.language == Language.GERMAN) "auf den Stack gelegt" else "added to stack"
        _state.value = s.copy(
            stack = s.stack + newItem,
            lastPassedBy = null,
            statusMessage = "«$displayName» $added."
        )
    }

    fun removeFromStack(itemId: Long) {
        val s = _state.value
        _state.value = s.copy(
            stack = s.stack.filter { it.id != itemId },
            statusMessage = str().removedFromStack
        )
    }

    fun goToStep(index: Int) {
        val steps = filteredSteps()
        if (index < 0 || index >= steps.size) return
        val stepDef = steps[index]
        val realIndex = ALL_STEPS.indexOf(stepDef)
        val hasMandatory = stepDef.hasMandatoryAction
        val newPriority = when {
            hasMandatory || !stepDef.hasPriority || stepDef.isConditional -> PriorityHolder.NONE
            else -> PriorityHolder.ACTIVE_PLAYER
        }
        _state.value = _state.value.copy(
            currentStepIndex = realIndex,
            priority = newPriority,
            lastPassedBy = null,
            stack = emptyList(),
            awaitingMandatoryAction = hasMandatory,
            statusMessage = "→ ${stepDef.displayName}"
        )
    }

    fun nextTurn() {
        val s = _state.value
        val st = str()
        _state.value = GameState(
            turnNumber = s.turnNumber + 1,
            activePlayerName = s.nonActivePlayerName,
            nonActivePlayerName = s.activePlayerName,
            currentStepIndex = 0,
            priority = PriorityHolder.NONE,
            awaitingMandatoryAction = true,
            includeFirstStrike = s.includeFirstStrike,
            language = s.language,
            statusMessage = "${st.roundLabel} ${s.turnNumber + 1}: ${s.nonActivePlayerName} → ${st.activePlayer}."
        )
    }

    fun resetGame() {
        _state.value = buildInitialState(_state.value.language)
    }

    private fun resolveTopOfStack() {
        val s = _state.value
        val resolved = s.stack.last()
        val word = if (s.language == Language.GERMAN) "aufgelöst" else "resolved"
        _state.value = s.copy(
            stack = s.stack.dropLast(1),
            priority = PriorityHolder.ACTIVE_PLAYER,
            lastPassedBy = null,
            statusMessage = "«${resolved.description}» $word. ${s.activePlayerName} ${str().hasPriority.lowercase()}."
        )
    }

    private fun advanceToNextStep() {
        val s = _state.value
        val steps = filteredSteps()
        val nextInFiltered = steps.indexOfFirst { it.id == s.currentStep.id } + 1

        if (nextInFiltered >= steps.size) {
            _state.value = s.copy(statusMessage = str().turnEnd, priority = PriorityHolder.NONE, lastPassedBy = null, stack = emptyList())
            return
        }

        val nextStep = steps[nextInFiltered]
        val realIndex = ALL_STEPS.indexOf(nextStep)
        val hasMandatory = nextStep.hasMandatoryAction
        val newPriority = when {
            hasMandatory || !nextStep.hasPriority || nextStep.isConditional -> PriorityHolder.NONE
            else -> PriorityHolder.ACTIVE_PLAYER
        }
        val msg = when {
            hasMandatory -> "→ ${nextStep.displayName}: ${nextStep.mandatoryActionDescription}"
            !nextStep.hasPriority || nextStep.isConditional -> "→ ${nextStep.displayName}"
            else -> "→ ${nextStep.displayName}: ${s.activePlayerName} ${str().hasPriority.lowercase()}."
        }
        _state.value = s.copy(
            currentStepIndex = realIndex, priority = newPriority,
            lastPassedBy = null, stack = emptyList(),
            awaitingMandatoryAction = hasMandatory, statusMessage = msg
        )
    }

    fun filteredSteps() = ALL_STEPS.filter { it.id != StepId.FIRST_STRIKE_DAMAGE || _state.value.includeFirstStrike }

    fun currentFilteredIndex() = filteredSteps().indexOfFirst { it.id == _state.value.currentStep.id }

    private fun buildInitialState(lang: Language = Language.GERMAN) = GameState(
        currentStepIndex = 0, priority = PriorityHolder.NONE,
        awaitingMandatoryAction = true, language = lang,
        statusMessage = if (lang == Language.GERMAN)
            "Runde 1: Enttapp-Schritt. AS enttappt seine Permanents."
        else "Round 1: Untap Step. AP untaps their permanents."
    )
}
