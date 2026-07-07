package soy.iko.crush.proto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Workspace(
    val id: String,
    val path: String,
    @SerialName("yolo") val yolo: Boolean = false,
    @SerialName("debug") val debug: Boolean = false,
    @SerialName("data_dir") val dataDir: String? = null,
    @SerialName("version") val version: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    val config: JsonElement? = null,
    val env: List<String>? = null,
    val skills: List<SkillState>? = null,
)

@Serializable
data class SkillState(
    val name: String,
    val path: String,
    val state: Int = 0,
    val error: String? = null,
)

@Serializable
data class VersionInfo(
    val version: String,
    val commit: String = "",
    @SerialName("build_id") val buildId: String = "",
    @SerialName("go_version") val goVersion: String = "",
    val platform: String = "",
)

@Serializable
data class ErrorBody(val message: String)
