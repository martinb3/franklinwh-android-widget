package org.mbs3.franklinwh.widget.protocol

import java.io.IOException

/** A received frame was malformed or its obfuscated portion could not be decoded. */
class FrameException(message: String, cause: Throwable? = null) : IOException(message, cause)
