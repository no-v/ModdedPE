package com.mcal.moddedpe.task

import android.content.Context
import com.mcal.moddedpe.utils.FileHelper
import com.mcal.moddedpe.utils.ZipHelper
import java.io.File

class ResourceInstaller(
    private val context: Context
) {
    fun install() {
        extract("resources/worlds.zip","games/com.mojang/minecraftWorlds")
        extract("resources/behavior_packs.zip","games/com.mojang/behavior_packs")
        extract("resources/resource_packs.zip","games/com.mojang/resource_packs")
        extract("resources/skin_packs.zip","games/com.mojang/skin_packs")
    }

    private fun extract(assetsName: String, output: String) {
        try {
            val tmp = File.createTempFile("temp", ".zip")
            val outputDir = File(context.filesDir.parentFile, output)
            context.assets.open(assetsName).use {
                FileHelper.writeToFile(tmp, it)
            }
            ZipHelper.extractFiles(tmp, outputDir)
            // tmp.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
