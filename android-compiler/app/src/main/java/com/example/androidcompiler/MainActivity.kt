package com.example.androidcompiler

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        val title = TextView(this).apply {
            text = "Android Compiler"
            textSize = 26f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Готово к настройке"
            textSize = 16f
            setPadding(0, 20, 0, 16)
        }
        val components = Button(this).apply {
            text = "Проверить компоненты"
            setOnClickListener { checkComponents() }
        }
        val gradle = Button(this).apply {
            text = "Скачать Gradle 8.7"
            setOnClickListener { downloadGradle() }
        }
        val note = TextView(this).apply {
            text = "Первый этап: менеджер компонентов. Для полноценной локальной сборки нужны совместимые JDK, Android SDK и адаптированная среда запуска Gradle."
            textSize = 14f
            setPadding(0, 16, 0, 12)
        }
        log = TextView(this).apply { textSize = 12f }
        root.addView(title)
        root.addView(status)
        root.addView(components)
        root.addView(gradle)
        root.addView(note)
        val scroll = ScrollView(this)
        scroll.addView(log)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun checkComponents() {
        val dir = File(filesDir, "toolchain")
        val gradleZip = File(dir, "gradle-8.7-bin.zip")
        log.text = listOf(
            "Хранилище: ${dir.absolutePath}",
            "Gradle ZIP: ${if (gradleZip.exists()) "загружен (${gradleZip.length()} bytes)" else "не загружен"}",
            "JDK: пока не установлен",
            "Android SDK: пока не установлен"
        ).joinToString("\n")
        status.text = "Проверка завершена"
    }

    private fun downloadGradle() {
        Thread {
            try {
                val dir = File(filesDir, "toolchain").apply { mkdirs() }
                val target = File(dir, "gradle-8.7-bin.zip")
                val connection = (URL("https://services.gradle.org/distributions/gradle-8.7-bin.zip")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                }
                connection.connect()
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
                runOnUiThread {
                    status.text = "Gradle скачан"
                    log.text = "Файл: ${target.absolutePath}\nРазмер: ${target.length()} bytes\nСледующий этап: проверка архива, установка JDK и Android SDK."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Ошибка загрузки"
                    log.text = e.message ?: e.javaClass.simpleName
                    Toast.makeText(this, "Не удалось скачать Gradle", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
        status.text = "Загрузка Gradle…"
    }
}
