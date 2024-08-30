package fr.arthurvimond.bitwig.extension

import com.bitwig.extension.controller.api.MidiOut
import fr.arthurvimond.bitwig.util.Midi
import fr.arthurvimond.bitwig.util.Sparkle

fun MidiOut.sendNoteOn(data1: Int, data2: Int = 127) {
    sendMidi(Midi.Out.NoteOn, data1, data2)
}

fun MidiOut.sendNoteOff(data1: Int) {
    sendMidi(Midi.Out.NoteOff, data1, 0)
}

fun MidiOut.sendNote(data1: Int, isOn: Boolean) {
    if (isOn) {
        sendNoteOn(data1)
    } else {
        sendNoteOff(data1)
    }
}

fun MidiOut.updatePadLedsState(selectedPad: Int, isPadBankSwitched: Boolean) {
    Sparkle.Note.PadLeds.forEach { ledNote ->
        val ledOffset = if (isPadBankSwitched) 8 else 0
        val ledIndex = ledNote - 68 + ledOffset
        sendNote(ledNote, isOn = ledIndex == selectedPad)
    }
}

fun MidiOut.turnOffSteps() {
    Sparkle.Note.Steps.forEach { sendNoteOff(it) }
}

fun MidiOut.turnOffPads() {
    Sparkle.Note.Pads.forEach { sendNoteOff(it) }
}

fun MidiOut.turnOffAllLeds() {
    sendNoteOff(Sparkle.Note.Pan_Level)
    sendNoteOff(Sparkle.Note.Sequencer)
    sendNoteOff(Sparkle.Note.PatternLed1)
    (0..75).forEach { sendNoteOff(it) }
}