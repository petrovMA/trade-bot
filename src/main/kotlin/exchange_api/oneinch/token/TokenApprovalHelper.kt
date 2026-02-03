package exchange_api.oneinch.token

import mu.KotlinLogging
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Helper for one-time ERC-20 token approvals for the 1inch Limit Order Protocol contract.
 * Before submitting limit orders, the maker must approve the protocol contract to spend their tokens.
 */
class TokenApprovalHelper(
    private val privateKey: String,
    private val bscRpcUrl: String,
    private val limitOrderProtocolAddress: String = "0x111111125421cA6dc452d289314280a0f8842A65"
) {
    private val log = KotlinLogging.logger {}
    private val web3j: Web3j = Web3j.build(HttpService(bscRpcUrl))
    private val credentials: Credentials = Credentials.create(privateKey)
    private val walletAddress: String = credentials.address

    companion object {
        val MAX_UINT256: BigInteger = BigInteger.TWO.pow(256).subtract(BigInteger.ONE)
        private val GAS_PRICE = BigInteger.valueOf(3_000_000_000L) // 3 Gwei for BSC
        private val GAS_LIMIT = BigInteger.valueOf(100_000L)
    }

    /**
     * Check current allowance for a token.
     * @return the current allowance amount
     */
    fun getAllowance(tokenAddress: String): BigInteger {
        val function = Function(
            "allowance",
            listOf(Address(walletAddress), Address(limitOrderProtocolAddress)),
            listOf(object : TypeReference<Uint256>() {})
        )

        val encodedFunction = FunctionEncoder.encode(function)
        val ethCall = web3j.ethCall(
            Transaction.createEthCallTransaction(walletAddress, tokenAddress, encodedFunction),
            DefaultBlockParameterName.LATEST
        ).send()

        val result = FunctionReturnDecoder.decode(ethCall.value, function.outputParameters)
        return if (result.isNotEmpty()) (result[0] as Uint256).value else BigInteger.ZERO
    }

    /**
     * Approve unlimited spending if current allowance is insufficient.
     * @return true if approval was already sufficient or tx was sent successfully
     */
    fun ensureApproval(tokenAddress: String, requiredAmount: BigInteger = MAX_UINT256): Boolean {
        val currentAllowance = getAllowance(tokenAddress)

        if (currentAllowance >= requiredAmount) {
            log.info("Token $tokenAddress already approved: allowance=$currentAllowance")
            return true
        }

        log.info("Approving token $tokenAddress for 1inch protocol (current=$currentAllowance)")
        return sendApprovalTx(tokenAddress, MAX_UINT256)
    }

    private fun sendApprovalTx(tokenAddress: String, amount: BigInteger): Boolean {
        return try {
            val function = Function(
                "approve",
                listOf(Address(limitOrderProtocolAddress), Uint256(amount)),
                emptyList()
            )

            val encodedFunction = FunctionEncoder.encode(function)

            val nonce = web3j.ethGetTransactionCount(walletAddress, DefaultBlockParameterName.LATEST)
                .send().transactionCount

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                GAS_PRICE,
                GAS_LIMIT,
                tokenAddress,
                BigInteger.ZERO,
                encodedFunction
            )

            val signedMessage = TransactionEncoder.signMessage(rawTransaction, 56L, credentials)
            val hexValue = Numeric.toHexString(signedMessage)

            val txResponse = web3j.ethSendRawTransaction(hexValue).send()

            if (txResponse.hasError()) {
                log.error("Approval tx failed: ${txResponse.error.message}")
                false
            } else {
                log.info("Approval tx sent: ${txResponse.transactionHash}")
                // Wait for confirmation
                waitForTxReceipt(txResponse.transactionHash)
                true
            }
        } catch (e: Exception) {
            log.error("Failed to send approval tx for $tokenAddress", e)
            false
        }
    }

    private fun waitForTxReceipt(txHash: String, maxAttempts: Int = 30) {
        for (i in 1..maxAttempts) {
            val receipt = web3j.ethGetTransactionReceipt(txHash).send()
            if (receipt.transactionReceipt.isPresent) {
                val status = receipt.transactionReceipt.get().status
                if (status == "0x1") {
                    log.info("Approval tx confirmed: $txHash")
                } else {
                    log.error("Approval tx reverted: $txHash")
                }
                return
            }
            Thread.sleep(2000)
        }
        log.warn("Approval tx not confirmed after ${maxAttempts * 2}s: $txHash")
    }

    fun close() {
        web3j.shutdown()
    }
}
