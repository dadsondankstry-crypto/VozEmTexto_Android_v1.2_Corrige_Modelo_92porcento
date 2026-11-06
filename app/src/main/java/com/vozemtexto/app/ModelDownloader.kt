package com.vozemtexto.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Baixa o modelo português Vosk. Existem dois formatos oficiais de modelos Vosk:
 *
 * - Clássico (V1): final.mdl, mfcc.conf, HCLr.fst e Gr.fst na raiz.
 *   O vosk-model-small-pt-0.3 utiliza ESTE formato, sem am/, conf/ nem graph/.
 * - Atual (V2): am/final.mdl, conf/model.conf, conf/mfcc.conf e graph/.
 *
 * O código anterior aceitava SOMENTE V2 e, portanto, rejeitava incorretamente
 * o ZIP oficial português quando a barra chegava aos 92% (após o download).
 */
object ModelDownloader {
    const val MODEL_NAME = "vosk-model-small-pt-0.3"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    private const val MAX_EXTRACTED = 350L * 1024 * 1024

    fun modelDir(context: Context): File = File(context.filesDir, MODEL_NAME)

    private fun goodFile(root: File, name: String): Boolean {
        val file = File(root, name)
        return file.isFile && file.length() > 0
    }

    /** Formato antigo oficialmente reconhecido pelo Vosk (ConfigureV1). */
    private fun hasLegacyLayout(root: File): Boolean =
        goodFile(root, "final.mdl") &&
        goodFile(root, "mfcc.conf") &&
        (goodFile(root, "HCLG.fst") ||
            (goodFile(root, "HCLr.fst") && goodFile(root, "Gr.fst")))

    /** Formato moderno oficialmente reconhecido pelo Vosk (ConfigureV2). */
    private fun hasModernLayout(root: File): Boolean =
        goodFile(root, "am/final.mdl") &&
        goodFile(root, "conf/model.conf") &&
        goodFile(root, "conf/mfcc.conf") &&
        (goodFile(root, "graph/HCLG.fst") ||
            (goodFile(root, "graph/HCLr.fst") && goodFile(root, "graph/Gr.fst")))

    fun isInstalled(context: Context): Boolean =
        modelDir(context).let { hasLegacyLayout(it) || hasModernLayout(it) }

    fun install(context: Context, update: (Int, String) -> Unit, finish: (String?) -> Unit) {
        Thread({
            val download = File(context.cacheDir, "$MODEL_NAME.zip.part")
            val staging = File(context.filesDir, "$MODEL_NAME.installing")
            var connection: HttpURLConnection? = null
            try {
                if (isInstalled(context)) {
                    finish(null)
                    return@Thread
                }

                update(0, "Conectando ao download oficial…")
                connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    instanceFollowRedirects = true
                }
                connection.connect()
                check(connection.responseCode in 200..299) {
                    "Servidor do modelo respondeu: HTTP ${connection.responseCode}."
                }

                val contentLength = connection.contentLengthLong
                var downloaded = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(download).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (contentLength > 0) {
                                update((downloaded * 90 / contentLength).toInt().coerceIn(0, 90),
                                    "Baixando português: ${downloaded / 1_000_000} MB")
                            }
                        }
                    }
                }
                check(downloaded > 0) { "O servidor retornou um arquivo vazio." }
                if (contentLength > 0) {
                    check(downloaded == contentLength) {
                        "Download incompleto: $downloaded de $contentLength bytes. Tente novamente."
                    }
                }

                update(92, "Descompactando modelo de português…")
                staging.deleteRecursively()
                check(staging.mkdirs()) { "Não foi possível criar a pasta temporária do modelo." }

                var extracted = 0L
                var entries = 0
                val prefix = "$MODEL_NAME/"
                val safeRoot = staging.canonicalPath + File.separator
                ZipInputStream(download.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        // Extrai apenas arquivos da pasta do modelo e evita Zip Slip.
                        if (entry.name == prefix) {
                            zip.closeEntry()
                            continue
                        }
                        check(entry.name.startsWith(prefix)) {
                            "Arquivo inesperado no pacote: ${entry.name}"
                        }
                        val relative = entry.name.removePrefix(prefix)
                        if (relative.isNotEmpty()) {
                            val target = File(staging, relative)
                            check(target.canonicalPath.startsWith(safeRoot)) {
                                "Tentativa de extrair arquivo fora da pasta permitida."
                            }
                            if (entry.isDirectory) {
                                check(target.mkdirs() || target.isDirectory) {
                                    "Não foi possível criar uma pasta do modelo."
                                }
                            } else {
                                check(target.parentFile?.mkdirs() != false ||
                                    target.parentFile?.isDirectory == true) {
                                    "Não foi possível criar uma subpasta do modelo."
                                }
                                FileOutputStream(target).use { output ->
                                    val buffer = ByteArray(32 * 1024)
                                    while (true) {
                                        val count = zip.read(buffer)
                                        if (count == -1) break
                                        extracted += count
                                        check(extracted <= MAX_EXTRACTED) {
                                            "O pacote de reconhecimento excedeu o tamanho permitido."
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                }
                                entries++
                            }
                        }
                        zip.closeEntry()
                    }
                }

                check(entries > 0) { "O pacote do modelo não contém arquivos." }
                update(98, "Conferindo arquivos de reconhecimento…")
                check(hasLegacyLayout(staging) || hasModernLayout(staging)) {
                    "Formato de modelo não reconhecido. Esperado: final.mdl + mfcc.conf + " +
                        "HCLr.fst + Gr.fst (clássico), ou am/ + conf/ + graph/ (atual)."
                }

                val destination = modelDir(context)
                // Só apaga um diretório anterior se a nova instalação já foi validada.
                if (destination.exists()) {
                    check(destination.deleteRecursively()) {
                        "Não foi possível substituir os arquivos antigos do modelo."
                    }
                }
                check(staging.renameTo(destination)) {
                    "Não foi possível finalizar a instalação do modelo."
                }
                check(isInstalled(context)) { "Os arquivos instalados não passaram na verificação." }
                update(100, "Português pronto para uso offline")
                finish(null)
            } catch (e: Exception) {
                staging.deleteRecursively()
                finish(e.localizedMessage ?: "Não foi possível instalar o modelo")
            } finally {
                download.delete()
                connection?.disconnect()
            }
        }, "download-modelo-pt").start()
    }
}
