package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.api.CursorTrack
import com.bitwig.extension.controller.api.Device
import com.bitwig.extension.controller.api.DrumPadBank
import com.bitwig.extension.controller.api.MidiOut
import fr.arthurvimond.bitwig.di.KoinQualifiers
import fr.arthurvimond.bitwig.extension.sendNoteOff
import fr.arthurvimond.bitwig.extension.sendNoteOn
import fr.arthurvimond.bitwig.extension.updatePadLedsState
import fr.arthurvimond.bitwig.state.PadsMode
import fr.arthurvimond.bitwig.state.State
import fr.arthurvimond.bitwig.util.Logger
import fr.arthurvimond.bitwig.util.Midi
import fr.arthurvimond.bitwig.util.Sparkle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class PadsManager(
    private val midiManager: MidiManager,
    private val deviceManager: DeviceManager,
    private val state: State,
    private val midiOut: MidiOut,
    private val cursorTrack: CursorTrack,
    private val drumPadBank: DrumPadBank,
    private val logger: Logger,
) : KoinComponent {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val drumMachineDevice: Device by inject(named(KoinQualifiers.DrumMachineDevice))

    init {

        setupDrumPadBankObservers(drumPadBank)
        setupDrumPadBankObservers(deviceManager.nestedDrumPadBank)

        // Pads input
        scope.launch {
            midiManager.noteOn.collect {
                if (it.note in Sparkle.Note.Pads) {
                    when (state.padsMode.value) {
                        PadsMode.Play -> {
                            if (state.isSelectPressed.value) return@collect
                            midiOut.sendMidi(Midi.Out.NoteOn, it.note, it.velocity)
                        }

                        PadsMode.Mute -> {
                            val index = it.note - 60
                            val drumPadBank = deviceManager.findDrumPadBank()
                            val drumPadItem = drumPadBank.getItemAt(index)
                            drumPadItem.mute().toggle()
                        }

                        PadsMode.Solo -> {
                            val index = it.note - 60
                            val drumPadBank = deviceManager.findDrumPadBank()
                            val drumPadItem = drumPadBank.getItemAt(index)
                            drumPadItem.solo().toggle()
                        }
                    }
                }
            }
        }

        scope.launch {
            midiManager.noteOff.collect {
                if (it.note in Sparkle.Note.Pads) {
                    if (state.padsMode.value == PadsMode.Play) {
                        midiOut.sendMidi(Midi.Out.NoteOff, it.note, it.velocity)
                    }
                }
            }
        }

        // Pads selection
        scope.launch {
            midiManager.noteOn.collect {
                if (it.note in Sparkle.Note.Pads) {
                    if (state.isSelectPressed.value) {
                        val offset = if (state.isPadBankSwitched.value) 8 else 0
                        val padIndex = it.note - 60 + offset
                        state.selectPad(padIndex)
                    }
                }
            }
        }

        // Selected Pad Led feedback
        scope.launch {
            state.selectedPad.collect { selectedPad ->
                midiOut.updatePadLedsState(selectedPad, state.isPadBankSwitched.value)
            }
        }

        // Playing notes Pad leds feedback
        cursorTrack.addNoteObserver { isNoteOn, key, _ ->
            if (state.padsMode.value != PadsMode.Play) return@addNoteObserver
            val isPadBankSwitched = state.isPadBankSwitched.value
            val offset = if (isPadBankSwitched) 8 else 0
            val range = 36..43 + offset
            if (key !in range) return@addNoteObserver
            val padNote = key + 24 - offset
            if (isNoteOn) {
                midiOut.sendNoteOn(padNote)
            } else {
                midiOut.sendNoteOff(padNote)
            }
        }
    }

    private fun setupDrumPadBankObservers(drumPadBank: DrumPadBank) {
        (0 until drumPadBank.sizeOfBank).forEach { i ->
            val drumPadItem = drumPadBank.getItemAt(i)

            // Mute observers
            drumPadItem.mute().addValueObserver { mute ->
                if (state.padsMode.value != PadsMode.Mute) return@addValueObserver
                val note = i + 60
                if (mute) {
                    midiOut.sendNoteOn(note)
                } else {
                    midiOut.sendNoteOff(note)
                }
            }

            // Solo observers
            drumPadItem.solo().addValueObserver { solo ->
                if (state.padsMode.value != PadsMode.Solo) return@addValueObserver
                val note = i + 60
                if (solo) {
                    midiOut.sendNoteOn(note)
                } else {
                    midiOut.sendNoteOff(note)
                }
            }
        }
    }
}