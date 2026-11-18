package mcplus.protocol

/** Thrown when bytes on the wire cannot be decoded as valid protocol data. */
class ProtocolException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
