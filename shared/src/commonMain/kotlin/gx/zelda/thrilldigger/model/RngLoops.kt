package gx.zelda.thrilldigger.model

/**
 * The loops Skyward Sword's RNG settles into ([SsRng]), largest basin first. Found by running the
 * RNG from random starts until each landed on a loop, which also measured each loop's basin
 * (docs/ss.md); loops no start reached are left out. `ThrillDiggerRngTest` checks each is a loop of
 * its length with its anchor as smallest state.
 */
object RngLoops {
    /** Shares are of 20,000 random starts (a 1-in-20,000 loop of 8 states is left out). */
    val all: List<RngLoop> = listOf(
        RngLoop(5862L, 1708724, 15364 / 20000.0),
        RngLoop(8002L, 127646, 1500 / 20000.0),
        RngLoop(4172L, 155834, 1447 / 20000.0),
        RngLoop(34267L, 354835, 675 / 20000.0),
        RngLoop(56857L, 146318, 509 / 20000.0),
        RngLoop(409493L, 48534, 261 / 20000.0),
        RngLoop(61847L, 81673, 240 / 20000.0),
        RngLoop(82622L, 26128, 3 / 20000.0),
    )
}
