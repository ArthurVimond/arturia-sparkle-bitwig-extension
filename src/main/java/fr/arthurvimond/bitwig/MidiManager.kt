package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.api.MidiIn
import fr.arthurvimond.bitwig.model.MidiCC
import fr.arthurvimond.bitwig.model.MidiNote
import fr.arthurvimond.bitwig.util.Logger
import fr.arthurvimond.bitwig.util.Midi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MidiManager(
    private val midiIn: MidiIn,
    private val logger: Logger,
) {

    private val scope = CoroutineScope(Dispatchers.Default)

    val noteOn: SharedFlow<MidiNote>
        get() = _noteOn.asSharedFlow()
    private val _noteOn: MutableSharedFlow<MidiNote> = MutableSharedFlow()

    val noteOff: SharedFlow<MidiNote>
        get() = _noteOff.asSharedFlow()
    private val _noteOff: MutableSharedFlow<MidiNote> = MutableSharedFlow()


    private val _cc: MutableSharedFlow<MidiCC> = MutableSharedFlow()
    val cc: SharedFlow<MidiCC>
        get() = _cc.asSharedFlow()

    init {

        midiIn.setMidiCallback { statusByte, data1, data2 ->
            // Debug log
//            if (statusByte != Midi.StatusByte.AfterTouch) {
//                logger.d("midi in received - statusByte: $statusByte - data1: $data1 - data2: $data2")
//            }
            when (statusByte) {
                Midi.StatusByte.NoteOn -> {
                    val midiNote = MidiNote(note = data1, velocity = data2)
                    scope.launch { _noteOn.emit(midiNote) }
                }

                Midi.StatusByte.NoteOff -> {
                    val midiNote = MidiNote(note = data1, velocity = 0)
                    scope.launch { _noteOff.emit(midiNote) }
                }

                Midi.StatusByte.CC -> {
                    val midiCC = MidiCC(cc = data1, value = data2)
                    scope.launch { _cc.emit(midiCC) }
                }
            }
        }

    }
}