package com.example.source_service.di

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class ServiceModule(private val app: Application, private val context: Context) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingletonFactory<PreferenceStore> {
            AndroidPreferenceStore(context)
        }
        addSingletonFactory {
            NetworkPreferences(
                preferenceStore = get(),
                verboseLogging = isDebugBuildType,
            )
        }
        addSingletonFactory { NetworkHelper(context, get()) }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
    }
}
