package edu.tcu.dotnguyen.weather

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
import edu.tcu.dotnguyen.weather.databinding.ActivityMainBinding
import edu.tcu.dotnguyen.weather.model.Place
import edu.tcu.dotnguyen.weather.model.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.math.RoundingMode
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
    private lateinit var geoResponse: List<Place>

    // Progress dialog to show loading state
    private lateinit var dialog: Dialog

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
                // generate a snack bar message like a toast to confirm the message
                Snackbar.make(
                    view,
                    getString(R.string.location_permission_granted),
                    Snackbar.LENGTH_SHORT
                ).show()
                updateLocationAndWeatherRepeatedly()
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.

                // generate a snack bar message like a toast to confirm the message
                Snackbar.make(view,
                        getString(R.string.location_permission_denied),
                        Snackbar.LENGTH_SHORT)
                    .show()
            }
        }

    private var cancellationTokenSource:CancellationTokenSource? = null

    private var weatherServiceCall:Call<WeatherResponse>? = null

    private var geoServiceCall:Call<List<Place>>? = null

    private var updateJob: Job? = null

    private var delayJob: Job? = null

    // Counters for handling data success state
    private var counter = 0
    private var informationSuccessRead = false

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

    override fun onDestroy() {
        // Cancel delay job before onDestroy
        cancelRequest()
        delayJob?.cancel()
        informationSuccessRead = false
        counter = 0
        super.onDestroy()
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
//                print("Permission is granted")
                updateLocationAndWeatherRepeatedly()
            }
            // True, then show details
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> {
                //GENERATE snack bar to explain
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
                Snackbar.make(
                    view, getString(R.string.location_permission_required), Snackbar.LENGTH_INDEFINITE
                ).setAction("OK") {
                    // If OK is clicked, show the prompt/launcher
                    requestPermissionLauncher.launch(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                }.show()
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
        // Cancel this job when screen rotate => refresh => app clear everything --> calling onCreate
        delayJob = lifecycleScope.launch(Dispatchers.IO) {
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

        showProgressDialog()
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> {
                cancellationTokenSource = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    // TImer to stop request for location, in case can not get location info and app run forever
                    cancellationTokenSource?.token).addOnSuccessListener {
                        if (it != null) {
                            updateWeather(it)
                        } else {
                            displayUpdateFailed()
                        }
                    }
            }
        }
    }

    // Show a progress dialog while loading
    private fun showProgressDialog() {
        this.dialog = Dialog(this)
        this.dialog.setContentView(R.layout.in_progress)
        this.dialog.setCancelable(false)
        this.dialog.show()
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
                        weatherResponse = weatherResponseNullable
//                        print("Calling Weather Successfully")
                        updatePlace(location)
                        displayWeather()
                    }
                    else {
                        displayUpdateFailed()
                    }
                }

                override fun onFailure(p0: Call<WeatherResponse>, p1: Throwable) {
                    displayUpdateFailed()
                }
            }
        )
    }

    private fun displayWeather() {
        val description = weatherResponse.weather[0].description.split(" ").joinToString(" ") {
            it.replaceFirstChar { char -> char.uppercase() }
        }

        val temperature = weatherResponse.main.temp

        binding.temperatureTv.text = getString(
            R.string.temperature,
            temperature
        )

        // Handle weather condition icons
        var icon = weatherResponse.weather[0].icon
        val sameIcon = listOf("03", "04", "09", "11", "13", "50")
        if (sameIcon.contains(icon.substring(0, 2))) {
            icon = icon.substring(0, 2)
        }
        icon = "ic_$icon"
        with(binding) {
            conditionIv.setImageResource(resources.getIdentifier(icon, "drawable", packageName))
        }

        binding.descriptionTv.text = getString(
            R.string.description,
            description,
            weatherResponse.main.temp_max,
            weatherResponse.main.temp_min
        )

        val utcInMs1 = (weatherResponse.sys.sunrise + weatherResponse.timezone) * 1000L - TimeZone.getDefault().rawOffset
        val sunRise = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(utcInMs1))
        val utcInMs2 = (weatherResponse.sys.sunset + weatherResponse.timezone) * 1000L - TimeZone.getDefault().rawOffset
        val sunSet = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(utcInMs2))
        binding.sunDataTv.text = getString(
            R.string.sun_data,
            sunRise,
            sunSet
        )

        binding.windDataTv.text = getString(
            R.string.wind_data,
            weatherResponse.wind.speed,
            weatherResponse.wind.deg,
            weatherResponse.wind.gust
        )

        // Handle precipitation data
        val rain = weatherResponse.rain
        val snow = weatherResponse.snow
        if (rain != null) {
            binding.precipitationDataTv.text = getString(
                R.string.precipitation_time,
                roundToTwoDecimalPlaces(rain.one_h), "rain"
            )
        } else if (snow != null) {
            binding.precipitationDataTv.text = getString(
                R.string.precipitation_time,
                roundToTwoDecimalPlaces(snow.one_h), "snow"
            )
        } else {
            val humidity = weatherResponse.main.humidity
            val cloudiness = weatherResponse.clouds.all
            binding.precipitationDataTv.text = getString(
                R.string.precipitation_data, humidity, cloudiness
            )
        }
        binding.precipitationDataTv.text = getString(
            R.string.precipitation_data,
            weatherResponse.main.humidity,
            weatherResponse.clouds.all
        )

        val vis = weatherResponse.visibility / 1000 * 0.62137119
        val pressure = weatherResponse.main.pressure  * 0.02953
        binding.otherDataTv.text = getString(
            R.string.other_data,
            weatherResponse.main.feels_like,
            vis,
            pressure
        )

        // Update last connection status
        binding.connectionTv.text = getString(R.string.updated, "just now!")
        dialog.dismiss()
        counter = 0
        informationSuccessRead = true

    }

    private var timetracker = 0
    private fun displayUpdateFailed() {
        // Counting 1 minutes
        timetracker += 15
        if (timetracker >= 60) {
            counter++
            timetracker = 0

            // Start a coroutine to handle the delay
            if (informationSuccessRead) {
                val time = "$counter Minute${if (counter > 1) "s" else ""} Ago!"
                // Update the connection status text
                binding.connectionTv.text = getString(R.string.updated, time)
            }
        }

        dialog.dismiss()
    }

    // Round a number to two decimal places
    private fun roundToTwoDecimalPlaces(number: Double): String {
        return BigDecimal(number * 0.0393701).setScale(2, RoundingMode.HALF_UP).toString()
    }

    private fun updatePlace(location: Location) {
        geoServiceCall = geoServices.getPlace(
            location.latitude,
            location.longitude,
            getString(R.string.appid)
        )

        geoServiceCall?.enqueue(object: Callback<List<Place>> {
                override fun onResponse(call: Call<List<Place>>, response: Response<List<Place>>) {
                    response.body()?.let {
                        geoResponse = it
                        displayPlace(true)
                    }?: displayPlace(false)
                }

                override fun onFailure(call: Call<List<Place>>, t: Throwable) {
                    displayPlace(false)
                }
            }
        )
    }

    private fun displayPlace(isSuccess: Boolean) {
        // When geoCall success => displayPlace(true)
        // If is is success fail => displayPlace(false)
        if (isSuccess) {
            binding.placeTv.text = getString(R.string.place, geoResponse[0].name, geoResponse[0].state)
        }
    }
}
