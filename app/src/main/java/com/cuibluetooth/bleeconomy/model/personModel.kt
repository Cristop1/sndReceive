package com.cuibluetooth.bleeconomy.model

import kotlinx.serialization.Serializable

@Serializable
data class Coordinates(
    val x: Double = 0.0,            // meters
    val y: Double = 0.0,            // meters
    val ts: Long? = null            // optional epoch millis from server
)

/* If your backend uses strings (recommended) switch to String */
typealias PersonId = Int

enum class Role { SELF, STUDENT, TEACHER, VISITOR }

/* ---------- Domain models (used by the app) ---------- */

interface Person {
    val id: PersonId
    val username: String
    val name: String?
    val institutionalCode: String?
    val projectName: String?
    var actual: Coordinates
    val role: Role

}

/** Device owner (the phone running the app). */
data class OwnPhone(
    override val id: PersonId,
    override val username: String,
    override var actual: Coordinates = Coordinates(),
    override val name: String? = null,
    override val institutionalCode: String? = null,
    override val projectName: String? = null,
) : Person {
    override val role: Role = Role.SELF
}

/** Student can publish a fixed stand location. */
data class Student(
    override val id: PersonId,
    override val username: String,
    override var actual: Coordinates = Coordinates(),
    val stand: Coordinates? = null,
    override val name: String? = null,
    override val institutionalCode: String? = null,
    override val projectName: String? = null,
) : Person {
    override val role: Role = Role.STUDENT
}

/** Teacher keeps a path/history (optional). */
data class Teacher(
    override val id: PersonId,
    override val username: String,
    override var actual: Coordinates = Coordinates(),
    val track: MutableList<Coordinates> = mutableListOf(),
    override val name: String? = null,
    override val institutionalCode: String? = null,
    override val projectName: String? = null,
) : Person {
    override val role: Role = Role.TEACHER
}

/* ---------- Transport layer (what comes/goes over REST/MQTT) ---------- */

@Serializable
data class PositionDTO(
    val id: PersonId,
    val x: Double,
    val y: Double,
    val ts: Long
)

/** Snapshot = REST “give me all current positions”. */
@Serializable
data class PositionSnapshotDTO(
    val positions: List<PositionDTO>
)

/** Streaming update = MQTT “this person moved”. */
@Serializable
data class PositionUpdateDTO(
    val position: PositionDTO
)

/** App -> server: student sets their stand position. */
@Serializable
data class SetStandRequestDTO(
    val personId: PersonId,
    val standX: Double,
    val standY: Double
)

/* ---------- Mapping helpers ---------- */

fun Person.applyPosition(dto: PositionDTO) {
    if (dto.id == this.id) {
        this.actual = Coordinates(dto.x, dto.y, dto.ts)
        if (this is Teacher) this.track.add(this.actual)
    }
}

fun Map<PersonId, Person>.applySnapshot(snapshot: PositionSnapshotDTO): Map<PersonId, Person> {
    val updated = this.toMutableMap()
    snapshot.positions.forEach { p ->
        updated[p.id]?.applyPosition(p)
    }
    return updated
}