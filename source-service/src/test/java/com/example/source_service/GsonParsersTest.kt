package com.example.source_service

import android.annotation.SuppressLint
import com.google.gson.GsonBuilder
import com.google.gson.LongSerializationPolicy

import kotlin.test.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class GsonParsersTest {
    @SuppressLint("CheckResult")
    @Test
    fun sourceMangaParses() {
        val gson = GsonBuilder().setLongSerializationPolicy(LongSerializationPolicy.STRING).create();

        gson.fromJson("{\n" +
            "  \"source\": \"6084907896154116083\",\n" +
            "  \"manga\": {\n" +
            "    \"initialized\": false,\n" +
            "    \"status\": 0,\n" +
            "    \"thumbnail_url\": \"https://static.mangafire.to/5be8/i/8/83/8354fe0ab6288c4b1590512a959934d9.jpg\",\n" +
            "    \"title\": \"Nisekoi: False Love\",\n" +
            "    \"update_strategy\": \"ALWAYS_UPDATE\",\n" +
            "    \"url\": \"/manga/nisekoii.l326\"\n" +
            "  }\n" +
            "}", SourceManga::class.java)
    }
}
