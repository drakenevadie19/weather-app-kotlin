package edu.tcu.dotnguyen.weather;
import edu.tcu.bmei.weatherdemo.model.Place
import edu.tcu.dotnguyen.weather.model.WeatherResponse;
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

public interface GeoService {
    @GET("geo/1.0/reverse")
    fun getWeather(
            @Query("lat") lat: Double,
            @Query("lon") lon: Double,
            @Query("appid") appid: String,
            @Query("units") units: String
    ): Call<List<Place>>
}
