package com.iponlove.app.core.network.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Serializes BigDecimal as a JSON number (Double). PostgREST returns numeric(14,2) as a
 * JSON number. BigDecimal.valueOf(double) uses the double's canonical string representation
 * (Ryu algorithm), which is exact for the 2-decimal-place money values used in this app.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeDouble(value.toDouble())

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal.valueOf(decoder.decodeDouble())
}

/**
 * Serializes Instant as an ISO-8601 string. PostgREST returns timestamptz columns as
 * ISO-8601 strings (e.g. "2026-06-24T10:30:00+00:00"); Instant.parse() handles both
 * UTC "Z" suffix and explicit offset forms.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}

/**
 * Serializes LocalDate as "YYYY-MM-DD". PostgREST returns date columns in this format.
 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}
