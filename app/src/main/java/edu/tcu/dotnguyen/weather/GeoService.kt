package edu.tcu.dotnguyen.weather

import edu.tcu.dotnguyen.weather.model.Place
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GeoService {
    @GET("geo/1.0/reverse")
    fun getPlace(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") appid: String
    ): Call<List<Place>>
}
