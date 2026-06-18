package com.github.maskedkunisquat.musicmanager.logic.ai

fun interface ModelDownloader {
    fun enqueue(modelFile: String, url: String, sha256: String?): Long
}
