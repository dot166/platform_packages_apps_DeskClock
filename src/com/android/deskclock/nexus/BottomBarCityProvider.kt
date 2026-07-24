package com.android.deskclock.nexus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.launcher3.nexus.bottombar.lawnchair.util.broadcastReceiverFlow
import com.android.launcher3.nexus.bottombar.model.SmartspaceAction
import com.android.launcher3.nexus.bottombar.model.SmartspaceScores
import com.android.launcher3.nexus.bottombar.model.SmartspaceTarget
import com.android.launcher3.nexus.bottombar.provider.BottomBarDataSource
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BottomBarCityProvider(context: Context,
    serviceComponentName: ComponentName
): BottomBarDataSource(
    context,
    R.string.cities_activity_title,
    "BottomBarCityProvider",
    serviceComponentName
) {
    val intentFilter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
        addAction(DataModel.ACTION_WORLD_CITIES_CHANGED)
        addAction(Intent.ACTION_BOOT_COMPLETED)
        addAction(Intent.ACTION_LOCALE_CHANGED)
        addAction(ACTION_BOTTOM_BAR_FORCE_UPDATE_BROADCAST)
    }
    override fun internalTargets(locale: Locale): Flow<List<SmartspaceTarget>> = broadcastReceiverFlow(context, intentFilter)
        .map { intent ->
            val list = mutableListOf<SmartspaceTarget>()
            val cities = if (DataModel.getDataModel().getShowHomeClock()) {
                DataModel.getDataModel().getSelectedCities(locale).apply {
                    add(0, DataModel.getDataModel().getHomeCity(locale))
                }
            } else {
                DataModel.getDataModel().getSelectedCities(locale)
            }
            for (city in cities) {
                val localCal = Calendar.getInstance(TimeZone.getDefault())
                val cityCal: Calendar = Calendar.getInstance(city.timeZone)
                val displayDayOfWeek =
                    localCal.get(Calendar.DAY_OF_WEEK) != cityCal.get(Calendar.DAY_OF_WEEK)

                val time = if (displayDayOfWeek) {
                    val weekday =
                        cityCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)
                    val slashDay: String =
                        context.getString(R.string.world_day_of_week_label, weekday)
                    "${cityCal.get(Calendar.HOUR_OF_DAY)}:${cityCal.get(Calendar.MINUTE)}$slashDay"
                } else {
                    "${cityCal.get(Calendar.HOUR_OF_DAY)}:${cityCal.get(Calendar.MINUTE)}"
                }
                list.add(SmartspaceTarget(city.id, SmartspaceAction(city.id, title = city.name, subtitle = time), score = SmartspaceScores.SCORE_WORLD_CLOCKS, featureType = SmartspaceTarget.FeatureType.FEATURE_WORLD_CLOCKS))
            }
            list
        }

    override fun forceRefresh() {
        context.sendBroadcast(Intent(ACTION_BOTTOM_BAR_FORCE_UPDATE_BROADCAST))
    }

    companion object {
        const val ACTION_BOTTOM_BAR_FORCE_UPDATE_BROADCAST =
            "com.android.deskclock.nexus.FORCE_UPDATE_CITIES"
    }
}