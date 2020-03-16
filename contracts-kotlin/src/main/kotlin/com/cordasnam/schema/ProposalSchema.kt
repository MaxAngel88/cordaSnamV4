package com.cordasnam.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for ProposalSchema.
 */
object ProposalSchema

/**
 * An ProposalState schema.
 */
object ProposalSchemaV1 : MappedSchema(
        schemaFamily = ProposalSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentProposal::class.java)) {

    @Entity
    @Table(name = "proposal_states")
    class PersistentProposal(

            @Column(name = "issuer")
            var issuer: String,

            @Column(name = "counterpart")
            var counterpart: String,

            @Column(name = "snam")
            var snamName: String,

            @Column(name = "data")
            var data: Instant,

            @Column(name = "energia")
            var energia: Double,

            @Column(name = "pricePerUnit")
            var pricePerUnit: Double,

            @Column(name = "validity")
            var validity: Instant,

            @Column(name = "type")
            var type: Char,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor() : this("","","",  Instant.now(), 0.0 , 0.0, Instant.now(), '-', UUID.randomUUID())
    }
}