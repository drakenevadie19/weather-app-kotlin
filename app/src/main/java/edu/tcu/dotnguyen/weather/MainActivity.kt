package edu.tcu.dotnguyen.weather

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.provider.Settings.Global.getString
//import android.provider.Settings.Global.getString
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import edu.tcu.dotnguyen.weather.model.Place
import edu.tcu.dotnguyen.weather.databinding.ActivityMainBinding
import edu.tcu.dotnguyen.weather.model.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    // View binding
    private lateinit var binding: ActivityMainBinding
    private lateinit var view: View
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var weatherServices: WeatherService
    private lateinit var weatherResponse: WeatherResponse
    private lateinit var geoServices: GeoService
    private lateinit var geoResponse: Place


    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
                // generate a snack bar message like a toast to confirm the message
                updateLocationAndWeatherRepeatedly()
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.

                // generate a snack bar message like a toast to confirm the message
                Snackbar.make(view, "Location permission is required for weather updates.", Snackbar.LENGTH_LONG)
                    .show()
            }
        }

    private var cancellationTokenSource:CancellationTokenSource? = null

    private var weatherServiceCall:Call<WeatherResponse>? = null

    private var geoServiceCall:Call<List<Place>>? = null

    private var updateJob: Job? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        view = binding.root

        setContentView(view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val retrofit = Retrofit.Builder()
        .baseUrl(getString(R.string.base_url))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        weatherServices = retrofit.create(WeatherService::class.java)
        geoServices = retrofit.create(GeoService::class.java)

        requestLocationPermission()
    }


    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
            }
            // True, then show details
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                    //GENERATE snack bar to explain

                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.

                // If OK is clicked, show the prompt/launcher
                requestPermissionLauncher.launch(
                    Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    private fun cancelRequest() {
        // Cancel the location request
        cancellationTokenSource?.cancel()
        weatherServiceCall?.cancel()
        geoServiceCall?.cancel()
        updateJob?.cancel()
    }

    private fun updateLocationAndWeatherRepeatedly() {
        // IO coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                // Launch or withContext
                updateJob = launch(Dispatchers.Main) {
                    updateLocationAndWeather()
                }
                delay(15000)
                cancelRequest()
            }
        }
    }

    private fun updateLocationAndWeather() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> {
                cancellationTokenSource = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    // TImer to stop request for location, in case can not get location info and app run forever
                    cancellationTokenSource?.token).addOnSuccessListener { it ->
                        if (it != null) {
                            updateWeather(it)
                        } else {
                            displayUpdateFailed()
                        }
                    }
            }
        }
    }

    private fun updateWeather(location: Location) {
        // Call to get weather
        weatherServiceCall = weatherServices.getWeather(
            location.latitude,
            location.longitude,
            getString(R.string.appid),
            "imperial"
        )

        weatherServiceCall?.enqueue(
            object: Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    // Got weather response
                    val weatherResponseNullable = response.body()
                    if (weatherResponseNullable != null) {
                        // The response is good
                        weatherResponse = weatherResponseNullable
                        updatePlace(location)
                        displayWeather()
                    }
                }

                override fun onFailure(p0: Call<WeatherResponse>, p1: Throwable) {
                    displayUpdateFailed()
                }
            })
        }
    }

    private fun updatePlace(location: Location) {
        geoServiceCall = geoServices.getPlace(
            location.latitude,
            location.longitude,
            getString(R.string.appid)
        )
        geoServiceCall?.enqueue(
            object : Callback<List<Place>> {
                override fun onResponse(call: Call<List<Place>>, response: Response<List<Place>>) {
                    response.body()?.let {
                        geoResponse = it
                        displayPlace(true)
                    } ?: displayPlace(false)
                }

                override fun onFailure(call: Call<Place>, t: Throwable) {
                    displayPlace(false)
                }
            })
    }


    private fun displayWeather() {
        val description = weatherResponse.weather[0].description.split(" ").joinToString(" ") {
            it.replaceFirstChar { char -> char.uppercase }
        }
        binding.descriptionTv.text = getString(R.string.description, description, WeatherResponse.main.temp_max, WeatherResponse.main.temp_min)

        val utcInMs = (weatherResponse.sys.sunrise + weatherRespomse.timezone) * 1000L - TimeZone.getDefault().rawOffset
        val sunRise = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(utcInMs))
    }

    private fun displayPlace(isSuccess: Boolean) {

        // When geoCall success => displayPlace(true)
        // If is is success fail => displayPlace(false)
        binding.placeTv.text = getString(R.string.place, geoResponse[0].name, gepResponse[0].state)

    }

    private fun displayUpdateFailed() {
    }
}
