package com.ahmadi.net

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class RouterApi(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) {
    private fun writeWord(out: BufferedOutputStream, word: String) {
        val b = word.toByteArray(Charsets.UTF_8)
        val n = b.size
        when {
            n < 0x80 -> out.write(n)
            n < 0x4000 -> { out.write((n shr 8) or 0x80); out.write(n and 0xff) }
            n < 0x200000 -> { out.write((n shr 16) or 0xC0); out.write((n shr 8) and 0xff); out.write(n and 0xff) }
            n < 0x10000000 -> { out.write((n shr 24) or 0xE0); out.write((n shr 16) and 0xff); out.write((n shr 8) and 0xff); out.write(n and 0xff) }
            else -> { out.write(0xF0); out.write((n shr 24) and 0xff); out.write((n shr 16) and 0xff); out.write((n shr 8) and 0xff); out.write(n and 0xff) }
        }
        out.write(b)
    }

    private fun readLength(input: BufferedInputStream): Int {
        val c = input.read()
        if (c < 0) throw IllegalStateException("روتر اتصال را بست")
        return when {
            (c and 0x80) == 0 -> c
            (c and 0xC0) == 0x80 -> ((c and 0x3f) shl 8) or input.read()
            (c and 0xE0) == 0xC0 -> ((c and 0x1f) shl 16) or (input.read() shl 8) or input.read()
            (c and 0xF0) == 0xE0 -> ((c and 0x0f) shl 24) or (input.read() shl 16) or (input.read() shl 8) or input.read()
            else -> { repeat(4) { input.read() }; 0 }
        }
    }

    private fun readSentence(input: BufferedInputStream): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val len = readLength(input)
            if (len == 0) break
            val bytes = ByteArray(len)
            var off = 0
            while (off < len) {
                val got = input.read(bytes, off, len - off)
                if (got < 0) throw IllegalStateException("پاسخ ناقص از روتر")
                off += got
            }
            words.add(String(bytes, Charsets.UTF_8))
        }
        return words
    }

    private fun writeSentence(out: BufferedOutputStream, words: List<String>) {
        words.forEach { writeWord(out, it) }
        out.write(0)
        out.flush()
    }

    fun command(path: String, args: Map<String, String> = emptyMap()): List<Map<String, String>> {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 7000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            writeSentence(output, listOf("/login", "=name=$username", "=password=$password"))
            var loginOk = false
            var attempts = 0
            while (!loginOk && attempts++ < 10) {
                val sentence = readSentence(input)
                if (sentence.firstOrNull() == "!done") loginOk = true
                if (sentence.firstOrNull() == "!trap" || sentence.firstOrNull() == "!fatal") {
                    throw IllegalStateException(sentence.joinToString(" "))
                }
            }
            if (!loginOk) throw IllegalStateException("ورود روتر ناموفق است؛ نام کاربری و رمز/API را بررسی کنید")
            val words = mutableListOf(path)
            args.forEach { (k, v) -> words.add("=$k=$v") }
            writeSentence(output, words)
            val rows = mutableListOf<Map<String, String>>()
            while (true) {
                val sentence = readSentence(input)
                when (sentence.firstOrNull()) {
                    "!re" -> {
                        val row = linkedMapOf<String, String>()
                        sentence.drop(1).forEach { w ->
                            if (w.startsWith("=")) {
                                val split = w.indexOf('=', 1)
                                if (split > 1) row[w.substring(1, split)] = w.substring(split + 1)
                            }
                        }
                        rows.add(row)
                    }
                    "!trap", "!fatal" -> throw IllegalStateException(sentence.joinToString(" "))
                    "!done" -> return rows
                }
            }
        }
    }

    fun resource() = command("/system/resource/print").firstOrNull() ?: emptyMap()
    fun identity() = command("/system/identity/print").firstOrNull() ?: emptyMap()
    fun active() = command("/ip/hotspot/active/print")
    fun users() = command("/ip/hotspot/user/print")
    fun addUser(name: String, pass: String, profile: String = "default", comment: String = "AHMADI-NET") =
        command("/ip/hotspot/user/add", mapOf("name" to name, "password" to pass, "profile" to profile, "comment" to comment))
    fun disableUser(id: String) = command("/ip/hotspot/user/disable", mapOf(".id" to id))
    fun removeActive(id: String) = command("/ip/hotspot/active/remove", mapOf(".id" to id))
}
