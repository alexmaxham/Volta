package com.alexmaxham.volta.core

data class AlertOptions(
    val lowEnabled: Boolean = false,
    val lowLevel: Int = 20,
    val chargeEnabled: Boolean = false,
    val chargeLevel: Int = 80,
    val warmEnabled: Boolean = false,
    val warmC: Float = 40f,
)

/** Which alerts have fired and not yet re-armed, so each fires once per crossing. */
data class AlertLatch(val low: Boolean = false, val charge: Boolean = false, val warm: Boolean = false)

enum class Alert { LOW, CHARGE, WARM }

object Alerts {
    /** Alerts to post now and the updated latch. */
    fun check(r: Reading, o: AlertOptions, latch: AlertLatch): Pair<List<Alert>, AlertLatch> {
        val fire = mutableListOf<Alert>()
        var low = latch.low
        var charge = latch.charge
        var warm = latch.warm

        if (r.plugged || r.level > o.lowLevel + 2) low = false
        else if (o.lowEnabled && !low && r.level <= o.lowLevel) { fire += Alert.LOW; low = true }

        if (!r.plugged || r.level < o.chargeLevel) charge = false
        else if (o.chargeEnabled && !charge && o.chargeLevel < 100 && r.level >= o.chargeLevel) { fire += Alert.CHARGE; charge = true }

        val t = r.tempC
        if (t == null || t < o.warmC - 2f) warm = false
        else if (o.warmEnabled && !warm && t >= o.warmC) { fire += Alert.WARM; warm = true }

        return fire to AlertLatch(low, charge, warm)
    }
}
