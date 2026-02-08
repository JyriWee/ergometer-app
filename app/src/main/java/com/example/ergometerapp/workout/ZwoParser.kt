package com.example.ergometerapp.workout

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

/**
 * Minimal, tolerant parser for Zwift `.zwo` files.
 *
 * Parsing errors throw [ZwoParseException] so callers can distinguish malformed XML from valid but
 * partially-known content.
 *
 * Tag handling rules:
 * - Tag values are de-duplicated.
 * - If a `<tag>` element has a `name` attribute, that value is used and any text content is ignored.
 * - If there is no `name` attribute, trimmed text content is used.
 * - Tags are only collected when inside a `<tags>` section.
 *
 * Unknown step handling:
 * - Inside `<workout>`, unknown tags are only recorded when they look like steps by having a
 *   `Duration` or `OnDuration` attribute, and the tag name starts with an uppercase letter.
 *
 * Example XML for manual testing:
 * ```
 * <workout_file>
 *   <name>Short Test</name>
 *   <description>Quick warmup and steady work.</description>
 *   <author>Trainer</author>
 *   <tags>
 *     <tag name="test"/>
 *     <tag>short</tag>
 *   </tags>
 *   <workout>
 *     <Warmup Duration="300" PowerLow="0.5" PowerHigh="0.75" Cadence="85"/>
 *     <SteadyState Duration="600" Power="0.8"/>
 *     <Cooldown Duration="300" PowerLow="0.6" PowerHigh="0.4"/>
 *   </workout>
 * </workout_file>
 * ```
 */
fun parseZwo(xml: String): WorkoutFile {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(StringReader(xml))

    var name: String? = null
    var description: String? = null
    var author: String? = null
    val tags = LinkedHashSet<String>()
    val steps = mutableListOf<Step>()

    var currentTextTag: String? = null
    var currentText = StringBuilder()
    var inWorkout = false
    var inTags = false

    fun flushTextIfNeeded(endTag: String) {
        val text = currentText.toString().trim()
        if (text.isEmpty()) return
        when (endTag) {
            "name" -> name = text
            "description" -> description = text
            "author" -> author = text
            "tag" -> tags.add(text)
        }
    }

    fun attrInt(tag: XmlPullParser, name: String): Int? =
        tag.getAttributeValue(null, name)?.toIntOrNull()

    fun attrDouble(tag: XmlPullParser, name: String): Double? =
        tag.getAttributeValue(null, name)?.toDoubleOrNull()

    fun attrStringMap(tag: XmlPullParser): Map<String, String> {
        if (tag.attributeCount == 0) return emptyMap()
        val map = LinkedHashMap<String, String>(tag.attributeCount)
        for (i in 0 until tag.attributeCount) {
            val key = tag.getAttributeName(i)
            val value = tag.getAttributeValue(i)
            if (key != null && value != null) {
                map[key] = value
            }
        }
        return map
    }

    try {
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name ?: ""
                if (tagName == "workout") {
                    inWorkout = true
                }
                if (tagName == "tags") {
                    inTags = true
                }

                    when (tagName) {
                        "name", "description", "author" -> {
                            currentTextTag = tagName
                            currentText = StringBuilder()
                        }
                    "tag" -> if (inTags) {
                        val attrName = parser.getAttributeValue(null, "name")
                        if (!attrName.isNullOrBlank()) {
                            tags.add(attrName)
                        } else {
                            currentTextTag = tagName
                            currentText = StringBuilder()
                        }
                    }
                        "Warmup" -> if (inWorkout) {
                            steps.add(
                                Step.Warmup(
                                    durationSec = attrInt(parser, "Duration"),
                                    powerLow = attrDouble(parser, "PowerLow"),
                                    powerHigh = attrDouble(parser, "PowerHigh"),
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "Cooldown" -> if (inWorkout) {
                            steps.add(
                                Step.Cooldown(
                                    durationSec = attrInt(parser, "Duration"),
                                    powerLow = attrDouble(parser, "PowerLow"),
                                    powerHigh = attrDouble(parser, "PowerHigh"),
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "SteadyState" -> if (inWorkout) {
                            steps.add(
                                Step.SteadyState(
                                    durationSec = attrInt(parser, "Duration"),
                                    power = attrDouble(parser, "Power"),
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "Ramp" -> if (inWorkout) {
                            steps.add(
                                Step.Ramp(
                                    durationSec = attrInt(parser, "Duration"),
                                    powerLow = attrDouble(parser, "PowerLow"),
                                    powerHigh = attrDouble(parser, "PowerHigh"),
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "IntervalsT" -> if (inWorkout) {
                            steps.add(
                                Step.IntervalsT(
                                    onDurationSec = attrInt(parser, "OnDuration"),
                                    offDurationSec = attrInt(parser, "OffDuration"),
                                    onPower = attrDouble(parser, "OnPower"),
                                    offPower = attrDouble(parser, "OffPower"),
                                    repeat = attrInt(parser, "Repeat"),
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "FreeRide" -> if (inWorkout) {
                            steps.add(
                                Step.FreeRide(
                                    durationSec = attrInt(parser, "Duration"),
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        else -> if (inWorkout) {
                            val looksLikeStep = parser.getAttributeValue(null, "Duration") != null ||
                                parser.getAttributeValue(null, "OnDuration") != null
                            val startsUppercase = tagName.firstOrNull()?.isUpperCase() == true
                            if (looksLikeStep && startsUppercase) {
                                // Preserve unknown step tags to keep forward compatibility with new ZWO variants.
                                steps.add(
                                    Step.Unknown(
                                        tagName = tagName,
                                        attributes = attrStringMap(parser),
                                    ),
                                )
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (currentTextTag != null) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tagName = parser.name ?: ""
                if (tagName == "workout") {
                    inWorkout = false
                }
                if (tagName == "tags") {
                    inTags = false
                }
                if (currentTextTag == tagName) {
                    flushTextIfNeeded(tagName)
                    currentTextTag = null
                    currentText = StringBuilder()
                }
                }
            }
            parser.next()
        }
    } catch (e: Exception) {
        if (e is XmlPullParserException || e is IOException || e is IllegalArgumentException) {
            val location = "line ${parser.lineNumber}, column ${parser.columnNumber}"
            throw ZwoParseException("Malformed ZWO XML at $location", e)
        }
        throw e
    }

    return WorkoutFile(
        name = name,
        description = description,
        author = author,
        tags = tags.toList(),
        steps = steps.toList(),
    )
}

class ZwoParseException(message: String, cause: Throwable) : Exception(message, cause)
