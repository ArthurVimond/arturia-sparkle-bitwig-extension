package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.api.*
import fr.arthurvimond.bitwig.extension.sendNoteOff
import fr.arthurvimond.bitwig.extension.sendNoteOn
import fr.arthurvimond.bitwig.extension.turnOffSteps
import fr.arthurvimond.bitwig.state.State
import fr.arthurvimond.bitwig.state.StepsMode
import fr.arthurvimond.bitwig.util.Logger
import fr.arthurvimond.bitwig.util.Sparkle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class StepsManager(
    private val host: ControllerHost,
    private val midiManager: MidiManager,
    private val deviceManager: DeviceManager,
    private val state: State,
    private val midiOut: MidiOut,
    private val logger: Logger,
    private val clipLauncherSlotBank: ClipLauncherSlotBank,
    private val cursorClip: PinnableCursorClip,
) {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val heldNotes: MutableMap<Int, MutableSet<Int>> = mutableMapOf()

    init {

        setupClipLauncherObservers()

        (0 until 16).forEach { i ->
            heldNotes[i] = mutableSetOf()
        }

        // Keep track of the held notes
        // to later turn on or off the LED in playingStep observer
        cursorClip.addNoteStepObserver { noteStep ->
            if (noteStep.y() !in 36..52) return@addNoteStepObserver
            val isOn = noteStep.state() == NoteStep.State.NoteOn
            val selectedPad = state.selectedPad.value
            val index = noteStep.y() - 36
            if (isOn) {
                heldNotes[index]!!.add(noteStep.x())
            } else {
                heldNotes[index]!!.remove(noteStep.x())
            }

            if (state.stepsMode.value != StepsMode.Sequencer) return@addNoteStepObserver
            if (index != selectedPad) return@addNoteStepObserver
            if (isOn) {
                midiOut.sendNoteOn(noteStep.x())
            } else {
                midiOut.sendNoteOff(noteStep.x())
            }
        }

        // Steps Leds feedback
        cursorClip.playingStep().markInterested()
        cursorClip.playingStep().addValueObserver { playingStep ->

            if (state.stepsMode.value != StepsMode.Sequencer) return@addValueObserver
            if (state.isSelectPressed.value) return@addValueObserver

            val previousStep = if (playingStep == 0) state.clipPagePosition.value * 16 - 1 else playingStep - 1

            val selectedPad = state.selectedPad.value
            Sparkle.Note.Steps.forEachIndexed { i, step ->
                // Current step
                if (i == playingStep - state.clipPagePosition.value * 16) {
                    // Check if already On, to temporarily turn it off
                    if (i in heldNotes[selectedPad]!!) {
                        midiOut.sendNoteOff(i)
                    } else {
                        midiOut.sendNoteOn(i)
                    }
                } else if (step !in heldNotes[selectedPad]!!) {
                    midiOut.sendNoteOff(i)
                }

                // Previous step
                if (i == previousStep - state.clipPagePosition.value * 16) {
                    if (i in heldNotes[selectedPad]!!) {
                        midiOut.sendNoteOn(i)
                    }
                }
            }
        }

        // Steps mode selection
        scope.launch {
            midiManager.noteOn.collect {
                when (it.note) {
                    Sparkle.Note.Bank -> {
                        state.setStepsMode(StepsMode.Bank)
                    }

                    Sparkle.Note.Pattern -> {
                        state.setStepsMode(StepsMode.Pattern)
                    }

                    Sparkle.Note.Sequencer -> {
                        state.setStepsMode(StepsMode.Sequencer)
                    }

                    Sparkle.Note.Tune -> {
                        state.setStepsMode(StepsMode.Tune)
                    }
                }
            }
        }

        // Bank mode
        scope.launch {
            midiManager.noteOn
                .filter { state.stepsMode.value == StepsMode.Bank }
                .collect {
                    if (it.note in Sparkle.Note.Steps) {
                        clipLauncherSlotBank.scrollPosition().set(it.note * 16)
                    }
                }
        }

        // Bank steps feedback
        scope.launch {
            state.bank.collect { bank ->
                Sparkle.Note.Steps.forEach { step ->
                    if (bank == step) {
                        host.showPopupNotification("Bank ${bank + 1}")
                        midiOut.sendNoteOn(step)
                    } else {
                        midiOut.sendNoteOff(step)
                    }
                }
            }
        }

        // Pattern mode

        // Bank
        clipLauncherSlotBank.scrollPosition().markInterested()
        clipLauncherSlotBank.scrollPosition().addValueObserver { scrollPosition ->
            state.setBank(scrollPosition / 16)
        }

        clipLauncherSlotBank.addIsSelectedObserver { index, selected ->
            if (selected) state.setSelectedPattern(index)
        }

        clipLauncherSlotBank.addIsPlayingObserver { index, playing ->
            if (playing) state.setPlayingPattern(index)
        }

        // Pattern / Clip selection and launch
        scope.launch {
            midiManager.noteOn
                .filter { state.stepsMode.value == StepsMode.Pattern }
                .collect {
                    if (it.note in Sparkle.Note.Steps) {
                        // Launch clip at index
                        clipLauncherSlotBank.select(it.note)
                        if (state.isSelectPressed.value) return@collect
                        clipLauncherSlotBank.getItemAt(it.note).hasContent()
                        val clipExists = clipLauncherSlotBank.getItemAt(it.note).hasContent().get()
                        if (!clipExists) clipLauncherSlotBank.createEmptyClip(it.note, 4)
                        clipLauncherSlotBank.launch(it.note)
                    }
                }
        }

        // Pattern step led feedback
        scope.launch {
            state.playingPattern
                .filter { state.stepsMode.value == StepsMode.Pattern }
                .collect {
                    Sparkle.Note.Steps.forEach { midiOut.sendNoteOff(it) }
                    midiOut.sendNoteOn(it)
                }
        }

        // Selected / Playing pattern led feedback (when Select is pressed)
        scope.launch {
            state.isSelectPressed
                .filter { state.stepsMode.value == StepsMode.Pattern }
                .collect { isSelectPressed ->
                    if (isSelectPressed) {
                        midiOut.turnOffSteps()
                        midiOut.sendNoteOn(state.selectedPattern.value)
                    } else {
                        midiOut.turnOffSteps()
                        midiOut.sendNoteOn(state.playingPattern.value)
                    }
                }
        }

        // Pattern length led feedback
        scope.launch {
            state.isSelectPressed
                .filter { state.stepsMode.value == StepsMode.Sequencer }
                .collect {
                    updatePatternLengthLedsState()
                }
        }

        // Reset clip page position in range if loop length is reduced
        scope.launch {
            state.isSelectPressed
                .filter { state.stepsMode.value == StepsMode.Sequencer }
                .filter { !it }
                .collect {
                    updatePatternLengthLedsState()
                }
        }

        // Selected pattern led feedback (while Select is pressed)
        scope.launch {
            state.selectedPattern
                .filter { state.isSelectPressed.value }
                .collect {
                    Sparkle.Note.Steps.forEach { midiOut.sendNoteOff(it) }
                    midiOut.sendNoteOn(state.selectedPattern.value)
                }
        }

        // Steps mode leds feedback
        scope.launch {
            state.stepsMode.collect {
                if (it == StepsMode.Bank) {
                    midiOut.sendNoteOn(Sparkle.Note.Bank)
                    midiOut.sendNoteOn(state.bank.value)
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Bank)
                }

                if (it == StepsMode.Pattern) {
                    midiOut.sendNoteOn(Sparkle.Note.Pattern)
                    midiOut.sendNoteOn(state.playingPattern.value)
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Pattern)
                }

                if (it == StepsMode.Sequencer) {
                    midiOut.sendNoteOn(Sparkle.Note.Sequencer)
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Sequencer)
                    midiOut.turnOffSteps()
                }

                if (it == StepsMode.Tune) {
                    midiOut.sendNoteOn(Sparkle.Note.Tune)
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Tune)
                }
            }
        }

        // Steps leds state
        scope.launch {
            state.stepsMode
                .filter { it == StepsMode.Sequencer }
                .collect {
                    updateStepsLedsState()
                }
        }

        scope.launch {
            state.selectedPad
                .filter { state.stepsMode.value == StepsMode.Sequencer }
                .filter { !state.isSelectPressed.value }
                .collect {
                    updateStepsLedsState()
                }
        }

        // Sequencer steps input
        scope.launch {
            midiManager.noteOn
                .filter { state.stepsMode.value == StepsMode.Sequencer }
                .filter { !state.isSelectPressed.value }
                .collect {
                    Sparkle.Note.Steps.forEach { step ->
                        if (it.note == step) {
                            cursorClip.toggleStep(step, state.selectedPad.value + 36, 127)
                        }
                    }
                }
        }

        handleStepsPatternLength()

        // Percentage -> Semitone mapping: https://github.com/mixxxdj/mixxx/wiki/Pitch-Percentages-For-Semitones-And-Notes
        handleTuneSteps()

    }

    private fun setupClipLauncherObservers() {
        (0 until clipLauncherSlotBank.sizeOfBank).forEach { index ->
            val item = clipLauncherSlotBank.getItemAt(index)
            item.hasContent().markInterested()
        }
    }

    suspend fun updateStepsLedsState() {
        val selectedPad = state.selectedPad.value
        midiOut.turnOffSteps()
        // NB: Need delay
        delay(2)
        heldNotes[selectedPad]!!.forEach { step ->
            midiOut.sendNoteOn(step)
        }
    }

    private fun handleStepsPatternLength() {

        cursorClip.loopLength.markInterested()
        cursorClip.loopLength.addValueObserver {
            updatePatternLengthLedsState()
        }

        scope.launch {
            state.clipPagePosition.collect { pagePosition ->
                if (pagePosition == -1) return@collect
                cursorClip.scrollToStep(pagePosition * 16)
                updatePatternLengthLedsState()
            }
        }

        scope.launch {
            midiManager.noteOn.collect {
                when (it.note) {
                    Sparkle.Note.PatternLengthLeft -> {
                        if (state.isSelectPressed.value) {
                            // Reduce the clip length
                            if (cursorClip.loopLength.get() > 4) {
                                val newLoopLength = cursorClip.loopLength.get() - 4
                                cursorClip.loopLength.set(newLoopLength)
                            }
                        } else {
                            // Move the clip launcher bank to the left
                            state.previousClipPagePosition()
                        }
                    }

                    Sparkle.Note.PatternLengthRight -> {
                        if (state.isSelectPressed.value) {
                            // Increase the clip length
                            if (cursorClip.loopLength.get() < 16) {
                                val newLoopLength = cursorClip.loopLength.get() + 4
                                cursorClip.loopLength.set(newLoopLength)
                            }
                        } else {
                            state.nextClipPagePosition(cursorClip.loopLength.get())
                        }
                    }
                }
            }
        }
    }

    private fun updatePatternLengthLedsState() {
        Sparkle.Note.PatternLeds.forEach {
            midiOut.sendNoteOff(it)
        }
        if (state.isSelectPressed.value) {
            val loopLength = cursorClip.loopLength.get()
            when (loopLength) {
                4.0 -> midiOut.sendNoteOn(Sparkle.Note.PatternLed1)
                8.0 -> midiOut.sendNoteOn(Sparkle.Note.PatternLed2)
                12.0 -> midiOut.sendNoteOn(Sparkle.Note.PatternLed3)
                16.0 -> midiOut.sendNoteOn(Sparkle.Note.PatternLed4)
            }
        } else {
            val loopLength = cursorClip.loopLength.get()
            val pagePositionLimit = (loopLength / 4 - 1).toInt()
            if (state.clipPagePosition.value > pagePositionLimit) {
                state.setClipPagePosition(pagePositionLimit)
            }
            when (state.clipPagePosition.value) {
                0 -> midiOut.sendNoteOn(Sparkle.Note.PatternLed1)
                1 -> midiOut.sendNoteOn(Sparkle.Note.PatternLed2)
                2 -> midiOut.sendNoteOn(Sparkle.Note.PatternLed3)
                3 -> midiOut.sendNoteOn(Sparkle.Note.PatternLed4)
            }
        }

    }

    private fun handleTuneSteps() {

        scope.launch {
            midiManager.noteOn
                .filter { state.stepsMode.value == StepsMode.Tune }
                .filter { !state.isSelectPressed.value }
                .collect {
                    if (it.note in Sparkle.Note.Steps) {
                        deviceManager.setSamplerTransposition(it.note - 8)
                    }
                }
        }

    }
}