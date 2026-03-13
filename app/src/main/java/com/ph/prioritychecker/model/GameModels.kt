package com.ph.prioritychecker.model

// ─── Phase & Step Enums ────────────────────────────────────────────────────

enum class PhaseType(val displayName: String, val shortName: String) {
    BEGINNING("Beginn", "BEG"),
    MAIN_PRE("1. Hauptphase", "HP1"),
    COMBAT("Kampfphase", "KPF"),
    MAIN_POST("2. Hauptphase", "HP2"),
    ENDING("Endphase", "END")
}

enum class StepId {
    UNTAP, UPKEEP, DRAW,
    MAIN_PRE,
    BEGINNING_OF_COMBAT, DECLARE_ATTACKERS, DECLARE_BLOCKERS,
    FIRST_STRIKE_DAMAGE, COMBAT_DAMAGE, END_OF_COMBAT,
    MAIN_POST,
    END_STEP, CLEANUP
}

enum class PriorityHolder(val label: String, val emoji: String) {
    ACTIVE_PLAYER("Aktiver Spieler", "⚔️"),
    NON_ACTIVE_PLAYER("Passiver Spieler", "🛡️"),
    NONE("Niemand", "⏸️");

    fun flip() = when (this) {
        ACTIVE_PLAYER -> NON_ACTIVE_PLAYER
        NON_ACTIVE_PLAYER -> ACTIVE_PLAYER
        NONE -> NONE
    }
}

// ─── What can be played ───────────────────────────────────────────────────

data class CastingOptions(
    // Instantgeschwindigkeit – immer wenn man Priorität hat
    val instants: Boolean = false,
    val activatedAbilities: Boolean = false,
    val flashCards: Boolean = false,
    // Hexereigeschwindigkeit – nur AP in Hauptphase, Stack leer
    val sorceries: Boolean = false,
    val creatures: Boolean = false,
    val artifacts: Boolean = false,
    val enchantments: Boolean = false,
    val planeswalkers: Boolean = false,
    val battles: Boolean = false
) {
    val hasSorcerySpeed get() = sorceries || creatures || artifacts || enchantments || planeswalkers || battles
    val hasInstantSpeed get() = instants || activatedAbilities || flashCards
    val hasAnything get() = hasSorcerySpeed || hasInstantSpeed

    companion object {
        val NONE = CastingOptions()

        val INSTANT_SPEED = CastingOptions(
            instants = true,
            activatedAbilities = true,
            flashCards = true
        )

        val FULL_MAIN_PHASE = CastingOptions(
            instants = true,
            activatedAbilities = true,
            flashCards = true,
            sorceries = true,
            creatures = true,
            artifacts = true,
            enchantments = true,
            planeswalkers = true,
            battles = true
        )
    }
}

// ─── Step Definition ──────────────────────────────────────────────────────

data class StepDefinition(
    val id: StepId,
    val displayName: String,
    val shortName: String,
    val phase: PhaseType,
    // Priority rules
    val hasPriority: Boolean,
    val isConditional: Boolean = false,       // Cleanup: nur wenn Trigger/SBA
    val apGetsFirstPriority: Boolean = true,
    val hasMandatoryAction: Boolean = false,   // Declare Attackers / Blockers etc.
    val mandatoryActionActor: PriorityHolder = PriorityHolder.ACTIVE_PLAYER,
    val mandatoryActionDescription: String = "",
    // Casting rules for the priority holder
    val apCastingOptions: CastingOptions = CastingOptions.NONE,
    val napCastingOptions: CastingOptions = CastingOptions.NONE,
    // Flavor & Descriptions
    val description: String,
    val priorityFlowText: String,
    val rulesReference: String,              // CR-Referenz
    val specialRules: List<String> = emptyList(),
    val triggerText: String = ""
)

// ─── All Game Steps (Comprehensive Rules accurate) ────────────────────────

val ALL_STEPS: List<StepDefinition> = listOf(

    StepDefinition(
        id = StepId.UNTAP,
        displayName = "Enttapp-Schritt",
        shortName = "UT",
        phase = PhaseType.BEGINNING,
        hasPriority = false,
        hasMandatoryAction = true,
        mandatoryActionActor = PriorityHolder.ACTIVE_PLAYER,
        mandatoryActionDescription = "AP enttappt alle seine Permanents (sofern keine Effekte dies verhindern).",
        apCastingOptions = CastingOptions.NONE,
        napCastingOptions = CastingOptions.NONE,
        description = "Der aktive Spieler enttappt alle seine Permanents. Kein Spieler erhält in diesem Schritt Priorität – Effekte können nicht gespielt werden.",
        priorityFlowText = "⏸️ Kein Spieler erhält Priorität.\nPflichtaktion wird automatisch ausgeführt.",
        rulesReference = "CR 502, 500.4",
        specialRules = listOf(
            "Kein Spieler erhält Priorität im Enttapp-Schritt (CR 502.2)",
            "Phasing findet vor dem Enttappen statt (CR 502.1)",
            "'Enttappt nicht' Effekte (z.B. Tap-Land) verhindern das Enttappen",
            "Trigger, die 'beim Enttappen' auslösen, warten bis zum Versorgungsschritt"
        )
    ),

    StepDefinition(
        id = StepId.UPKEEP,
        displayName = "Versorgungsschritt",
        shortName = "UP",
        phase = PhaseType.BEGINNING,
        hasPriority = true,
        apGetsFirstPriority = true,
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Trigger-Effekte mit 'Am Beginn des Versorgungsschritts' werden auf den Stack gelegt. Dann erhält der aktive Spieler Priorität.",
        priorityFlowText = "⚔️ AP erhält Priorität\n→ AP gibt ab\n🛡️ NAP erhält Priorität\n→ NAP gibt ab\n✅ Stack leer → Nächster Schritt\n🔄 Stack hat Inhalt → Oberstes auflösen, AP erhält Priorität",
        rulesReference = "CR 503",
        triggerText = "'Am Beginn deines Versorgungsschritts' Trigger",
        specialRules = listOf(
            "AP erhält immer zuerst Priorität nach Triggern (APNAP-Regel, CR 101.4)",
            "Spontanzauber (Instants) und aktivierte Fähigkeiten können gespielt werden",
            "Hexereien (Sorceries) können NICHT gespielt werden",
            "Kein sorcery-speed Spielen von Kreaturen, Artefakten, etc."
        )
    ),

    StepDefinition(
        id = StepId.DRAW,
        displayName = "Ziehschritt",
        shortName = "ZI",
        phase = PhaseType.BEGINNING,
        hasPriority = true,
        hasMandatoryAction = true,
        mandatoryActionActor = PriorityHolder.ACTIVE_PLAYER,
        mandatoryActionDescription = "AP zieht eine Karte (Pflichtaktion, bevor Priorität vergeben wird).",
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Der aktive Spieler zieht eine Karte. Danach werden Trigger ausgelöst, und der aktive Spieler erhält Priorität.",
        priorityFlowText = "📋 AP zieht Karte (Pflicht)\n→ Trigger auf Stack\n⚔️ AP erhält Priorität\n→ AP gibt ab → NAP gibt ab → ✅",
        rulesReference = "CR 504",
        triggerText = "'Immer wenn ein Spieler eine Karte zieht' Trigger",
        specialRules = listOf(
            "Das Ziehen der Karte ist eine Pflichtaktion (kein Teil des Stack-Systems)",
            "Nach dem Ziehen erhalten Trigger ihre Gelegenheit, auf den Stack zu gehen",
            "Spontanzauber und aktivierte Fähigkeiten können danach gespielt werden",
            "Im ersten Zug (Startspieler) wird keine Karte gezogen (sofern Regelset)"
        )
    ),

    StepDefinition(
        id = StepId.MAIN_PRE,
        displayName = "1. Hauptphase",
        shortName = "HP1",
        phase = PhaseType.MAIN_PRE,
        hasPriority = true,
        apGetsFirstPriority = true,
        apCastingOptions = CastingOptions.FULL_MAIN_PHASE,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Der aktive Spieler hat volle Spielmöglichkeiten (Hexerei-Geschwindigkeit), wenn der Stack leer ist. Der passive Spieler kann nur auf Instant-Speed reagieren.",
        priorityFlowText = "⚔️ AP erhält Priorität (Stack leer)\n→ AP kann alles spielen\n→ AP gibt ab → NAP reagiert\n→ beide geben ab, Stack leer → Kampfphase",
        rulesReference = "CR 505",
        specialRules = listOf(
            "AP kann Hexereien, Kreaturen, Artefakte, Verzauberungen, Planeswalker spielen (Stack muss leer sein!)",
            "Länder können nur in der Hauptphase gespielt werden (eine pro Zug)",
            "NAP kann nur auf Instant-Speed reagieren",
            "Sobald AP etwas auf den Stack legt, muss der Stack aufgelöst werden, bevor AP wieder sorcery-speed spielen kann",
            "Planeswalker-Fähigkeiten (+/−/0) sind aktivierte Fähigkeiten → Instant-Speed"
        )
    ),

    StepDefinition(
        id = StepId.BEGINNING_OF_COMBAT,
        displayName = "Beginn der Kampfphase",
        shortName = "BK",
        phase = PhaseType.COMBAT,
        hasPriority = true,
        apGetsFirstPriority = true,
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Letzter Moment vor der Angriffserklärung. Wichtig für 'Am Beginn der Kampfphase'-Trigger und für Reaktionen bevor Angreifer erklärt werden.",
        priorityFlowText = "⚔️ AP erhält Priorität\n→ 'Beginn Kampfphase' Trigger\n→ Letzte Chance vor Angreifern\n→ beide geben ab → Angreifer erklären",
        rulesReference = "CR 507",
        triggerText = "'Am Beginn der Kampfphase' Trigger",
        specialRules = listOf(
            "Letzter Moment, um Kreaturen zu eliminieren, bevor sie angreifen",
            "NAP kann Reaktionen spielen, bevor Angreifer deklariert werden",
            "Tap-Effekte auf potentielle Angreifer können hier gespielt werden",
            "Kein sorcery-speed Spielen möglich (kein Hauptphasen-Schritt)"
        )
    ),

    StepDefinition(
        id = StepId.DECLARE_ATTACKERS,
        displayName = "Angriffserklärung",
        shortName = "ANG",
        phase = PhaseType.COMBAT,
        hasPriority = true,
        hasMandatoryAction = true,
        mandatoryActionActor = PriorityHolder.ACTIVE_PLAYER,
        mandatoryActionDescription = "AP erklärt, welche Kreaturen angreifen (und wen/was). Dies ist keine Stack-Aktion.",
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Der aktive Spieler erklärt seine Angreifer. Alle 'Greift an'-Trigger gehen auf den Stack. Dann erhält der aktive Spieler Priorität.",
        priorityFlowText = "⚔️ AP erklärt Angreifer (Pflicht, kein Stack)\n→ 'Wenn ~ angreift' Trigger\n⚔️ AP erhält Priorität\n→ AP gibt ab → NAP reagiert → ...",
        rulesReference = "CR 508",
        triggerText = "'Immer wenn ~ angreift' Trigger",
        specialRules = listOf(
            "Angriff kostet: Kreatur muss getappt werden (außer sie hat Vigilance)",
            "Kreaturen mit Summoning Sickness können NICHT angreifen",
            "Nach Angriffserklärung: Attackierende Kreaturen sind 'attacking' bis Ende des Kampfschritts",
            "Mehrere Trigger (APNAP-Reihenfolge): AP-Trigger zuerst auf den Stack",
            "NAP kann jetzt auf die Angreifer reagieren (z.B. Instant-Blocker mit Flash)"
        )
    ),

    StepDefinition(
        id = StepId.DECLARE_BLOCKERS,
        displayName = "Blockererklärung",
        shortName = "BLK",
        phase = PhaseType.COMBAT,
        hasPriority = true,
        hasMandatoryAction = true,
        mandatoryActionActor = PriorityHolder.NON_ACTIVE_PLAYER,
        mandatoryActionDescription = "NAP erklärt, welche Kreaturen welche Angreifer blockieren. AP ordnet dann Kampfschadensreihenfolge bei mehreren Blockern.",
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Der passive Spieler erklärt Blocker. Bei mehreren Blockern ordnet der AP die Schadensreihenfolge. Trigger gehen auf den Stack, dann erhält der aktive Spieler Priorität.",
        priorityFlowText = "🛡️ NAP erklärt Blocker (Pflicht, kein Stack)\n→ AP ordnet Kampfschaden-Reihenfolge\n→ 'Wenn ~ blockt/geblockt wird' Trigger\n⚔️ AP erhält Priorität",
        rulesReference = "CR 509",
        triggerText = "'Wenn ~ blockt' / 'Wenn ~ geblockt wird' Trigger",
        specialRules = listOf(
            "Jede Kreatur kann max. 1 Angreifer blocken (außer Spezialregeln)",
            "Ein Angreifer kann von mehreren Kreatureb geblockt werden",
            "AP ordnet bei mehreren Blockern die Schadensreihenfolge (wichtig für Trample)",
            "Geblockte Kreatur bleibt 'blocked' auch wenn Blocker entfernt wird (außer Fähigkeit)",
            "First Strike / Double Strike Kreaturen beeinflussen den nächsten Schritt"
        )
    ),

    StepDefinition(
        id = StepId.FIRST_STRIKE_DAMAGE,
        displayName = "Erstangriff-Schaden",
        shortName = "EA",
        phase = PhaseType.COMBAT,
        hasPriority = true,
        hasMandatoryAction = true,
        mandatoryActionActor = PriorityHolder.ACTIVE_PLAYER,
        mandatoryActionDescription = "Erstangriff- und Doppelschlag-Kreaturen teilen ihren Kampfschaden zu.",
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Nur vorhanden wenn Erstangriff- oder Doppelschlag-Kreaturen am Kampf beteiligt sind. Schaden wird zugeteilt, Zustandsbasierte Aktionen werden geprüft, dann erhält AP Priorität.",
        priorityFlowText = "⚡ First/Double Strike Schaden\n→ SBA-Prüfung (tödlicher Schaden etc.)\n→ 'Wenn ~ Kampfschaden zufügt' Trigger\n⚔️ AP erhält Priorität",
        rulesReference = "CR 510.1, 702.7",
        triggerText = "'Wenn ~ Kampfschaden zufügt' Trigger (für First/Double Striker)",
        specialRules = listOf(
            "Dieser Schritt existiert NUR wenn First Strike oder Double Strike Kreaturen kämpfen",
            "Double Strike Kreaturen teilen in DIESEM Schritt UND im normalen Kampfschadenschritt Schaden zu",
            "Nach SBA-Prüfung können Kreaturen sterben, bevor normaler Schaden zugeteilt wird",
            "AP erhält Priorität – kann auf 'stirbt'-Trigger oder andere Effekte reagieren"
        )
    ),

    StepDefinition(
        id = StepId.COMBAT_DAMAGE,
        displayName = "Kampfschadenschritt",
        shortName = "KS",
        phase = PhaseType.COMBAT,
        hasPriority = true,
        hasMandatoryAction = true,
        mandatoryActionActor = PriorityHolder.ACTIVE_PLAYER,
        mandatoryActionDescription = "Alle verbleibenden Kampfkreaturen (ohne First Strike) teilen Schaden zu. Schaden wird gleichzeitig zugeteilt.",
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Normaler Kampfschaden wird zugeteilt (alle gleichzeitig). Zustandsbasierte Aktionen werden geprüft, Trigger gehen auf den Stack, AP erhält Priorität.",
        priorityFlowText = "⚔️ Kampfschaden wird zugeteilt (gleichzeitig)\n→ SBA (tödlicher Schaden, Schadensmarkierungen)\n→ 'Wenn ~ Kampfschaden zufügt' Trigger\n⚔️ AP erhält Priorität",
        rulesReference = "CR 510",
        triggerText = "'Wenn ~ Kampfschaden zufügt' / 'Wenn ~ stirbt' Trigger",
        specialRules = listOf(
            "Schaden wird GLEICHZEITIG zugeteilt (kein Priorisieren zwischen Angreifer/Blocker möglich)",
            "Trample: Überschussschaden geht an Spieler/Planeswalker, wenn Blocker tödlich verwundet",
            "Deathtouch: Jeglicher Schaden von einer Deathtouch-Kreatur ist tödlich",
            "Lifelink: Schaden erzeugt gleichzeitig Leben (Trigger auf Stack nach SBA)",
            "Unblockte Kreaturen fügen dem Angriffsziel Schaden zu (Spieler, Planeswalker, Battle)"
        )
    ),

    StepDefinition(
        id = StepId.END_OF_COMBAT,
        displayName = "Ende der Kampfphase",
        shortName = "EK",
        phase = PhaseType.COMBAT,
        hasPriority = true,
        apGetsFirstPriority = true,
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Letzter Schritt der Kampfphase. 'Am Ende der Kampfphase' Trigger werden ausgelöst. Angreifer und Blocker verlassen den 'kämpfend'-Status.",
        priorityFlowText = "⚔️ AP erhält Priorität\n→ 'Ende der Kampfphase' Trigger\n→ beide geben ab → 2. Hauptphase",
        rulesReference = "CR 511",
        triggerText = "'Am Ende der Kampfphase' Trigger",
        specialRules = listOf(
            "Kreaturen, die 'bis zum Ende der Kampfphase' Effekte haben, verlieren diese hier",
            "Angreifer/Blocker-Status wird entfernt",
            "Noch immer Instant-Speed Spielen möglich (letzte Chance in Kampfphase)",
            "'Am Ende der Kampfphase'-Trigger gehen als erstes auf den Stack"
        )
    ),

    StepDefinition(
        id = StepId.MAIN_POST,
        displayName = "2. Hauptphase",
        shortName = "HP2",
        phase = PhaseType.MAIN_POST,
        hasPriority = true,
        apGetsFirstPriority = true,
        apCastingOptions = CastingOptions.FULL_MAIN_PHASE,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Identisch mit der 1. Hauptphase. AP kann wieder auf Hexerei-Geschwindigkeit spielen wenn Stack leer ist.",
        priorityFlowText = "⚔️ AP erhält Priorität (Stack leer)\n→ AP kann alles spielen\n→ AP gibt ab → NAP reagiert\n→ beide geben ab, Stack leer → Endphase",
        rulesReference = "CR 505",
        specialRules = listOf(
            "Identische Regeln wie die 1. Hauptphase",
            "Länder können NUR einmal pro Zug gespielt werden (nicht nochmal wenn schon gespielt)",
            "Gute Zeit für Karten, die man nach dem Kampf spielen möchte",
            "AP kann Planeswalker aktivieren (falls noch nicht getan)",
            "Planeswalker können maximal einmal pro Zug eine Fähigkeit aktivieren"
        )
    ),

    StepDefinition(
        id = StepId.END_STEP,
        displayName = "Endschritt",
        shortName = "ES",
        phase = PhaseType.ENDING,
        hasPriority = true,
        apGetsFirstPriority = true,
        apCastingOptions = CastingOptions.INSTANT_SPEED,
        napCastingOptions = CastingOptions.INSTANT_SPEED,
        description = "Trigger mit 'Am Beginn des Endschritts' werden ausgelöst und auf den Stack gelegt. Dann erhält AP Priorität.",
        priorityFlowText = "⚔️ AP erhält Priorität\n→ 'Beginn Endschritt' Trigger\n→ beide geben ab → Aufräumschritt",
        rulesReference = "CR 513",
        triggerText = "'Am Beginn des Endschritts' Trigger",
        specialRules = listOf(
            "'Am Beginn deines Endschritts'-Trigger (z.B. 'Gib ~ am Ende des Zuges zurück') werden hier ausgelöst",
            "Instants können noch gespielt werden – letzter Moment für Instant-Speed Reaktionen",
            "Kein sorcery-speed Spielen möglich",
            "Übrig behaltener Mana geht NICHT in den Aufräumschritt (Mana brennt)"
        )
    ),

    StepDefinition(
        id = StepId.CLEANUP,
        displayName = "Aufräumschritt",
        shortName = "AUF",
        phase = PhaseType.ENDING,
        hasPriority = false,
        isConditional = true,
        hasMandatoryAction = true,
        mandatoryActionActor = PriorityHolder.ACTIVE_PLAYER,
        mandatoryActionDescription = "AP wirft Karten ab bis auf Handkartenlimit (standard: 7). Kampfschaden wird entfernt. 'Bis Zugabende'-Effekte enden.",
        apCastingOptions = CastingOptions.NONE,
        napCastingOptions = CastingOptions.NONE,
        description = "AP wirft auf Handkartenlimit ab. Schaden von Kreaturen wird entfernt. 'Bis Zugabende' Effekte enden. Normalerweise keine Priorität.",
        priorityFlowText = "📋 Pflichtaktionen (kein Stack):\n• AP wirft Karten ab (max. Handkartenlimit)\n• Kampfschaden wird entfernt\n• 'Bis Zugabende' Effekte enden\n⚠️ NUR wenn SBA/Trigger: AP erhält Priorität",
        rulesReference = "CR 514",
        specialRules = listOf(
            "Normalerweise KEINE Priorität – Karten können nicht gespielt werden (CR 514.3)",
            "Handkartenlimit ist Standard 7, kann durch Effekte geändert werden",
            "Kampfschaden wird von allen Kreaturen entfernt",
            "'Bis zum Ende des Zuges' / 'Diesen Zug' Effekte enden",
            "AUSNAHME: Wenn Trigger ausgelöst werden oder SBA eintreten → AP erhält Priorität → dann weiterer Aufräumschritt",
            "Mana brennt am Ende jedes Schrittes/jeder Phase (leeres Manapool)"
        )
    )
)

// ─── Game State ──────────────────────────────────────────────────────────

data class StackItem(
    val id: Long,
    val description: String,
    val controlledByActive: Boolean,
    val itemType: String = "Effekt",
    val card: ScryfallCard? = null
) {
    fun controllerLabel(apShort: String, napShort: String) =
        if (controlledByActive) apShort else napShort
}

data class GameState(
    val turnNumber: Int = 1,
    val activePlayerName: String = "Spieler 1",
    val nonActivePlayerName: String = "Spieler 2",
    val currentStepIndex: Int = 0,
    val priority: PriorityHolder = PriorityHolder.NONE,
    val stack: List<StackItem> = emptyList(),
    val lastPassedBy: PriorityHolder? = null,
    val awaitingMandatoryAction: Boolean = true,
    val statusMessage: String = "Runde 1: Enttapp-Schritt.",
    val includeFirstStrike: Boolean = false,
    val language: Language = Language.GERMAN
) {
    val currentStep get() = ALL_STEPS[currentStepIndex]

    fun priorityHolderName(nobody: String) = when (priority) {
        PriorityHolder.ACTIVE_PLAYER -> activePlayerName
        PriorityHolder.NON_ACTIVE_PLAYER -> nonActivePlayerName
        PriorityHolder.NONE -> nobody
    }

    fun currentCastingOptions(): CastingOptions {
        if (priority == PriorityHolder.NONE) return CastingOptions.NONE
        val step = currentStep
        return when (priority) {
            PriorityHolder.ACTIVE_PLAYER -> {
                val isMainPhase = step.id == StepId.MAIN_PRE || step.id == StepId.MAIN_POST
                if (isMainPhase && stack.isEmpty()) step.apCastingOptions
                else step.apCastingOptions.copy(
                    sorceries = false, creatures = false, artifacts = false,
                    enchantments = false, planeswalkers = false, battles = false
                )
            }
            PriorityHolder.NON_ACTIVE_PLAYER -> step.napCastingOptions
            PriorityHolder.NONE -> CastingOptions.NONE
        }
    }

    fun bothPassedJustNow(justPassed: PriorityHolder): Boolean {
        return lastPassedBy != null && lastPassedBy != justPassed
    }
}
