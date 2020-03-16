package com.cordasnam.flow

import co.paralleluniverse.fibers.Suspendable
import com.cordasnam.contract.TransactionContract
import com.cordasnam.contract.TransactionContract.Companion.TRANSACTION_CONTRACT_ID
import com.cordasnam.state.ProposalState
import com.cordasnam.state.TransactionState
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.*
import pojo.TransactionPojo


object TransactionFlow {
    @InitiatingFlow
    @StartableByRPC
    class Starter(
            val buyer: Party,
            val seller: Party,
            val snam: Party,
            val properties: TransactionPojo) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val transactionState = TransactionState(
                    buyer,
                    seller,
                    snam,
                    properties.data,
                    properties.energia,
                    properties.pricePerUnit,
                    properties.energia * properties.pricePerUnit,
                    properties.idProposal,
                    UniqueIdentifier(properties.externalId, UUID.randomUUID()))

            val txCommand = Command(TransactionContract.Commands.Create(), transactionState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(transactionState, TRANSACTION_CONTRACT_ID)
                    .addCommand(txCommand)


            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS

            var firstFlow : FlowSession? = null
            var secondFlow : FlowSession? = null

            // Send the state to the counterparty, and receive it back with their signature.
            when(serviceHub.myInfo.legalIdentities.first()){
                seller -> {
                    firstFlow = initiateFlow(snam)
                    secondFlow = initiateFlow(buyer)
                }
                buyer -> {
                    firstFlow = initiateFlow(snam)
                    secondFlow = initiateFlow(seller)
                }
                snam -> {
                    firstFlow = initiateFlow(seller)
                    secondFlow = initiateFlow(buyer)
                }

                else -> throw FlowException("node "+serviceHub.myInfo.legalIdentities.first()+" not partecipating to the transaction")
            }


            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(firstFlow!!, secondFlow!!), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, setOf(firstFlow!!, secondFlow!!), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Starter::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a transaction." using (output is TransactionState)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartyFlow, expectedTxId = txId))

        }
    }


    @InitiatingFlow
    @StartableByRPC
    class Issuer(
            val proposalId: String
    ) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]


            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val customCriteria = QueryCriteria.LinearStateQueryCriteria( uuid = listOf(UUID.fromString(proposalId)))
            criteria = criteria.and(customCriteria)

            val proposalStates = serviceHub.vaultService.queryBy<ProposalState>(
                    criteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if(proposalStates.size > 1 || proposalStates.isEmpty()) throw FlowException("no proposal state with UUID "+UUID.fromString(proposalId)+" found")

            val proposalStateRef = proposalStates[0]
            val proposalState = proposalStateRef.state.data

            if(proposalState.type == 'A'){
                if(!ProposalFlow.checkBalance(serviceHub.myInfo.legalIdentities.first().name.organisation, proposalState.energia)){
                    throw FlowException("not enough MWH balance")
                }
            }

            var buyer: Party? = null
            var seller: Party? = null

            if(proposalState.type == 'A') {
                buyer = proposalState.issuer
                seller = proposalState.counterpart

            }else if(proposalState.type == 'V') {
                buyer = proposalState.counterpart
                seller = proposalState.issuer
            }

            val idProposal = proposalState.linearId.id.toString()


            val transactionState = TransactionState(
                    buyer!!,
                    seller!!,
                    proposalState.snam,
                    proposalState.data,
                    proposalState.energia,
                    proposalState.pricePerUnit,
                    proposalState.energia * proposalState.pricePerUnit,
                    idProposal,
                    UniqueIdentifier(id = UUID.randomUUID()))


            val proposalCommand = Command(TransactionContract.Commands.Issue(), transactionState.participants.map { it.owningKey })
            val proposalBuilder = TransactionBuilder(notary)
                    .addInputState(proposalStateRef)
                    .addOutputState(transactionState, TRANSACTION_CONTRACT_ID)
                    .addCommand(proposalCommand)


            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            proposalBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(proposalBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS

            var firstFlow : FlowSession? = null
            var secondFlow : FlowSession? = null

            // Send the state to the counterparty, and receive it back with their signature.
            when(serviceHub.myInfo.legalIdentities.first()){

                proposalState.counterpart -> {
                    firstFlow = initiateFlow(proposalState.issuer)
                    secondFlow = initiateFlow(proposalState.snam)
                }

                else -> throw FlowException("node "+serviceHub.myInfo.legalIdentities.first()+" cannot start the flow")
            }


            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(firstFlow!!, secondFlow!!), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.

            if(proposalState.type == 'A'){
                ProposalFlow.updateBalance(serviceHub.myInfo.legalIdentities.first().name.organisation, -proposalState.energia)
                ProposalFlow.updateBalance(proposalState.issuer.name.organisation, proposalState.energia)

            } else if (proposalState.type == 'V'){
                ProposalFlow.updateBalance(serviceHub.myInfo.legalIdentities.first().name.organisation, proposalState.energia)
            }

            return subFlow(FinalityFlow(fullySignedTx, setOf(firstFlow!!, secondFlow!!), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Issuer::class)
    class IssuerAcceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    //val output = stx.tx.outputs.single().data
                    //"This must be a transaction." using (output is TransactionState)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartyFlow, expectedTxId = txId))
        }
    }
}
