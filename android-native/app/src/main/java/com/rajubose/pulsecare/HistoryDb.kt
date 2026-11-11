package com.rajubose.pulsecare

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Reading(
    val id: Long,
    val time: String,
    val pulse: Int?,
    val systolic: Int?,
    val diastolic: Int?,
    val note: String
)

class HistoryDb(context: Context) : SQLiteOpenHelper(context, "pulsecare.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recorded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                pulse INTEGER,
                systolic INTEGER,
                diastolic INTEGER,
                note TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(pulse: Int?, systolic: Int?, diastolic: Int?, note: String = "") {
        val values = ContentValues().apply {
            if (pulse != null) put("pulse", pulse)
            if (systolic != null) put("systolic", systolic)
            if (diastolic != null) put("diastolic", diastolic)
            put("note", note)
        }
        writableDatabase.insertOrThrow("readings", null, values)
    }

    fun latest(limit: Int = 100): List<Reading> {
        val out = mutableListOf<Reading>()
        readableDatabase.rawQuery(
            "SELECT id, recorded_at, pulse, systolic, diastolic, note FROM readings ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += Reading(
                    c.getLong(0), c.getString(1),
                    if (c.isNull(2)) null else c.getInt(2),
                    if (c.isNull(3)) null else c.getInt(3),
                    if (c.isNull(4)) null else c.getInt(4),
                    c.getString(5) ?: ""
                )
            }
        }
        return out
    }
}
