package io.horizontalsystems.swapapp.swap

/** No provider can route between the two selected tokens. */
class NoSupportedSwapProvider : Throwable("No swap provider supports this token pair")

/** Providers were asked for a quote but none returned a route. */
class SwapRouteNotFound : Throwable("No swap route found")

/** `POST /swap` failed with a provider error; [message] is the server's reason for the user. */
class SwapProviderError(message: String) : Throwable(message)
