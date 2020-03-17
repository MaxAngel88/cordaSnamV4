package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class IssueTransactionPojo(
        val id : String = ""
)