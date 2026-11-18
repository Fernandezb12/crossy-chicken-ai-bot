package com.crossybot.capture
import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.hardware.display.*
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import com.crossybot.Hub
import com.crossybot.automation.TouchService
import com.crossybot.ui.CalibrationView
import com.crossybot.ui.MainActivity
import kotlin.math.roundToInt

class CaptureService:Service(){
 private var projection:MediaProjection?=null;private var display:VirtualDisplay?=null;private var reader:ImageReader?=null
 private val thread=HandlerThread("capture");private lateinit var handler:Handler
 private var panel:LinearLayout?=null;private var calibration:CalibrationView?=null
 private var width=0;private var height=0;private var lastAt=0L
 private val wm by lazy {getSystemService(WindowManager::class.java)}
 override fun onCreate(){super.onCreate();Hub.init(this);thread.start();handler=Handler(thread.looper)
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture","Captura del juego",NotificationManager.IMPORTANCE_LOW))
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="STOP"){Hub.stop();stopSelf();return START_NOT_STICKY}
  if(projection!=null)return START_NOT_STICKY
  val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,2,Intent(this,CaptureService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
  startForeground(7,Notification.Builder(this,"capture").setSmallIcon(com.crossybot.R.drawable.ic_bot).setContentTitle("Crossy Chicken · captura activa").setContentText("Procesamiento local. Pulsa DETENER para terminar.").setContentIntent(open).addAction(Notification.Action.Builder(null,"DETENER",stop).build()).setOngoing(true).build())
  try{
   @Suppress("DEPRECATION") val data=intent?.getParcelableExtra<Intent>("data")?:error("Falta consentimiento de captura")
   val code=intent.getIntExtra("code",Activity.RESULT_CANCELED);require(code==Activity.RESULT_OK)
   projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(code,data)
   val dm=android.util.DisplayMetrics();@Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm);width=dm.widthPixels;height=dm.heightPixels
   val cw=minOf(width,640);val ch=(height*cw.toDouble()/width).roundToInt()
   projection!!.registerCallback(object:MediaProjection.Callback(){override fun onStop(){Hub.pause("Captura revocada por Android");stopSelf()}
    override fun onCapturedContentResize(w:Int,h:Int){if(w!=width||h!=height){Hub.pause("Cambió el área capturada; vuelve a autorizar y calibrar");stopSelf()}}
   },Hub.main)
   reader=ImageReader.newInstance(cw,ch,PixelFormat.RGBA_8888,2)
   reader!!.setOnImageAvailableListener({r->
    val image=r.acquireLatestImage()?:return@setOnImageAvailableListener
    try{
     val now=SystemClock.elapsedRealtime();if(now-lastAt<1000/Hub.captureFps.coerceIn(5,20))return@setOnImageAvailableListener;lastAt=now
     val plane=image.planes[0];val rowPixels=plane.rowStride/plane.pixelStride
     val padded=Bitmap.createBitmap(rowPixels,ch,Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(plane.buffer)
     val bitmap=Bitmap.createBitmap(padded,0,0,cw,ch);if(bitmap!==padded)padded.recycle()
     @Suppress("DEPRECATION") val rotation=wm.defaultDisplay.rotation
     Hub.submit(Frame(bitmap,width,height,rotation,dm.densityDpi,"chrome",now))
    }finally{image.close()}
   },handler)
   display=projection!!.createVirtualDisplay("CrossyCapture",cw,ch,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
   Hub.focus={TouchService.instance?.allowed()==true}
   Hub.actuator={g,t,cb->TouchService.instance?.gesture(g,t,cb)?:cb(false)}
   if(Settings.canDrawOverlays(this))showPanel()
  }catch(e:Exception){Hub.pause("Error de captura: ${e.message}");stopSelf()}
  return START_NOT_STICKY
 }
 private fun params(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START}
 private fun showPanel(){
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(0xE614342B.toInt())}
  fun button(text:String,action:()->Unit){layout.addView(Button(this).apply{this.text=text;textSize=11f;setOnClickListener{action()}},LinearLayout.LayoutParams(115,65))}
  button("■ PARAR"){Hub.stop();stopSelf()}
  button("▶ IA"){Hub.activate()};button("Ⅱ Pausa"){Hub.pause()}
  button("Calibrar"){
   Hub.pause("Calibración");panel?.let{wm.removeView(it)};panel=null
   // Wait for the overlay to disappear from the next captured frame.
   Hub.main.postDelayed({val frame=Hub.snapshot();if(frame==null){showPanel();return@postDelayed}
    calibration=CalibrationView(this,frame){view->wm.removeView(view);calibration=null;showPanel()}
    wm.addView(calibration,params(-1,-1).apply{flags=flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN})
   },350)
  }
  button("Prueba ↑"){Hub.test(com.crossybot.core.Action.FORWARD)}
  button("Prueba ←"){Hub.test(com.crossybot.core.Action.LEFT)}
  button("Prueba →"){Hub.test(com.crossybot.core.Action.RIGHT)}
  button("Prueba ↓"){Hub.test(com.crossybot.core.Action.BACK)}
  button("Inicio"){Hub.testStart()}
  button("Reinicio"){Hub.testRestart()}
  panel=layout;wm.addView(layout,params(115,-2))
 }
 override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);Hub.pause("Orientación modificada: vuelve a iniciar la captura");stopSelf()}
 override fun onDestroy(){
  Hub.stop();panel?.let{runCatching{wm.removeView(it)}};calibration?.let{runCatching{wm.removeView(it)}}
  reader?.setOnImageAvailableListener(null,null);display?.release();display=null;reader?.close();reader=null
  val mp=projection;projection=null;mp?.stop();thread.quitSafely();super.onDestroy()
 }
 override fun onBind(intent:Intent?)=null
}
