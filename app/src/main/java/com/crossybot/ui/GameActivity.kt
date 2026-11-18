package com.crossybot.ui
import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.webkit.*
import android.widget.*
import com.crossybot.Hub
import com.crossybot.capture.Frame
import com.crossybot.core.Action
import com.crossybot.storage.Gesture
import kotlin.math.roundToInt

class GameActivity:Activity(){
 private lateinit var web:WebView;private lateinit var root:LinearLayout;private lateinit var status:TextView
 private var active=false;private var copying=false;private var calibrating=false
 private var downTime=0L;private var touching=false
 private val handler=Handler(Looper.getMainLooper())
 private val pump=object:Runnable{override fun run(){if(active){capture();status.text="${Hub.phase} · ${Hub.status}\n${Hub.fps.toInt()} fps · ${Hub.analysisMs} ms · confianza ${(Hub.confidence*100).toInt()}%";handler.postDelayed(this,(1000/Hub.captureFps.coerceIn(5,20)).toLong())}}}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);Hub.init(this);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(20,52,43))};setContentView(root)
  root.setOnApplyWindowInsetsListener{v,insets->
   @Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets
  }
  fun row(items:List<Pair<String,()->Unit>>){val line=LinearLayout(this);root.addView(line);items.forEach{(label,run)->line.addView(Button(this).apply{text=label;textSize=11f;minWidth=0;setPadding(0,0,0,0);setOnClickListener{run()}},LinearLayout.LayoutParams(0,dp(42),1f))}}
  row(listOf("← Inicio" to {finish()},"Calibrar" to {calibrate()},"▶ IA" to {Hub.activate();Unit},"Ⅱ" to {Hub.pause()},"■" to {Hub.pause("Detenido por el usuario")}))
  row(listOf("Probar ↑" to {Hub.test(Action.FORWARD);Unit},"↓" to {Hub.test(Action.BACK);Unit},"←" to {Hub.test(Action.LEFT);Unit},"→" to {Hub.test(Action.RIGHT);Unit},"Inicio" to {Hub.testStart()},"Reinicio" to {Hub.testRestart()}))
  status=TextView(this).apply{setTextColor(Color.WHITE);textSize=11f;setPadding(8,0,8,0)};root.addView(status,LinearLayout.LayoutParams(-1,dp(40)))
  web=WebView(this);root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true;web.settings.mediaPlaybackRequiresUserGesture=true
  web.settings.allowFileAccess=false;web.settings.allowContentAccess=false;web.settings.setSupportMultipleWindows(false)
  web.webChromeClient=WebChromeClient()
  web.webViewClient=object:WebViewClient(){
   override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
    if(!request.isForMainFrame)return false
    val u=request.url;return if(u.scheme!="https"||u.host !in setOf("www.silvergames.com","media.silvergames.com")){Hub.pause("Enlace externo: automatización pausada");true}else false
   }
   override fun onPageStarted(view:WebView,url:String,favicon:Bitmap?){Hub.pause("Cargando juego")}
   override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame)Hub.pause("WebView: ${error.description}")}
  }
  // Same provider gameframe observed on the requested Silvergames page. No game code is injected.
  web.loadUrl("https://www.silvergames.com/es/crossy-chicken/gameframe")
  web.setOnTouchListener{_,event->if(event.actionMasked==MotionEvent.ACTION_DOWN&&Hub.running&&Hub.recordingTest==null)Hub.pause("Control manual detectado");false}
 }
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun attach(){Hub.focus={active&&!calibrating&&hasWindowFocus()&&web.url?.contains("silvergames.com/")==true};Hub.actuator={g,t,done->gesture(g,t,done)}}
 override fun onResume(){super.onResume();active=true;web.onResume();attach();handler.post(pump)}
 override fun onPause(){active=false;Hub.pause("Juego en segundo plano");cancelTouch();handler.removeCallbacks(pump);web.onPause();super.onPause()}
 override fun onDestroy(){handler.removeCallbacksAndMessages(null);Hub.actuator=null;Hub.focus={false};web.destroy();super.onDestroy()}
 override fun onConfigurationChanged(newConfig:android.content.res.Configuration){super.onConfigurationChanged(newConfig);Hub.pause("Orientación nueva: recalibra este perfil")}
 private fun capture(){
  if(copying||calibrating||web.width<2||web.height<2)return
  copying=true;val w=web.width;val h=web.height;val bw=minOf(w,640);val b=Bitmap.createBitmap(bw,(h*bw.toDouble()/w).roundToInt(),Bitmap.Config.ARGB_8888)
  val pos=IntArray(2);web.getLocationInWindow(pos);val rect=Rect(pos[0],pos[1],pos[0]+w,pos[1]+h);val time=SystemClock.elapsedRealtime()
  try{PixelCopy.request(window,rect,b,{result->copying=false;if(result==PixelCopy.SUCCESS&&active&&!calibrating){
   @Suppress("DEPRECATION") val rotation=windowManager.defaultDisplay.rotation
   Hub.submit(Frame(b,w,h,rotation,resources.displayMetrics.densityDpi,"webview",time))
  }else b.recycle()},handler)}catch(e:Exception){copying=false;b.recycle();Hub.pause("Captura WebView: ${e.message}")}
 }
 private fun calibrate(){
  Hub.pause("Calibrando controles");val frame=Hub.snapshot()?:run{Toast.makeText(this,"Espera a que cargue una captura",Toast.LENGTH_SHORT).show();return}
  calibrating=true
  val dialog=Dialog(this,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
  val view=CalibrationView(this,frame){dialog.dismiss()};dialog.setContentView(view);dialog.setCancelable(false)
  dialog.setOnDismissListener{calibrating=false;attach()};dialog.show()
 }
 private fun event(action:Int,x:Float,y:Float){val e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
  // Dispatch directly to the view's event handler; do not mark generated gestures as manual.
  web.onTouchEvent(e);e.recycle()
 }
 private fun cancelTouch(){if(touching){event(MotionEvent.ACTION_CANCEL,0f,0f);touching=false}}
 private fun gesture(g:Gesture,token:Int,done:(Boolean)->Unit){
  val p=Hub.profile?:run{done(false);return};if(!Hub.valid(token)||touching){done(false);return}
  val a=p.game.point(g.start);val b=p.game.point(g.end);downTime=SystemClock.uptimeMillis();touching=true;event(MotionEvent.ACTION_DOWN,(a.x*web.width).toFloat(),(a.y*web.height).toFloat())
  val steps=if((a-b).norm()<.003)1 else 6
  fun step(i:Int){handler.postDelayed({
   if(!Hub.valid(token)||!active){cancelTouch();done(false);return@postDelayed}
   val v=a+(b-a)*(i.toDouble()/steps);event(if(i==steps)MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE,(v.x*web.width).toFloat(),(v.y*web.height).toFloat())
   if(i==steps){touching=false;done(true)}else step(i+1)
  },g.holdMs.coerceIn(40,1000)/steps)}
  step(1)
 }
}
