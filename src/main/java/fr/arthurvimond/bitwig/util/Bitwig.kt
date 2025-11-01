package fr.arthurvimond.bitwig.util

import java.util.*

object Bitwig {

    object Instrument {
        val DrumMachine = UUID.fromString("8ea97e45-0255-40fd-bc7e-94419741e9d1")
        val InstrumentSelector = UUID.fromString("9588fbcf-721a-438b-8555-97e4231f7d2c")
        val Sampler = UUID.fromString("468bc14b-b2e7-45a1-9666-e83117fe404e")

        // V0 drums
        val V0Kick = UUID.fromString("8415c7af-1379-4730-97bf-16380f96d0fe")
        val V0ZapKick = UUID.fromString("eef2e851-925b-4e86-81c6-67463e17c5f7")
        val V0Snare = UUID.fromString("446c58e3-ee39-4a22-b1e1-a62c614f98d4")
        val V0Hat = UUID.fromString("212c6aa0-04b6-49b7-a77f-c1fcee5d33a1")
        val V0Cymbal = UUID.fromString("7b21c41e-67c3-4dd0-aa64-4fbb03d95cdb")
        val V0Tom = UUID.fromString("3c6105ad-176e-4403-993b-3eedefdf6dda")

        // V1 drums (legacy E drums)
        val V1Kick = UUID.fromString("c6d5de18-a6f1-4daa-90a9-d9254527601a")
        val V1Snare = UUID.fromString("db22eb41-c8a0-4055-b617-637614dfa185")
        val V1Clap = UUID.fromString("89eba41d-46d3-4506-8ce6-ba9fe3e3bee4")
        val V1Hat = UUID.fromString("742e4a89-df78-4ca5-b6b0-ca78889d5953")
        val V1Tom = UUID.fromString("b5c7c298-e6af-42b3-8f14-26e25bb72d48")
        val V1Cowbell = UUID.fromString("dd594db1-a908-453f-a1b9-0a1b6c4c3b32")

        // V8 drums
        val V8Kick = UUID.fromString("10fba33b-8e65-4eea-a5cf-312986178240")
        val V8Snare = UUID.fromString("97938f59-c3d2-4b2c-8640-21c2fd2cc516")
        val V8Clap = UUID.fromString("b13d3937-6002-4e88-8e50-e99119708072")
        val V8Hat = UUID.fromString("85d9c654-088f-4a8a-bbfc-e98af9eafb7b")
        val V8Maracas = UUID.fromString("03ec3a24-b3c9-4ba4-b6dc-855178d60de7")
        val V8Cymbal = UUID.fromString("0af9f363-ff81-4e72-b2b2-31c8c9682e28")
        val V8Tom = UUID.fromString("e1be73d9-ba43-4011-91b7-2178bc4af5ea")
        val V8Claves = UUID.fromString("1b709991-7d7d-45d1-aec8-847c01611bfb")
        val V8Cowbell = UUID.fromString("f3e8fa57-dd7a-4d94-91dd-1376c1c8304a")

        // V9 drums
        val V9Kick = UUID.fromString("32a4c607-039a-4998-be9c-578468f25454")
        val V9Snare = UUID.fromString("90600c24-04c5-412e-b978-6d3cef1522da")
        val V9Clap = UUID.fromString("3df67ed2-4d70-4a86-a966-14762e2aeea4")
        val V9Tom = UUID.fromString("60f69854-fda1-4538-9ff1-c1553ea25224")
        val V9HatClosed = UUID.fromString("5c147bc8-7b62-408b-b057-c4023c4e1adb")
        val V9HatOpen = UUID.fromString("94fc934e-a4ba-44f1-aaaa-ae30920fab17")
        val V9Ride = UUID.fromString("38f52e07-2339-491e-9dd4-8bf6a95c2dae")
        val V9Crash = UUID.fromString("84bd7819-2007-46e0-b930-b4dacff1974a")
        val V9Rimshot = UUID.fromString("f88c7dda-c8cd-456f-8bdf-ac25fa5bfea1") // No decay param
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