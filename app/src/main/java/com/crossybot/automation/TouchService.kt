package com.crossybot.automation
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.crossybot.Hub
import com.crossybot.storage.Gesture

class TouchService:AccessibilityService(){
 companion object { @Volatile var instance:TouchService?=null }
 private var approvedUrl=false;private var gestureBusy=false
 override fun onServiceConnected(){super.onServiceConnected();instance=this;Hub.init(this)}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){
  val root=rootInActiveWindow?:run{approvedUrl=false;return}
  if(root.packageName?.toString()!="com.android.chrome"){
   approvedUrl=false
   if(Hub.profile?.key?.startsWith("chrome")==true&&Hub.running)Hub.pause("Chrome perdió el enfoque")
   return
  }
  val addresses=root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
  if(addresses.isNotEmpty())approvedUrl=addresses.any{node->
   val raw=node.text?.toString().orEmpty();val uri=android.net.Uri.parse(if(raw.startsWith("http"))raw else "https://$raw")
   uri.host in setOf("www.silvergames.com","silvergames.com")&&uri.path?.contains("/crossy-chicken")==true
  }
 }
 fun allowed()=approvedUrl&&rootInActiveWindow?.packageName?.toString()=="com.android.chrome"
 fun gesture(g:Gesture,token:Int,done:(Boolean)->Unit){
  val frame=Hub.snapshot()?:run{done(false);return};val p=Hub.profile?:run{frame.bitmap.recycle();done(false);return}
  val start=p.game.point(g.start);val end=p.game.point(g.end);val w=frame.width;val h=frame.height;frame.bitmap.recycle()
  if(gestureBusy||!allowed()||!Hub.valid(token)||!p.game.contains(start)||!p.game.contains(end)){done(false);return}
  val path=Path().apply{moveTo((start.x*w).toFloat(),(start.y*h).toFloat());if((end-start).norm()>.002)lineTo((end.x*w).toFloat(),(end.y*h).toFloat())}
  val d=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,g.holdMs.coerceIn(30,1000))).build()
  gestureBusy=true
  val accepted=dispatchGesture(d,object:GestureResultCallback(){override fun onCompleted(gestureDescription:GestureDescription?){gestureBusy=false;done(true)};override fun onCancelled(gestureDescription:GestureDescription?){gestureBusy=false;done(false)}},null)
  if(!accepted){gestureBusy=false;done(false)}
 }
 override fun onInterrupt(){Hub.pause("Servicio de accesibilidad interrumpido")}
 override fun onDestroy(){instance=null;Hub.pause("Accesibilidad desactivada");super.onDestroy()}
}
