package com.empathie.energyaccounting.update

import java.io.IOException

/**
 * A filesystem problem while downloading or finalizing an update, such as a full disk or a
 * destination that cannot be written. It extends [IOException] so callers that only handle I/O
 * failures still work, but the updater reports it as a storage error instead of a network one.
 */
class StorageException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
