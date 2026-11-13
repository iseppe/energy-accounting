package com.empathie.energyaccounting.data.export

/**
 * How [BackupSerializer.restoreJson] treats the local ledger.
 */
enum class RestoreMode {
    /** Keeps local data and adds what is missing, deduplicating by natural identity. */
    MERGE,

    /** Clears local entries and templates first, then imports the backup verbatim. */
    REPLACE,
}
