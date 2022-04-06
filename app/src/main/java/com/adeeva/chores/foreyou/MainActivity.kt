package com.adeeva.chores.foreyou

import android.location.Location
import android.util.Log
import com.google.android.gms.location.*
import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.adeeva.chores.foreyou.Constants.API_KEY
import com.adeeva.chores.foreyou.Constants.METRIC_UNIT
import com.adeeva.chores.foreyou.models.WeatherResponse
import com.adeeva.chores.foreyou.network.WeatherConfig
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import okhttp3.internal.http.RealResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object{
        private const val  TAG = "MainActivity"
    }

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgresssDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turm it on",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission, Please enable them as it is Mandatory for the app",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogPermissions()
                    }
                }).onSameThread()
                .check()

            Toast.makeText(
                this,
                "Your location provider is already on.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRationalDialogPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature." +
            "it an be enabled under application settings")
            .setPositiveButton(
                "Go to settings"
            ){_,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){ dialog,
                _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()!!
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult){
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Currect Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Currect Longitude", "$longitude")

            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if (Constants.isNetworkAvailable(this)){
            val client = WeatherConfig.getWeatherService()
                .getWeather(latitude, longitude, METRIC_UNIT, API_KEY)

            showCustomProgressDialog()

            client.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    val responseBody = response.body()
                    if(response.isSuccessful && responseBody != null){

                        hideProgressDialog()

                        setupUI(responseBody)

                        Log.i("Response Result", "$responseBody")
                    }else{
                        Log.e(TAG, "onFailure: ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e(TAG, "Error: ${t.message.toString()}")
                    hideProgressDialog()
                }

            })
        } else {
            Toast.makeText(
                this@MainActivity,
                "No Internet Connection Available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showCustomProgressDialog(){
        mProgresssDialog = Dialog(this)
        mProgresssDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgresssDialog!!.show()

    }

    private fun hideProgressDialog(){
        if (mProgresssDialog != null){
            mProgresssDialog!!.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI(responseBody: WeatherResponse){
        for (i in responseBody.weather.indices){
            Log.i("Weather Nmae", responseBody.weather.toString())

            val tvCity: TextView = findViewById(R.id.tv_city_code)
            tvCity.text = responseBody.name
            val tvStatus: TextView = findViewById(R.id.tv_status)
            tvStatus.text = responseBody.weather[i].description
            val tvDegree: TextView = findViewById(R.id.tv_degree)
            tvDegree.text = responseBody.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            val tvMinTemp: TextView = findViewById(R.id.tv_min_temp)
            tvMinTemp.text = responseBody.main.temp_min.toString() + " min"
            val tvMaxTemp: TextView = findViewById(R.id.tv_max_temp)
            tvMaxTemp.text = responseBody.main.temp_max.toString() + " max"
            val tvSunrise: TextView = findViewById(R.id.tv_sunrise)
            tvSunrise.text = unixTime(responseBody.sys.sunrise)
            val tvSunset: TextView = findViewById(R.id.tv_sunset)
            tvSunset.text = unixTime(responseBody.sys.sunset)

            val tvWind: TextView = findViewById(R.id.tv_wind)
            tvWind.text = responseBody.wind.speed.toString() + " miles/hour"

            val tvPressure: TextView = findViewById(R.id.tv_pressure)
            tvPressure.text = responseBody.main.pressure.toString()

            val tvHumidity: TextView = findViewById(R.id.tv_humidity)
            tvHumidity.text = responseBody.main.humidity.toString() + " Percent"

            val tvVisibility: TextView = findViewById(R.id.tv_visibilty)
            tvVisibility.text = responseBody.visibility.toString()

            val ivWeather: ImageView = findViewById(R.id.iv_weather)

            when(responseBody.weather[i].icon){
                "01d" -> ivWeather.setImageResource(R.drawable.ic_sunny)
                "02d" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "03d" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "04d" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "09d" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "10d" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "11d" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "13d" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "01n" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "02n" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "03n" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "10n" -> ivWeather.setImageResource(R.drawable.ic_cloud)
                "11n" -> ivWeather.setImageResource(R.drawable.ic_cloud)


            }

        }
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    private fun getUnit(value: String): String? {
        var value = " °C"
        if("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }
}
