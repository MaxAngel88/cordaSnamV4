package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class BalancePojo(
        val totalSold: Double = 0.0,
        val totalBought: Double = 0.0,
        val delta: Double = totalSold - totalBought
)