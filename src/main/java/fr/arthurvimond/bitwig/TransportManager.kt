package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.api.*
import fr.arthurvimond.bitwig.extension.sendNote
import fr.arthurvimond.bitwig.state.State
import fr.arthurvimond.bitwig.util.Logger
import fr.arthurvimond.bitwig.util.Midi
import fr.arthurvimond.bitwig.util.Sparkle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class TransportManager(
    private val hardwareSurface: HardwareSurface,
    private val midiManager: MidiManager,
    private val state: State,
    private val transport: Transport,
    private val groove: Groove,
    private val midiIn: MidiIn,
    private val midiOut: MidiOut,
    private val logger: Logger,
) {

    private val scope = CoroutineScope(Dispatchers.Default)

    init {

        // Play / Pause
        val playButton = hardwareSurface.createHardwareButton("PLAY_BUTTON")
        playButton.pressedAction().setActionMatcher(midiIn.createNoteOnActionMatcher(1, Sparkle.Note.PlayPause))
        playButton.pressedAction().setBinding(transport.playAction())

        transport.isMetronomeEnabled.markInterested()
        transport.isMetronomeEnabled.addValueObserver {
            if (state.isSelectPressed.value) {
                midiOut.sendNote(Sparkle.Note.Step14, isOn = it)
            }
        }

        // Play / Pause, Stop, Rec, Metronome
        scope.launch {
            midiManager.noteOn.collect {
                when (it.note) {
                    Sparkle.Note.PlayPause -> {
                        transport.continuePlayback()
                    }

                    Sparkle.Note.Stop -> {
                        transport.stop()
                    }

                    Sparkle.Note.Rec -> {
                        transport.isClipLauncherOverdubEnabled.toggle()
                    }

                    Sparkle.Note.Step14 -> {
                        if (state.isSelectPressed.value) {
                            transport.isMetronomeEnabled.toggle()
                        }
                    }
                }
            }
        }

        // Tempo / Groove shuffle knob
        scope.launch {
            midiManager.cc.collect { cc ->
                when (cc.cc) {
                    Sparkle.CC.Tempo -> {
                        if (state.isSelectPressed.value) {
                            // Groove
                            groove.shuffleAmount.inc(cc.value - 64, 64)
                        } else {
                            // Tempo
                            val tempoValue = when (cc.value) {
                                Midi.CC.Minus -> -0.1
                                else -> 0.1
                            }
                            transport.tempo().incRaw(tempoValue)
                        }
                    }
                }
            }
        }

        transport.isPlaying.markInterested()
        transport.isPlaying.addValueObserver { isPlaying ->
            if (isPlaying) {
                midiOut.sendMidi(Midi.Out.NoteOn, Sparkle.Note.PlayPause, 127)
            } else {
                midiOut.sendMidi(Midi.Out.NoteOff, Sparkle.Note.PlayPause, 0)
            }
        }

        transport.isClipLauncherOverdubEnabled.markInterested()
        transport.isClipLauncherOverdubEnabled.addValueObserver { isOverdubEnabled ->
            if (isOverdubEnabled) {
                midiOut.sendMidi(Midi.Out.NoteOn, Sparkle.Note.Rec, 127)
            } else {
                midiOut.sendMidi(Midi.Out.NoteOff, Sparkle.Note.Rec, 0)
            }
        }

    }
}