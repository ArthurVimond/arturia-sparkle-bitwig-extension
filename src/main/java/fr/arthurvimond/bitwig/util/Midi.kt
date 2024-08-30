package fr.arthurvimond.bitwig.util

object Midi {

    object CC {
        const val Minus = 63
        const val Plus = 65
    }

    object Out {
        const val NoteOn = 0x90
        const val NoteOff = 0x80
        const val CC = 0xB0
    }

    object StatusByte {
        const val NoteOn = 144
        const val NoteOff = 128
        const val AfterTouch = 160
        const val CC = 176
    }
}