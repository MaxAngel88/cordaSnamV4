package com.cordasnam.server

import com.cordasnam.flow.ProposalFlow
import com.cordasnam.flow.TransactionFlow
import com.cordasnam.schema.ProposalSchemaV1
import com.cordasnam.schema.TransactionSchemaV1
import com.cordasnam.state.ProposalState
import com.cordasnam.state.TransactionState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import pojo.*
import java.text.SimpleDateFormat
import java.util.*
import javax.servlet.http.HttpServletRequest

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */

@RestController
@RequestMapping("/api/") // The paths for GET and POST requests are relative to this base path.
class MainController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    /**
     * Returns the node's name.
     */
    @GetMapping(value = [ "me" ], produces = [ APPLICATION_JSON_VALUE ])
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the network map service. These names can be used to look up identities using
     * the identity service.
     */
    @GetMapping(value = [ "peers" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     *
     * Proposal API ************************************************
     *
     */

    /**
     * Displays all proposal on Party Vault.
     */
    @GetMapping(value = [ "proposal/get" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getAllProposalsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                @DefaultValue("") @QueryParam("id") idProposal: String,
                                @DefaultValue("") @QueryParam("counterpart") counterpart: String,
                                @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                @DefaultValue("unconsumed") @QueryParam("status") status: String): Response {

        try {
            var myPage = page

            if (myPage < 1){
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when(status){
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            val results = builder {
                var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)

                if(idProposal.isNotEmpty()){
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria( uuid = listOf(UUID.fromString(idProposal)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(counterpart.isNotEmpty()){
                    val idEqual = ProposalSchemaV1.PersistentProposal::counterpart.equal(counterpart)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(from.isNotEmpty() && to.isNotEmpty()){
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    var myFrom = format.parse(from)
                    var myTo = format.parse(to)
                    var dateBetween = ProposalSchemaV1.PersistentProposal::data.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = proxy.vaultQueryBy<ProposalState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }
        }catch (ex: Exception){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     * Displays my Proposal.
     */
    @GetMapping(value = [ "proposal/get/myProposals" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getMyProposalsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                               @DefaultValue("") @QueryParam("id") idProposal: String,
                               @DefaultValue("") @QueryParam("counterpart") counterpart: String,
                               @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                               @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                               @DefaultValue("unconsumed") @QueryParam("status") status: String): Response {

        try{
            var myPage = page

            if (myPage < 1){
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when(status){
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            val results = builder {

                var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)

                val issuerEqual = ProposalSchemaV1.PersistentProposal::issuer.equal(myLegalName.toString())
                val firstCriteria = QueryCriteria.VaultCustomQueryCriteria(issuerEqual, myStatus)
                criteria = criteria.and(firstCriteria)

                if(idProposal.isNotEmpty()){
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria( uuid = listOf(UUID.fromString(idProposal)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(counterpart.isNotEmpty()){
                    val idEqual = ProposalSchemaV1.PersistentProposal::counterpart.equal(counterpart)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(from.isNotEmpty() && to.isNotEmpty()){
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    var myFrom = format.parse(from)
                    var myTo = format.parse(to)
                    var dateBetween = ProposalSchemaV1.PersistentProposal::data.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = proxy.vaultQueryBy<ProposalState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }
        }catch (ex: Exception){

            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)

            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     * Displays my received Proposal.
     */
    @GetMapping(value = [ "proposal/get/receivedProposals" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getReceivedProposalsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                     @DefaultValue("") @QueryParam("id") idProposal: String,
                                     @DefaultValue("") @QueryParam("issuer") issuer: String,
                                     @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                     @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                     @DefaultValue("unconsumed") @QueryParam("status") status: String): Response {

        try {
            var myPage = page

            if (myPage < 1){
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when(status){
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }


            val results = builder {

                var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)

                val counterpartEqual = ProposalSchemaV1.PersistentProposal::counterpart.equal(myLegalName.toString())
                val firstCriteria = QueryCriteria.VaultCustomQueryCriteria(counterpartEqual, myStatus)
                criteria = criteria.and(firstCriteria)

                if(idProposal.isNotEmpty()){
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria( uuid = listOf(UUID.fromString(idProposal)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(issuer.isNotEmpty()){
                    val idEqual = ProposalSchemaV1.PersistentProposal::issuer.equal(issuer)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(from.isNotEmpty() && to.isNotEmpty()){
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    var myFrom = format.parse(from)
                    var myTo = format.parse(to)
                    var dateBetween = ProposalSchemaV1.PersistentProposal::data.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = proxy.vaultQueryBy<ProposalState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }
        }catch (ex: Exception){

            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)

            return Response.status(BAD_REQUEST).entity(resp).build()
        }

    }

    /**
     * Create a Proposal
     */
    @PostMapping(value = ["proposal/insert"], consumes = [ APPLICATION_JSON_VALUE ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun createProposal(
            @RequestBody
            proposalPojo : ProposalPojo): Response {

        try {
            val counterpart : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(proposalPojo.counterpart))!!
            val snam : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Sman,L=Milan,C=IT"))!!

            val signedTx = proxy.startTrackedFlow(ProposalFlow::Starter, counterpart, snam, proposalPojo).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "transaction $signedTx committed to ledger.")

            return Response.status(CREATED).entity(resp).build()

        } catch (ex: Exception) {

            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)

            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     * Issue a Transaction
     */
    @PostMapping(value = ["proposal/issueTransaction"], consumes = [ APPLICATION_JSON_VALUE ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueTransaction(
            @RequestBody
            issueTransactionPojo : IssueTransactionPojo): Response {

        try {

            val signedTx = proxy.startTrackedFlow(TransactionFlow::Issuer, issueTransactionPojo.id).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "transaction $signedTx committed to ledger.")

            return Response.status(CREATED).entity(resp).build()

        } catch (ex: Exception) {

            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)

            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     *
     * Transaction API ************************************************
     *
     */

    /**
     * Displays all transaction on Party Vault.
     */
    @GetMapping(value = [ "transaction/get" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getTransactionsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                @DefaultValue("") @QueryParam("id") idTransaction: String,
                                @DefaultValue("") @QueryParam("buyer") buyer: String,
                                @DefaultValue("") @QueryParam("seller") seller: String,
                                @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                @DefaultValue("unconsumed") @QueryParam("status") status: String): Response {


        try {
            var myPage = page

            if (myPage < 1) {
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when (status) {
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            var criteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)
            val results = builder {


                if (idTransaction.isNotEmpty()) {
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(idTransaction)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (seller.isNotEmpty()) {
                    val idEqual = TransactionSchemaV1.PersistentTransaction::sellerName.equal(seller)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (buyer.isNotEmpty()) {
                    val idEqual = TransactionSchemaV1.PersistentTransaction::buyerName.equal(buyer)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }


                if (from.isNotEmpty() && to.isNotEmpty()) {
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    var myFrom = format.parse(from)
                    var myTo = format.parse(to)
                    var dateBetween = TransactionSchemaV1.PersistentTransaction::data.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = proxy.vaultQueryBy<TransactionState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }
        } catch (ex: Exception) {
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)

            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     * Get aggregateValues
     */
    @GetMapping(value = [ "transaction/getAggregateValues" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getAggregateValues(@DefaultValue("unconsumed") @QueryParam("status") status: String): Response {

        try{

            var myStatus = Vault.StateStatus.UNCONSUMED

            when(status){
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            if(myLegalName.organisation != "Sman"){
                var criteriaV : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)
                var criteriaA : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)

                var totalSold = builder {
                    val sellerMe = TransactionSchemaV1.PersistentTransaction::sellerName.equal(myLegalName.toString())
                    val sellerCriteria = QueryCriteria.VaultCustomQueryCriteria(sellerMe, myStatus)
                    criteriaV = criteriaV.and(sellerCriteria)

                    val sum = TransactionSchemaV1.PersistentTransaction::totalPrice.sum()
                    val sumCriteria = QueryCriteria.VaultCustomQueryCriteria(sum, myStatus)
                    criteriaV = criteriaV.and(sumCriteria)

                    proxy.vaultQueryBy<TransactionState>(criteriaV).otherResults[0]
                }

                var totalBought = builder {
                    val buyerMe = TransactionSchemaV1.PersistentTransaction::buyerName.equal(myLegalName.toString())
                    val buyerCriteria = QueryCriteria.VaultCustomQueryCriteria(buyerMe, myStatus)
                    criteriaA = criteriaA.and(buyerCriteria)

                    val sum = TransactionSchemaV1.PersistentTransaction::totalPrice.sum()
                    val sumCriteria = QueryCriteria.VaultCustomQueryCriteria(sum, myStatus)
                    criteriaA = criteriaA.and(sumCriteria)

                    proxy.vaultQueryBy<TransactionState>(criteriaA).otherResults[0]
                }


                if(totalSold is Double && totalBought is Double){
                    return Response.ok(BalancePojo(totalSold, totalBought)).build()
                }else if(totalSold is Double){
                    return Response.ok(BalancePojo(totalSold, 0.0)).build()
                }else if(totalBought is Double){
                    return Response.ok(BalancePojo(0.0, totalBought)).build()
                }else{
                    return Response.ok(BalancePojo(0.0, 0.0)).build()
                }
            }else{

                var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)

                var totalSold = builder {


                    val sum = TransactionSchemaV1.PersistentTransaction::totalPrice.sum()
                    val sumCriteria = QueryCriteria.VaultCustomQueryCriteria(sum, myStatus)

                    proxy.vaultQueryBy<TransactionState>(sumCriteria).otherResults[0]
                }


                if(totalSold is Double){
                    return Response.ok(BalancePojo(totalSold, totalSold)).build()
                }else {
                    return Response.ok(BalancePojo(0.0, 0.0)).build()
                }

            }

        }catch (ex: Exception){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }

    }

    /**
     * Create a Transaction
     */
    @PostMapping(value = ["transaction/insert"], consumes = [ APPLICATION_JSON_VALUE ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun createTransaction(
            @RequestBody
            transactionPojo : TransactionPojo): Response {

        try {
            val buyer : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(transactionPojo.buyer))!!
            val seller : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(transactionPojo.seller))!!
            val snam : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Sman,L=Milan,C=IT"))!!

            val signedTx = proxy.startTrackedFlow(TransactionFlow::Starter, buyer, seller, snam, transactionPojo).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "transaction $signedTx committed to ledger.")

            return Response.status(CREATED).entity(resp).build()

        } catch (ex: Throwable) {

            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)

            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }



}
