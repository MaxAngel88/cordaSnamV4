package pojo

import net.corda.core.serialization.CordaSerializable
import java.time.Instant


@CordaSerializable
data class ProposalPojo(
        val counterpart: String = "",
        val data: Instant = Instant.now(),
        val energia: Double = 0.0,
        val pricePerUnit: Double = 0.0,
        val validity: Instant = data.plusSeconds(60 * 15),
        val type: Char = '-'
)