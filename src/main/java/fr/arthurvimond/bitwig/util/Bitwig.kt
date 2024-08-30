package fr.arthurvimond.bitwig.util

import java.util.*

object Bitwig {

    object Instrument {
        val DrumMachine = UUID.fromString("8ea97e45-0255-40fd-bc7e-94419741e9d1")
        val InstrumentSelector = UUID.fromString("9588fbcf-721a-438b-8555-97e4231f7d2c")
        val Sampler = UUID.fromString("468bc14b-b2e7-45a1-9666-e83117fe404e")
        val EKick = UUID.fromString("c6d5de18-a6f1-4daa-90a9-d9254527601a")
        val ESnare = UUID.fromString("db22eb41-c8a0-4055-b617-637614dfa185")
        val EClap = UUID.fromString("89eba41d-46d3-4506-8ce6-ba9fe3e3bee4")
        val EHat = UUID.fromString("742e4a89-df78-4ca5-b6b0-ca78889d5953")
    }

    object AudioEffect {
        val Filter = UUID.fromString("4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42")
    }

    val NominalVolume = 0.7937


    // Sampler Speed semitone -> percentage -> double value converter
    //          0%       0.5
    // -8 semi  63       0.57875
    // -7 semi  66.74    0.583425
    // -6 semi  70.71    0.5883875
    // -5 semi  74.92    0.59365
    // -4 semi  79.37    0.5992125
    // -3 semi  84.09    0.6051125
    // -2 semi  89.09    0.6113625
    // -1 semi  94.39    0.6179875
    // 0 semi   100      0.625
    // 1 semi   105.95   0.6324375
    // 2 semi   112.25   0.6403125
    // 3 semi   118.92   0.64865
    // 4 semi   125.99   0.6574875
    // 5 semi   133.48   0.66685
    // 6 semi   141.42   0.676775
    // 7 semi   149.83   0.6872875

    //          400%     1.0
    val SamplerSpeedMap = mapOf(
        -8 to 0.57875,
        -7 to 0.583425,
        -6 to 0.5883875,
        -5 to 0.59365,
        -4 to 0.5992125,
        -3 to 0.6051125,
        -2 to 0.6113625,
        -1 to 0.6179875,
        0 to 0.625,
        1 to 0.6324375,
        2 to 0.6403125,
        3 to 0.64865,
        4 to 0.6574875,
        5 to 0.66685,
        6 to 0.676775,
        7 to 0.6872875,
    )
}