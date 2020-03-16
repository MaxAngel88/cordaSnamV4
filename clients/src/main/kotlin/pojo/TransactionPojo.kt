package pojo

import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class TransactionPojo(
        val buyer: String = "",
        val seller: String = "",
        val externalId: String = "",
        val data: Instant = Instant.now(),
        val energia: Double = 0.0,
        val totalPrice: Double = 0.0,
        val pricePerUnit: Double = 0.0,
        val idProposal: String = ""
)