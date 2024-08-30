package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.api.DrumPadBank
import com.bitwig.extension.controller.api.MidiOut
import com.bitwig.extension.controller.api.NoteInput
import com.bitwig.extension.controller.api.Transport
import fr.arthurvimond.bitwig.extension.*
import fr.arthurvimond.bitwig.state.PadsMode
import fr.arthurvimond.bitwig.state.State
import fr.arthurvimond.bitwig.state.StepsMode
import fr.arthurvimond.bitwig.util.Logger
import fr.arthurvimond.bitwig.util.Sparkle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class PadControlsManager(
    private val midiManager: MidiManager,
    private val stepsManager: StepsManager,
    private val state: State,
    private val noteInput: NoteInput,
    private val transport: Transport,
    private val midiOut: MidiOut,
    private val deviceManager: DeviceManager,
    private val drumPadBank: DrumPadBank,
    private val logger: Logger,
) : KoinComponent {

    private val scope = CoroutineScope(Dispatchers.Default)

    init {

        updateKeyTranslationTable()

        drumPadBank.scrollPosition().markInterested()
        drumPadBank.cursorIndex().markInterested()
        drumPadBank.channelCount().markInterested()

        // Set initial pad bank switcher state
        // NB: Hacky but works
        var scrollPositionSet = false
        drumPadBank.scrollPosition().addValueObserver { scrollPosition ->
            if (scrollPosition != 0 && !scrollPositionSet) {
                state.setPadBankSwitched(!drumPadBank.isFirstBank)
                scrollPositionSet = true
            }
        }

        scope.launch {
            midiManager.noteOn.collect {
                when (it.note) {
                    Sparkle.Note.Select -> state.setIsSelectPressed(true)
                    Sparkle.Note.PadBankSwitcher -> state.switchPadBank()
                    Sparkle.Note.Mute -> {
                        if (state.padsMode.value != PadsMode.Mute) {
                            state.setPadsMode(PadsMode.Mute)
                        } else {
                            state.setPadsMode(PadsMode.Play)
                            if (state.isSelectPressed.value) {
                                deviceManager.findDrumPadBank().clearMutedPads()
                            }
                            midiOut.turnOffPads()
                        }
                    }

                    Sparkle.Note.Solo -> {
                        if (state.padsMode.value != PadsMode.Solo) {
                            state.setPadsMode(PadsMode.Solo)
                        } else {
                            state.setPadsMode(PadsMode.Play)
                            if (state.isSelectPressed.value) {
                                deviceManager.findDrumPadBank().clearSoloedPads()
                            }
                            midiOut.turnOffPads()
                        }
                    }

                    Sparkle.Note.Step16 -> {
                        if (state.isSelectPressed.value) {
                            state.toggleVelocityOff()
                        }

                    }
                }
            }
        }

        scope.launch {
            midiManager.noteOff.collect {
                when (it.note) {
                    Sparkle.Note.Select -> state.setIsSelectPressed(false)
                }
            }
        }

        // Metronome / Velocity Off leds feedback
        scope.launch {
            state.isSelectPressed.collect {
                if (it) {
                    midiOut.sendNoteOn(Sparkle.Note.Select)

                    midiOut.turnOffSteps()

                    delay(2)
                    if (transport.isMetronomeEnabled.get()) {
                        midiOut.sendNoteOn(Sparkle.Note.Step14)
                    }
                    if (state.isVelocityOff.value) {
                        midiOut.sendNoteOn(Sparkle.Note.Step16)
                    }
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Select)

                    midiOut.sendNoteOff(Sparkle.Note.Step14)
                    midiOut.sendNoteOff(Sparkle.Note.Step16)

                    if (state.stepsMode.value == StepsMode.Sequencer) {
                        stepsManager.updateStepsLedsState()
                    }
                }
            }
        }

        scope.launch {
            state.padsMode.collect {
                if (it == PadsMode.Mute) {
                    midiOut.sendNoteOn(Sparkle.Note.Mute)
                    updateMutedPadsState()
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Mute)
                }

                if (it == PadsMode.Solo) {
                    midiOut.sendNoteOn(Sparkle.Note.Solo)
                    updateSoloedPadsState()
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Solo)
                }
            }
        }

        // Needed to temporarily disable input notes while select is pressed
        scope.launch {
            state.isSelectPressed.collect {
                updateKeyTranslationTable()
            }
        }

        // Velocity off
        scope.launch {
            state.isVelocityOff.collect {
                midiOut.sendNote(Sparkle.Note.Step16, isOn = it)
                updateVelocityTranslationTable()
            }
        }

        // Needed to temporarily disable input notes while PadsMode is not Play
        scope.launch {
            state.padsMode
                .map { it == PadsMode.Play }
                .distinctUntilChanged()
                .collect { updateKeyTranslationTable() }
        }

        // Pad bank switcher feedback
        scope.launch {
            state.isPadBankSwitched.collect { switched ->
                deviceManager.switchDrumPadBank(switched)
                updateKeyTranslationTable()
                midiOut.updatePadLedsState(state.selectedPad.value, state.isPadBankSwitched.value)
            }
        }

    }

    private fun updateMutedPadsState() {
        (0 until drumPadBank.sizeOfBank).forEach { i ->
            val drumPadBank = deviceManager.findDrumPadBank()
            val drumPadItem = drumPadBank.getItemAt(i)
            val mute = drumPadItem.mute().get()
            val note = i + 60
            if (mute) {
                midiOut.sendNoteOn(note)
            } else {
                midiOut.sendNoteOff(note)
            }
        }
    }

    private fun updateSoloedPadsState() {
        (0 until drumPadBank.sizeOfBank).forEach { i ->
            val drumPadBank = deviceManager.findDrumPadBank()
            val drumPadItem = drumPadBank.getItemAt(i)
            val solo = drumPadItem.solo().get()
            val note = i + 60
            if (solo) {
                midiOut.sendNoteOn(note)
            } else {
                midiOut.sendNoteOff(note)
            }
        }
    }

    private fun updateKeyTranslationTable() {
        val offset = if (state.isPadBankSwitched.value) 8 else 0
        val table = MutableList(128) { -1 }

        // Temporarily disable input notes while select is pressed or PadsMode is not Play
        if (!state.isSelectPressed.value &&
            state.padsMode.value == PadsMode.Play
        ) {
            (60 until 68).forEach { i ->
                table[i] = i - 24 + offset
            }
        }

        noteInput.setKeyTranslationTable(table.toTypedArray())
    }

    private fun updateVelocityTranslationTable() {
        val table = MutableList(128) { -1 }
        table.forEachIndexed { i, _ ->
            val velocity = if (state.isVelocityOff.value) 100 else i
            table[i] = velocity
        }
        noteInput.setVelocityTranslationTable(table.toTypedArray())
    }
}