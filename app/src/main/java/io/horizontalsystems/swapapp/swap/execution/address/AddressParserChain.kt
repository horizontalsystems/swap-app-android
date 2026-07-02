package io.horizontalsystems.swapapp.swap.execution.address

/**
 * Runs an address through an ordered list of [IAddressHandler]s, ported from the reference wallet's
 * `AddressParserChain`. An address is valid for the chain if any handler supports it; a throwing
 * handler is treated as "not supported" rather than failing the whole check.
 */
class AddressParserChain(private val handlers: List<IAddressHandler>) {

    fun isSupported(address: String): Boolean = handlers.any {
        try {
            it.isSupported(address)
        } catch (_: Throwable) {
            false
        }
    }
}