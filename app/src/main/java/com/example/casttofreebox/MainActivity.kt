package com.example.casttofreebox

import android.content.res.ColorStateList
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.casttofreebox.databinding.ActivityMainBinding
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.ServerSocket

const val URL = "https://gogodownload.net/download.php?url=aHR0cHM6LyAdeqwrwedffryretgsdFrsftrsvfsfsr8xN2M3Y2AawehyfcghysfdsDGDYdgdsfsdfwstdgdsgtertQ0b2xkLmdvZjYyMzU0LmNvbS91c2VyMTM0Mi83YmQ5NjQ1MGNjMWJkYmM4M2U1YzE1YTI3ZDQ2ZmQ5MS9FUC4xLnYxLjE2MzkxODI4MjMuMTA4MHAubXA0P3Rva2VuPXpuZU9uZUY4cUNXMGJPOU5zZlIyNEEmZXhwaXJlcz0xNjkwMjIyNzI4JmlkPTIyNDY1"

class MainActivity : AppCompatActivity() {

    lateinit var ids: ActivityMainBinding       //View binding
    var nsdService: NSDService? = null
    val httpService = HttpService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ids = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ids.root)

        activateButton(ids.ibCast)
        deactivateButton(ids.ibLink)
        deactivateButton(ids.ibCheck)
        deactivateButton(ids.ibPlay)
        deactivateButton(ids.ibStop)
        ids.ibCast.setOnClickListener { startService() }
        ids.ibLink.setOnClickListener { connectService() }
        ids.ibCheck.setOnClickListener { checkDevices() }
        ids.ibPlay.setOnClickListener { playVideo(URL) }
        ids.ibStop.setOnClickListener { stopVideo() }
    }

    private fun deactivateButton(button: ImageButton) {
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.inactiveButton))
        button.isClickable = false
        button.isFocusable = false
    }
    private fun activateButton(button: ImageButton) {
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.activeButton))
        button.isClickable = true
        button.isFocusable = true
    }

    private fun startService() {
        GlobalScope.launch(Dispatchers.Main) {
            if(httpService.httpSearchFreebox()) {
                httpService.getAppToken()
                Toast.makeText(this@MainActivity, "Confirm connection on Freebox (you have 1min30)", Toast.LENGTH_LONG).show()
                activateButton(ids.ibLink)
                deactivateButton(ids.ibCast)
            }
        }
    }

    private fun connectService() {
        GlobalScope.launch(Dispatchers.Main) {
            when(httpService.appTokenValid()) {
                -1 -> { deactivateButton(ids.ibLink); activateButton(ids.ibCast); Toast.makeText(this@MainActivity, "App Token is Invalid", Toast.LENGTH_SHORT).show() }
                0 -> Toast.makeText(this@MainActivity, "App Token is Pending !!", Toast.LENGTH_SHORT).show()
                1 -> {
                    deactivateButton(ids.ibLink)
                    activateButton(ids.ibCast)
                    if(httpService.getSessionToken()) {
                        Toast.makeText(this@MainActivity, "Service connected !", Toast.LENGTH_SHORT).show()
                        ids.ibCast.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.cast_connected))
                        ids.ibCast.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.lightActiveButton))
                        ids.ibCast.setOnClickListener { disconnectService() }
                        activateButton(ids.ibCheck)
                        activateButton(ids.ibPlay)
                        activateButton(ids.ibStop)
                    } else {
                        Toast.makeText(this@MainActivity, "Error during connection", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun disconnectService() {
        GlobalScope.launch(Dispatchers.Main) {
            if (httpService.logout()) {
                Toast.makeText(this@MainActivity, "Service disconnected !", Toast.LENGTH_SHORT)
                    .show()
                ids.ibCast.setImageDrawable(
                    ContextCompat.getDrawable(
                        this@MainActivity,
                        R.drawable.cast
                    )
                )
                ids.ibCast.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@MainActivity,
                        R.color.activeButton
                    )
                )
                ids.ibCast.setOnClickListener { startService() }
                deactivateButton(ids.ibCheck)
                deactivateButton(ids.ibPlay)
                deactivateButton(ids.ibStop)
            } else {
                Toast.makeText(this@MainActivity, "Service logout failed", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun checkDevices() {
        GlobalScope.launch(Dispatchers.Main) {
            httpService.getRecieversAirmedia()
        }
    }

    private fun playVideo(url: String) {
        GlobalScope.launch(Dispatchers.Main) {
            httpService.playVideo(url)
        }

    }

    private fun stopVideo() {
        GlobalScope.launch(Dispatchers.Main) {
            httpService.stopVideo()
        }
    }




}