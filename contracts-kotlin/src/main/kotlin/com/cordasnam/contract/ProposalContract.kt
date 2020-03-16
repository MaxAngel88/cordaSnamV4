package com.cordasnam.contract

import com.cordasnam.state.ProposalState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class ProposalContract : Contract {
    companion object {
        @JvmStatic
        val PROPOSAL_CONTRACT_ID = "com.cordasnam.contract.ProposalContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()
        for(command in commands) {
            val setOfSigners = command.signers.toSet()
            when (command.value) {
                is Commands.Create -> verifyCreate(tx, setOfSigners)
                is Commands.End -> verifyEnd(tx, setOfSigners)
                else -> throw IllegalArgumentException("Unrecognised command.")
            }
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

        "No inputs should be consumed when creating a transaction." using (tx.inputStates.isEmpty())

        "Only one transaction state should be created." using (tx.outputStates.size == 1)
        val proposal = tx.outputsOfType<ProposalState>().single()

        "issuer and counterpart cannot be the same" using (proposal.issuer != proposal.counterpart)
        "energy must be grather than 0" using (proposal.energia > 0)
        "pricePerUnit must be grather than 0" using (proposal.pricePerUnit > 0)
        "validity must be grather than date" using (proposal.validity > proposal.data)
        "proposal type must be 'A' or 'V'" using (proposal.type == 'V' || proposal.type == 'A')
        "All of the participants must be signers." using (signers.containsAll(proposal.participants.map { it.owningKey }))
    }

    private fun verifyEnd(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "one input when ending a proposal." using (tx.inputStates.size == 1)
        "no proposal state should be created." using (tx.outputStates.isEmpty())
        val proposal = tx.inputsOfType<ProposalState>().single()
        "All of the participants must be signers." using (signers.containsAll(proposal.participants.map { it.owningKey }))
    }

    interface Commands : CommandData {
        class Create : Commands, TypeOnlyCommandData()
        class End : Commands, TypeOnlyCommandData()
    }
}