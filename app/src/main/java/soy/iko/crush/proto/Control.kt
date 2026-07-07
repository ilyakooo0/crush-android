package soy.iko.crush.proto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerControl(val command: String)
