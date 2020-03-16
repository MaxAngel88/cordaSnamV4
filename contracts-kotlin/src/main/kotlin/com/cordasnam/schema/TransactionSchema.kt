package com.cordasnam.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for TransactionSchema.
 */
object TransactionSchema

/**
 * An TransactionState schema.
 */
object TransactionSchemaV1 : MappedSchema(
        schemaFamily = TransactionSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTransaction::class.java)) {

    @Entity
    @Table(name = "transaction_states")
    class PersistentTransaction(

            @Column(name = "buyer")
            var buyerName: String,

            @Column(name = "seller")
            var sellerName: String,

            @Column(name = "snam")
            var snamName: String,

            @Column(name = "data")
            var data: Instant,

            @Column(name = "energia")
            var energia: Double,

            @Column(name = "pricePerUnit")
            var pricePerUnit: Double,

            @Column(name = "totalPrice")
            var totalPrice: Double,

            @Column(name = "idProposal")
            var idProposal: String,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor() : this("","","",   Instant.now(), 0.0 , 0.0, 0.0,  "", UUID.randomUUID())
    }
}