package com.iponlove.app.core.database.converters

import androidx.room.TypeConverter
import com.iponlove.app.feature.accounts.domain.model.AccountType
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/**
 * Room type converters shared across the database.
 *
 *  - [Instant] ↔ epoch millis (Long): millisecond precision is exactly what the
 *    monotonic `updated_at` rule depends on (ADR-0001).
 *  - [BigDecimal] ↔ plain string: money is never stored as a float (PHP, 2 dp).
 *  - enums ↔ their `name`.
 */
class IponConverters {

    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun accountTypeToString(value: AccountType?): String? = value?.name

    @TypeConverter
    fun stringToAccountType(value: String?): AccountType? = value?.let(AccountType::valueOf)

    @TypeConverter
    fun categoryTypeToString(value: CategoryType?): String? = value?.name

    @TypeConverter
    fun stringToCategoryType(value: String?): CategoryType? = value?.let(CategoryType::valueOf)

    @TypeConverter
    fun transactionTypeToString(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun stringToTransactionType(value: String?): TransactionType? = value?.let(TransactionType::valueOf)
}
