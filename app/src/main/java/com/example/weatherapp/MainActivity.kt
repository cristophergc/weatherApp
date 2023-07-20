package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setUpUi()

        if (!isLocationEnabled()){
            Toast.makeText(this,
                "Your location provider is turned off. Please turn it on",
                Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                           requestNewLocationData()
                        }
                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity, "Permission denied", Toast.LENGTH_LONG).show()
                            }
                    }
                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ){
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
            }
    }

    private fun isLocationEnabled(): Boolean{
        //this provide access to the system location services.
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestNewLocationData()
                true
            }else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(false)
            .setMaxUpdates(1)
            .build()

        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult!!.lastLocation
            val latitude = mLastLocation!!.latitude
            Log.i("current latitude", "$latitude")
            val longitude = mLastLocation!!.longitude
            Log.i("current latitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrr", t.message.toString())
                    hideProgressDialog()
                }

                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>?
                ) {
                   if (response!!.isSuccessful){
                        hideProgressDialog()
                       val weatherList: WeatherResponse? = response.body()

                       val weatherResponseJsonString = Gson().toJson(weatherList)
                       val editor = mSharedPreferences.edit()
                       editor.putString(Constants.WEATHE_RESPONSO_DATA, weatherResponseJsonString)
                       editor.apply()

                       setUpUi()
                       Log.i("Response result", "$weatherList")
                   }else{
                       val rc = response.code()
                       when(rc){
                           400 -> {
                               Log.e("Error 400", "Bad Connection")
                           }
                           404 -> {
                               Log.e("Error 404", "not found")
                           }else -> {
                               Log.e("Error", "Generic Error")
                           }
                       }
                   }
                }
            })

        }else{
            Toast.makeText(this@MainActivity,
                "No internet connection available",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this).setMessage("It looks like you have turned off the permission required for this feature. " +
                "It can be enabled under the Applications Settings")
            .setPositiveButton("Go to Settings")
            { _,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun setUpUi(){

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHE_RESPONSO_DATA, "")
        if(!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for (i in weatherList?.weather?.indices!!) {
                Log.i("Weather name", weatherList.weather.toString())

                binding?.tvMain?.text = weatherList.weather[i].main
                binding?.tvMainDescription?.text = weatherList.weather[i].description
                binding?.tvTemp?.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)
                binding?.tvHumidity?.text = weatherList.main.humidity.toString() + "per cent"
                binding?.tvMin?.text = weatherList.main.temp_min.toString() + "min"
                binding?.tvMax?.text = weatherList.main.temp_max.toString() + "max"
                binding?.tvSpeed?.text = weatherList.wind.speed.toString()
                binding?.tvName?.text = weatherList.name
                binding?.tvCountry?.text = weatherList.sys.country

                when (weatherList.weather[i].icon) {
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                    "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                }

            }
        }
    }

    private fun getUnit(value: String):String?{
        var value = "C"
        if("US" == value || "LR" == value || "MM" == value){
            value = "F"
        }
        return value
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}


