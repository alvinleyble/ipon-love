package com.iponlove.app.core.database.converters

import androidx.room.TypeConverter
import com.iponlove.app.feature.accounts.domain.model.AccountType
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters shared across the database.
 *
 *  - [Instant] ↔ epoch millis (Long): millisecond precision is exactly what the
 *    monotonic `updated_at` rule depends on (ADR-0001).
 *  - [LocalDate] ↔ epoch day (Long): a recurrence is a calendar date, not an instant.
 *  - [BigDecimal] ↔ plain string: money is never stored as a float (PHP, 2 dp).
 *  - enums ↔ their `name`.
 */
class IponConverters {

    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

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

    /**
     * A local-only id list (today: `transaction_drafts.localImageIds`, ADR-0066) as a
     * comma-joined string. The members are UUIDs, so the separator can never occur inside one;
     * an empty list round-trips through `""` rather than null, which keeps the column non-null.
     */
    @TypeConverter
    fun stringListToString(value: List<String>?): String? = value?.joinToString(",")

    @TypeConverter
    fun stringToStringList(value: String?): List<String>? =
        value?.split(",")?.filter { it.isNotBlank() }

    @TypeConverter
    fun recurringFrequencyToString(value: RecurringFrequency?): String? = value?.name

    @TypeConverter
    fun stringToRecurringFrequency(value: String?): RecurringFrequency? =
        value?.let(RecurringFrequency::valueOf)
}
