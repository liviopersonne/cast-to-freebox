package com.example.casttofreebox

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdManager.ResolveListener
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import java.net.ServerSocket

class NSDService(private val context: Context) {
    init {
        registerService()
    }

    lateinit var serviceInfo: NsdServiceInfo
    var nsdManager: NsdManager? = null
    lateinit var registrationListener: RegistrationListener
    lateinit var discoveryListener: DiscoveryListener
    lateinit var resolveListener: ResolveListener


    override fun toString(): String {
        return "<NSD Service on port ${serviceInfo.port}>: [NSD Manager: $nsdManager]"
    }

    // Initialize a server socket on an available port.
    private fun availableSocket(): Int {
        val mLocalPort: Int
        ServerSocket(0).also { socket ->
            mLocalPort = socket.localPort
        }
        return mLocalPort
    }

    private fun registerService(): Boolean {

        serviceInfo = NsdServiceInfo().apply {
            serviceName = "Cast to Freebox Service"
            serviceType = "_fbx-api._tcp"
            port = availableSocket()
        }

        registrationListener = object : RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                //Set real name (if conflicts android can change "service" -> "service (1)"
                serviceInfo.serviceName = NsdServiceInfo.serviceName
                Log.d("MainActivity", "Service registered with name ${serviceInfo.serviceName}")
                nsdManager!!.discoverServices(serviceInfo.serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.d("MainActivity", "Error during registration: Code = $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                // Service has been unregistered. This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                Log.d("MainActivity", "Service has been unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.d("MainActivity", "Error during unregistration: Code = $errorCode")
            }
        }

        nsdManager = (getSystemService(context, NsdManager::class.java))?.apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
        if(nsdManager == null) { return false }

        discoveryListener = object : DiscoveryListener {
            // Called as soon as service discovery begins.
            override fun onDiscoveryStarted(regType: String) {
                Log.d("MainActivity", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("MainActivity", "Service found:")
                when {
                    service.serviceType !in listOf("_fbx-api._tcp", "_fbx-api._tcp.") ->
                        Log.d("MainActivity", "   Unknown service type: ${service.serviceType} on machine: ${service.serviceName}")
                    service.serviceName == serviceInfo.serviceName ->
                        Log.d("MainActivity", "   You found yourself :)")
                    else -> {
                        Log.d("MainActivity", "   Found potential service: ${service.serviceName}")
                        Log.d("MainActivity", "   ${service.attributes}")
                        //nsdManager?.resolveService(service, resolveListener)
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e("MainActivity", "Service lost: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("MainActivity", "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("MainActivity", "Discovery failed: Error code:$errorCode")
                nsdManager?.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("MainActivity", "Discovery failed: Error code:$errorCode")
                nsdManager?.stopServiceDiscovery(this)
            }
        }



        return true
    }
}