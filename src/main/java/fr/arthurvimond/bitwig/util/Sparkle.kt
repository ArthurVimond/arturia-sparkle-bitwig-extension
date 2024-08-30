package fr.arthurvimond.bitwig.util

object Sparkle {

    object Note {
        val Steps = 0..15
        val Step1 = 0
        val Step2 = 1
        val Step3 = 2
        val Step4 = 3
        val Step5 = 4
        val Step6 = 5
        val Step7 = 6
        val Step8 = 7
        val Step9 = 8
        val Step10 = 9
        val Step11 = 10
        val Step12 = 11
        val Step13 = 12
        val Step14 = 13
        val Step15 = 14
        val Step16 = 15
        const val Bank = 16
        const val Pattern = 17
        const val Sequencer = 18
        const val Tune = 19
        const val Select = 20
        const val PadBankSwitcher = 21
        const val Mute = 22
        const val Solo = 23

        const val Rec = 24
        const val Stop = 25
        const val PlayPause = 26

        const val LoopOn = 27
        const val Copy = 28
        const val Erase = 29
        const val GlobalFilter = 30
        const val Slicer = 31
        const val Roller = 32
        const val InstrumentFilter = 33
        const val Send1_2 = 34
        const val Pan_Level = 35

        const val PatternLengthLeft = 36
        const val PatternLengthRight = 37

        const val BrowserInstrument = 38
        const val BrowserKit = 39
        const val BrowserProject = 40

        val PatternLeds = 41..44
        const val PatternLed1 = 41
        const val PatternLed2 = 42
        const val PatternLed3 = 43
        const val PatternLed4 = 44

        const val LoopMovePush = 55
        const val BrowserDialPush = 56
        const val PadX_YTouch = 57

        val Pads = 60..67
        val Pad1 = 60
        val Pad2 = 61
        val Pad3 = 62
        val Pad4 = 63
        val Pad5 = 64
        val Pad6 = 65
        val Pad7 = 66
        val Pad8 = 67
        val PadLeds = 68..75
        val PadLed1 = 68
        val PadLed2 = 69
        val PadLed3 = 70
        val PadLed4 = 71
        val PadLed5 = 72
        val PadLed6 = 73
        val PadLed7 = 74
        val PadLed8 = 75
    }

    object CC {
        const val Volume = 47
        const val Tempo = 48

        const val Param1 = 49
        const val Param2 = 50
        const val Param3 = 51

        const val LoopDivide = 52
        const val LoopMove = 53

        const val BrowserDial = 54

        const val PadY = 58
        const val PadX = 59
    }
}