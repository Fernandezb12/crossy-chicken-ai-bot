package com.crossybot.capture
import android.graphics.Bitmap
import android.os.SystemClock

data class Frame(val bitmap:Bitmap,val width:Int,val height:Int,val rotation:Int,val density:Int,val source:String,val at:Long=SystemClock.elapsedRealtime()){
 val key get()="$source-${width}x$height-r$rotation-d$density"
}
