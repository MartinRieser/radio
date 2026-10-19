package com.pulsecast.radio.data

import com.pulsecast.radio.model.Station
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RadioBrowserApi {

    @GET("json/stations/topclick/{limit}")
    suspend fun getTopClickStations(
        @Path("limit") limit: Int = 40,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<Station>

    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String,
        @Query("limit") limit: Int = 40,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<Station>

    @GET("json/stations/bytag/{tag}")
    suspend fun getStationsByTag(
        @Path("tag") tag: String,
        @Query("limit") limit: Int = 40,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<Station>

    @GET("json/stations/bycountrycodeexact/{countrycode}")
    suspend fun getStationsByCountry(
        @Path("countrycode") countryCode: String,
        @Query("limit") limit: Int = 40,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<Station>
}
