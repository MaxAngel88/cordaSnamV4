package com.cordasnam.state

import com.cordasnam.contract.ProposalContract
import com.cordasnam.flow.ProposalFlow
import com.cordasnam.schema.ProposalSchemaV1
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant

@BelongsToContract(ProposalContract::class)
data class ProposalState(
        val issuer: Party,
        val counterpart: Party,
        val snam: Party,
        val data: Instant,
        val energia: Double,
        val pricePerUnit: Double,
        val validity: Instant,
        val type: Char,
        override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState, QueryableState, SchedulableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(issuer, counterpart, snam)

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        return ScheduledActivity(flowLogicRefFactory.create(ProposalFlow.EndProposal::class.java, thisStateRef), validity)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is ProposalSchemaV1 -> ProposalSchemaV1.PersistentProposal(
                    this.issuer.name.toString(),
                    this.counterpart.name.toString(),
                    this.snam.name.toString(),
                    this.data,
                    this.energia,
                    this.pricePerUnit,
                    this.validity,
                    this.type,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ProposalSchemaV1)

}