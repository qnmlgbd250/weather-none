package com.skypulse.weather.data

import android.content.Context
import android.util.Log
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class CityEntry(
    val name: String,
    val province: String,
    val lat: Double,
    val lon: Double
)

class CityDatabase(context: Context) {

    private val appContext = context.applicationContext

    init {
        try {
            ServiceSettings.updatePrivacyShow(appContext, true, true)
            ServiceSettings.updatePrivacyAgree(appContext, true)
        } catch (e: Exception) {
            Log.e("CityDatabase", "Privacy init failed", e)
        }
    }

    suspend fun search(query: String): List<CityEntry> {
        if (query.isBlank()) return emptyList()

        return suspendCancellableCoroutine { cont ->
            try {
                val poiQuery = PoiSearch.Query(query, "")
                poiQuery.pageSize = 15
                poiQuery.pageNum = 0

                val poiSearch = PoiSearch(appContext, poiQuery)
                poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                        Log.d("CityDatabase", "PoiSearch result: rCode=$rCode, pois=${result?.pois?.size}")
                        if (rCode == AMapException.CODE_AMAP_SUCCESS && result != null) {
                            val pois = result.pois
                            if (pois != null && pois.isNotEmpty()) {
                                val entries = pois.mapNotNull { poi ->
                                    val city = poi.cityName ?: return@mapNotNull null
                                    val title = poi.title ?: return@mapNotNull null
                                    val point: LatLonPoint? = poi.latLonPoint
                                    if (point == null) return@mapNotNull null
                                    val district = poi.adName ?: ""
                                    CityEntry(
                                        name = title,
                                        province = if (district.isNotBlank()) "$city · $district" else city,
                                        lat = point.latitude,
                                        lon = point.longitude
                                    )
                                }
                                if (cont.isActive) cont.resume(entries)
                            } else {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        } else {
                            Log.w("CityDatabase", "PoiSearch failed: rCode=$rCode")
                            if (cont.isActive) cont.resume(emptyList())
                        }
                    }

                    override fun onPoiItemSearched(item: PoiItem?, rCode: Int) {}
                })
                poiSearch.searchPOIAsyn()

                cont.invokeOnCancellation {
                    poiSearch.setOnPoiSearchListener(null)
                }
            } catch (e: Exception) {
                Log.e("CityDatabase", "Search exception", e)
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }
}
