package com.meteocool.location

data class MeteocoolLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val elapsedNanosSinceBoot: Long
) : Comparable<MeteocoolLocation?> {
    override fun compareTo(other: MeteocoolLocation?): Int {
        if(other == null){
            return 1
        }
        return (this.elapsedNanosSinceBoot - other.elapsedNanosSinceBoot).toInt()
    }
}
