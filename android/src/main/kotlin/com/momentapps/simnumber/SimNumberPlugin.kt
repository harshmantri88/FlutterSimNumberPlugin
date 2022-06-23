package com.momentapps.simnumber

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.BinaryMessenger;
import org.json.JSONArray


/** SimnumberPlugin */
class SimNumberPlugin: FlutterPlugin, ActivityAware, MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {
  private val REQUEST_READ_PHONE_STATE = 0
  private var permissionEvent: EventSink? = null
  private var applicationContext: Context? = null
  private var activity: Activity? = null
  private var telephonyManager: TelephonyManager? = null
  private var result: Result? = null
  private var methodChannel: MethodChannel? = null
  private var permissionEventChannel: EventChannel? = null


  companion object {
    fun registerWith(registrar: PluginRegistry.Registrar) {
      val instance = SimNumberPlugin()
      instance.onAttachedToEngine(registrar.context(), registrar.messenger(), registrar.activity())
    }
  }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPluginBinding) {
    this.onAttachedToEngine(
      flutterPluginBinding.applicationContext,
      flutterPluginBinding.binaryMessenger,
      null
    )
  }

  private fun onAttachedToEngine(
    applicationContext: Context,
    messenger: BinaryMessenger,
    _activity: Activity?
  ) {
    this.applicationContext = applicationContext
    MethodChannel(messenger, "sim_number").also { methodChannel = it }
      .setMethodCallHandler(this as MethodCallHandler)
    EventChannel(messenger, "phone_permission_event").also { permissionEventChannel = it }
      .setStreamHandler(object : EventChannel.StreamHandler {
        override fun onListen(o: Any, eventSink: EventSink) {
          this@SimNumberPlugin.permissionEvent = eventSink
        }
        override fun onCancel(o: Any) {}
      } as EventChannel.StreamHandler)
  }

  override fun onDetachedFromEngine(flutterPluginBinding: FlutterPluginBinding) {}


  @SuppressLint("WrongConstant")
  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    this.result = result
    when (call.method) {
      "getSimData" -> {
        Log.e("SADASDASDASDASD","Hello DATA")
        telephonyManager = applicationContext?.getSystemService("phone") as TelephonyManager
        this.getSimData()
      }
      "hasPhonePermission" -> {
        result.success(this.hasPhonePermission() as Any?)
      }
      "requestPhonePermission" -> {
        this.requestPhonePermission()
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun hasPhonePermission(): Boolean {
    return if (Build.VERSION.SDK_INT > 29) {
      ContextCompat.checkSelfPermission(
        applicationContext!!,
        "android.permission.READ_PHONE_NUMBERS"
      ) == 0
    } else ContextCompat.checkSelfPermission(
      applicationContext!!,
      "android.permission.READ_PHONE_STATE"
    ) == 0
  }

  private fun requestPhonePermission() {
    if (Build.VERSION.SDK_INT > 29) {
      if (!ActivityCompat.shouldShowRequestPermissionRationale(
          activity!!,
          "android.permission.READ_PHONE_NUMBERS",
        )
      ) {
        ActivityCompat.requestPermissions(
          activity!!,
          arrayOf("android.permission.READ_PHONE_NUMBERS"),
          REQUEST_READ_PHONE_STATE
        )
      }
    } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
        activity!!,
        "android.permission.READ_PHONE_STATE"
      )
    ) {
      ActivityCompat.requestPermissions(
        activity!!,
        arrayOf("android.permission.READ_PHONE_STATE"),
        REQUEST_READ_PHONE_STATE
      )
    }
  }

  private fun getSimData() {
    if (!hasPhonePermission()) {
      requestPhonePermission()
    } else {
      generateMobileNumber()
    }
  }

  private fun generateMobileNumber() {
    val simJsonArray = JSONArray()
    if (Build.VERSION.SDK_INT >= 22) {
      for (subscriptionInfo in getSubscriptions()) {
        val simCard = SimInfo(telephonyManager!!, subscriptionInfo)
        simJsonArray.put(simCard.toJSON() as Any)
      }
    }
    if (simJsonArray.length() == 0) {
      val simCard2: SimInfo? = getSingleSimCard()
      if (simCard2 != null) {
        simJsonArray.put(simCard2.toJSON() as Any)
      }
    }
    if (simJsonArray.toString().isEmpty()) {
      result!!.error("UNAVAILABLE", "No phone number on sim card", null as Any?)
    } else {
      result!!.success(simJsonArray.toString() as Any)
    }
  }

  private fun getSingleSimCard(): SimInfo? {
    if (ActivityCompat.checkSelfPermission(
        (activity as Context?)!!,
        "android.permission.READ_PHONE_NUMBERS"
      ) == -1 && ActivityCompat.checkSelfPermission(
        (activity as Context?)!!, "android.permission.READ_PHONE_STATE"
      ) == -1
    ) {
      Log.e("UNAVAILABLE", "No phone number on sim card Permission Denied#2", null as Throwable?)
      return null
    }
    return SimInfo(telephonyManager!!,null)
  }

  @SuppressLint("WrongConstant")
  @RequiresApi(api = 22)
  fun getSubscriptions(): List<SubscriptionInfo> {
    val subscriptionManager =
      activity!!.getSystemService("telephony_subscription_service") as SubscriptionManager
    if (ActivityCompat.checkSelfPermission(
        (activity as Context?)!!,
        "android.permission.READ_PHONE_NUMBERS"
      ) == -1 && ActivityCompat.checkSelfPermission(
        (activity as Context?)!!, "android.permission.READ_PHONE_STATE"
      ) == -1
    ) {
      Log.e("UNAVAILABLE", "No phone number on sim card Permission Denied#1", null as Throwable?)
      return ArrayList()
    }
    return subscriptionManager.activeSubscriptionInfoList as List<SubscriptionInfo>
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String?>,
    grantResults: IntArray
  ): Boolean {

    Log.e("SADASDASDASDASD","onRequestPermissionsResult 1")

    if (requestCode == REQUEST_READ_PHONE_STATE) {
      Log.e("SADASDASDASDASD","onRequestPermissionsResult 2")

      if (grantResults.isNotEmpty() && grantResults[0] == 0) {
        Log.e("SADASDASDASDASD","onRequestPermissionsResult 3")

        if (permissionEvent != null) {
          Log.e("SADASDASDASDASD","onRequestPermissionsResult 4")

          permissionEvent!!.success(true as Any)
        }
        Log.e("SADASDASDASDASD","onRequestPermissionsResult 5")

        generateMobileNumber()
        return true
      }
      if (permissionEvent != null) {
        Log.e("SADASDASDASDASD","onRequestPermissionsResult 6")
        permissionEvent!!.success(false as Any)
      }
    }
    result!!.error("PERMISSION", "onRequestPermissionsResult is not granted", null as Any?)
    return false
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
  }

  override fun onDetachedFromActivity() {
  }
}