package exchange_api.oneinch.order

import exchange_api.oneinch.rest.response.LimitOrderStruct
import exchange_api.oneinch.rest.response.SubmitOrderRequest
import exchange_api.oneinch.token.BscTokenRegistry
import mu.KotlinLogging
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Builds and signs EIP-712 limit orders for the 1inch Limit Order Protocol v4.
 *
 * The order struct follows 1inch's LimitOrderV4 format.
 * makerTraits encoding (uint256):
 *   - bits 80-119: expiry timestamp (40 bits)
 *   - bits 120-159: nonce (40 bits)
 *   - bit 254: ALLOW_MULTIPLE_FILLS flag
 */
class LimitOrderBuilder(
    private val privateKey: String,
    private val tokenRegistry: BscTokenRegistry,
    private val chainId: Int = 56,
    private val limitOrderProtocolAddress: String = "0x111111125421cA6dc452d289314280a0f8842A65"
) {
    private val log = KotlinLogging.logger {}
    private val credentials: Credentials = Credentials.create(privateKey)
    private val random = SecureRandom()
    private var nonceCounter = System.currentTimeMillis()

    val walletAddress: String get() = credentials.address

    data class SignedOrder(
        val order: LimitOrderStruct,
        val orderHash: String,
        val signature: String
    )

    /**
     * Build and sign a limit order.
     *
     * @param makerAssetSymbol symbol of the token the maker is offering (e.g., "USDT")
     * @param takerAssetSymbol symbol of the token the maker wants to receive (e.g., "BTC")
     * @param makingAmount amount of maker asset in human-readable form
     * @param takingAmount amount of taker asset in human-readable form
     * @param expirySeconds order TTL in seconds
     * @param receiver address that receives taker tokens (defaults to maker)
     */
    fun buildAndSign(
        makerAssetSymbol: String,
        takerAssetSymbol: String,
        makingAmount: BigDecimal,
        takingAmount: BigDecimal,
        expirySeconds: Long = 3600,
        receiver: String = "0x0000000000000000000000000000000000000000"
    ): SignedOrder {
        val makerAddress = credentials.address
        val makerAssetAddress = tokenRegistry.getAddress(makerAssetSymbol)
        val takerAssetAddress = tokenRegistry.getAddress(takerAssetSymbol)
        val makingAmountWei = tokenRegistry.toWei(makingAmount, makerAssetSymbol)
        val takingAmountWei = tokenRegistry.toWei(takingAmount, takerAssetSymbol)

        val salt = generateSalt()
        val expiryTimestamp = System.currentTimeMillis() / 1000 + expirySeconds
        val nonce = nextNonce()
        val makerTraits = encodeMakerTraits(expiryTimestamp, nonce, allowMultipleFills = true)

        val order = LimitOrderStruct(
            salt = salt.toString(),
            maker = makerAddress,
            receiver = receiver,
            makerAsset = makerAssetAddress,
            takerAsset = takerAssetAddress,
            makingAmount = makingAmountWei.toString(),
            takingAmount = takingAmountWei.toString(),
            makerTraits = makerTraits.toString()
        )

        val orderHash = computeOrderHash(order)
        val signature = signOrderHash(orderHash)

        log.info {
            "Built limit order: maker=$makerAssetSymbol($makingAmount) -> taker=$takerAssetSymbol($takingAmount), " +
                "expiry=${expirySeconds}s, hash=$orderHash"
        }

        return SignedOrder(order, orderHash, signature)
    }

    fun toSubmitRequest(signedOrder: SignedOrder): SubmitOrderRequest {
        return SubmitOrderRequest(
            orderHash = signedOrder.orderHash,
            signature = signedOrder.signature,
            data = signedOrder.order
        )
    }

    /**
     * Encode makerTraits as a uint256 with:
     * - bits 80-119: expiry timestamp (40 bits)
     * - bits 120-159: nonce (40 bits)
     * - bit 254: ALLOW_MULTIPLE_FILLS
     */
    private fun encodeMakerTraits(
        expiryTimestamp: Long,
        nonce: Long,
        allowMultipleFills: Boolean
    ): BigInteger {
        var traits = BigInteger.ZERO

        // Set expiry at bits 80-119
        val expiryBig = BigInteger.valueOf(expiryTimestamp)
        traits = traits.or(expiryBig.shiftLeft(80))

        // Set nonce at bits 120-159
        val nonceBig = BigInteger.valueOf(nonce)
        traits = traits.or(nonceBig.shiftLeft(120))

        // Set ALLOW_MULTIPLE_FILLS at bit 254
        if (allowMultipleFills) {
            traits = traits.setBit(254)
        }

        return traits
    }

    /**
     * Compute EIP-712 typed data hash for the limit order.
     * domain separator: name="1inch Limit Order Protocol", version="4", chainId, verifyingContract
     */
    private fun computeOrderHash(order: LimitOrderStruct): String {
        val domainSeparator = computeDomainSeparator()
        val structHash = computeStructHash(order)

        // EIP-712: "\x19\x01" ++ domainSeparator ++ structHash
        val encoded = ByteArray(2 + 32 + 32)
        encoded[0] = 0x19
        encoded[1] = 0x01
        System.arraycopy(domainSeparator, 0, encoded, 2, 32)
        System.arraycopy(structHash, 0, encoded, 34, 32)

        return Numeric.toHexString(Hash.sha3(encoded))
    }

    private fun computeDomainSeparator(): ByteArray {
        val typeHash = Hash.sha3(
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".toByteArray()
        )
        val nameHash = Hash.sha3("1inch Limit Order Protocol".toByteArray())
        val versionHash = Hash.sha3("4".toByteArray())
        val chainIdBytes = Numeric.toBytesPadded(BigInteger.valueOf(chainId.toLong()), 32)
        val contractBytes = Numeric.toBytesPadded(Numeric.toBigInt(limitOrderProtocolAddress), 32)

        val encoded = ByteArray(5 * 32)
        System.arraycopy(typeHash, 0, encoded, 0, 32)
        System.arraycopy(nameHash, 0, encoded, 32, 32)
        System.arraycopy(versionHash, 0, encoded, 64, 32)
        System.arraycopy(chainIdBytes, 0, encoded, 96, 32)
        System.arraycopy(contractBytes, 0, encoded, 128, 32)

        return Hash.sha3(encoded)
    }

    private fun computeStructHash(order: LimitOrderStruct): ByteArray {
        val typeHash = Hash.sha3(
            "Order(uint256 salt,address maker,address receiver,address makerAsset,address takerAsset,uint256 makingAmount,uint256 takingAmount,uint256 makerTraits)".toByteArray()
        )

        val encoded = ByteArray(9 * 32)
        System.arraycopy(typeHash, 0, encoded, 0, 32)
        System.arraycopy(Numeric.toBytesPadded(BigInteger(order.salt), 32), 0, encoded, 32, 32)
        System.arraycopy(Numeric.toBytesPadded(Numeric.toBigInt(order.maker), 32), 0, encoded, 64, 32)
        System.arraycopy(Numeric.toBytesPadded(Numeric.toBigInt(order.receiver), 32), 0, encoded, 96, 32)
        System.arraycopy(Numeric.toBytesPadded(Numeric.toBigInt(order.makerAsset), 32), 0, encoded, 128, 32)
        System.arraycopy(Numeric.toBytesPadded(Numeric.toBigInt(order.takerAsset), 32), 0, encoded, 160, 32)
        System.arraycopy(Numeric.toBytesPadded(BigInteger(order.makingAmount), 32), 0, encoded, 192, 32)
        System.arraycopy(Numeric.toBytesPadded(BigInteger(order.takingAmount), 32), 0, encoded, 224, 32)
        System.arraycopy(Numeric.toBytesPadded(BigInteger(order.makerTraits), 32), 0, encoded, 256, 32)

        return Hash.sha3(encoded)
    }

    private fun signOrderHash(orderHash: String): String {
        val hashBytes = Numeric.hexStringToByteArray(orderHash)
        val keyPair: ECKeyPair = credentials.ecKeyPair
        val signatureData: Sign.SignatureData = Sign.signMessage(hashBytes, keyPair, false)

        // Combine r + s + v into a single hex string
        val r = signatureData.r
        val s = signatureData.s
        val v = signatureData.v

        val signatureBytes = ByteArray(65)
        System.arraycopy(r, 0, signatureBytes, 0, 32)
        System.arraycopy(s, 0, signatureBytes, 32, 32)
        signatureBytes[64] = v[0]

        return Numeric.toHexString(signatureBytes)
    }

    private fun generateSalt(): BigInteger {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return BigInteger(1, bytes)
    }

    @Synchronized
    private fun nextNonce(): Long = ++nonceCounter
}
