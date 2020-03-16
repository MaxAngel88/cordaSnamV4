package com.cordasnam.state

import com.cordasnam.contract.TransactionContract
import com.cordasnam.schema.TransactionSchemaV1
import net.corda.core.contracts.*
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.time.Instant

@BelongsToContract(TransactionContract::class)
data class TransactionState(
        val buyer: Party,
        val seller: Party,
        val snam: Party,
        val data: Instant,
        val energia: Double,
        val pricePerUnit: Double,
        val totalPrice: Double,
        val idProposal: String,
        override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(buyer, seller, snam)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TransactionSchemaV1 -> TransactionSchemaV1.PersistentTransaction(
                    this.buyer.name.toString(),
                    this.seller.name.toString(),
                    this.snam.name.toString(),
                    this.data,
                    this.energia,
                    this.pricePerUnit,
                    this.totalPrice,
                    this.idProposal,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TransactionSchemaV1)
}
