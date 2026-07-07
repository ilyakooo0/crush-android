package soy.iko.crush.net

import kotlinx.serialization.json.Json

/** Shared JSON instance configured for the crush server protocol. */
val CrushJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    classDiscriminator = "#type"
}
