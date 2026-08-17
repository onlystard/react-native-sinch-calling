package com.sinchcalling

import com.facebook.react.bridge.ReactApplicationContext

class SinchCallingModule(reactContext: ReactApplicationContext) :
  NativeSinchCallingSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeSinchCallingSpec.NAME
  }
}
