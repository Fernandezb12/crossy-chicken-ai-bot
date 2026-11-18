package com.crossybot.ui
import android.app.*
import android.content.*
import android.graphics.Color
import android.media.projection.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import com.crossybot.Hub
import com.crossybot.capture.CaptureService
import com.crossybot.core.*

class MainActivity:Activity(){
 private lateinit var info:TextView;private lateinit var numbers:TextView
 private val handler=Handler(Looper.getMainLooper())
 private val refresh=object:Runnable{override fun run(){
  if(::info.isInitialized){info.text="${Hub.phase}\n${Hub.status}\nCaptura ${Hub.fps.toInt()} fps · análisis ${Hub.analysisMs} ms\nCaptura → análisis ${Hub.frameMs} ms · movimiento ${Hub.actionLatency} ms\nConfianza ${(100*Hub.confidence).toInt()}% · recomendación ${Hub.recommendation}"
   numbers.text="PUNTOS  ${Hub.currentScore?.toString()?:"—"}     RÉCORD OCR  ${Hub.journal.best}\nPARTIDAS  ${Hub.journal.games}     DERROTAS  ${Hub.journal.defeats}\nSUPERVIVENCIA  ${Hub.survivalMs/1000} s\n${Hub.profile?.let{if(it.ready())"Controles verificados" else "Calibración pendiente"}?:"Sin perfil"}"
  };handler.postDelayed(this,500)
 }}
 override fun onCreate(state:Bundle?){super.onCreate(state);Hub.init(this)
  val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(24),dp(20),dp(28));setBackgroundColor(Color.rgb(14,29,25))};scroll.addView(body);setContentView(scroll)
  scroll.setOnApplyWindowInsetsListener{v,insets->
   @Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets
  }
  fun text(s:String,size:Float=14f)=TextView(this).apply{text=s;textSize=size;setTextColor(Color.rgb(232,243,235));setPadding(0,dp(8),0,dp(8));body.addView(this)}
  fun button(s:String,run:()->Unit){body.addView(Button(this).apply{text=s;isAllCaps=false;setOnClickListener{run()}})}
  text("CROSSY CHICKEN",28f);text("AI BOT  /  ANDROID",14f)
  text("Control local con calibración visual. Versión experimental: agua y vías bloqueadas. Las partidas autónomas aún requieren validación en tu teléfono.")
  info=text("Preparando diagnóstico",13f);numbers=text("",16f)
  button("ABRIR JUEGO · integrado"){stopService(Intent(this,CaptureService::class.java));startActivity(Intent(this,GameActivity::class.java))}
  button("ABRIR JUEGO · Chrome"){
   val intent=Intent(Intent.ACTION_VIEW,Uri.parse("https://www.silvergames.com/es/crossy-chicken")).setPackage("com.android.chrome")
   try{startActivity(intent)}catch(e:ActivityNotFoundException){message("Chrome no está instalado. Usa el juego integrado.")}
  }
  button("ACTIVAR IA"){if(!Hub.activate())message(Hub.status)}
  button("PAUSAR IA"){Hub.pause()}
  button("DETENER IA Y CAPTURA"){Hub.stop();stopService(Intent(this,CaptureService::class.java))}
  button("CALIBRAR CONTROLES"){message("Dentro del juego integrado pulsa Calibrar. Para Chrome, activa captura y usa el panel flotante. Marca el área, el pollito, las casillas vecinas, suelo y los cuatro gestos. Después verifica cada dirección con Probar ↑ ↓ ← →.")}
  text("Modo de funcionamiento",19f)
  select(body,listOf("Manual · observar","Asistido · recomendar","Automático · ejecutar"),Hub.mode.ordinal){Hub.pause("Modo cambiado");Hub.mode=Mode.entries[it]}
  text("Estrategia",19f)
  select(body,listOf("Conservadora","Equilibrada","Agresiva","Adaptativa"),Hub.strategy.ordinal){Hub.strategy=Strategy.entries[it]}
  body.addView(Switch(this).apply{text="Reiniciar tras derrota (requiere prueba)";setTextColor(Color.WHITE);isChecked=Hub.autoRestart;setOnCheckedChangeListener{_,v->Hub.autoRestart=v}})
  text("Rendimiento",19f)
  select(body,listOf("5 fps · bajo consumo","10 fps","12 fps · normal","15 fps","20 fps · más CPU"),listOf(5,10,12,15,20).indexOf(Hub.captureFps).coerceAtLeast(0)){Hub.captureFps=listOf(5,10,12,15,20)[it]}
  text("Sensibilidad del reconocimiento",16f)
  select(body,listOf("Normal · 86% mínimo","Estricta · 90%","Muy estricta · 95%"),listOf(.86,.90,.95).indexOf(Hub.recognitionThreshold).coerceAtLeast(0)){Hub.recognitionThreshold=listOf(.86,.90,.95)[it]}
  text("Captura máxima: 640 px de ancho. Si el análisis supera 250 ms, las acciones se pausan. Las cifras de puntos provienen del OCR, nunca del número de toques.",12f)
  text("Permisos para Chrome",19f)
  button("1 · Habilitar accesibilidad"){
   AlertDialog.Builder(this).setTitle("Control táctil explícito").setMessage("Este servicio ejecuta los gestos calibrados cuando activas la IA. Comprueba la aplicación y la dirección visibles de Chrome. Puedes revocarlo en cualquier momento. No envía datos a servidores.")
    .setPositiveButton("Abrir Ajustes"){_,_->startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}.setNegativeButton("Cancelar",null).show()
  }
  button("2 · Permitir panel flotante"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
  button("3 · INICIAR CAPTURA DE CHROME"){
   if(com.crossybot.automation.TouchService.instance==null){message("Habilita antes el servicio de accesibilidad");return@button}
   if(!Settings.canDrawOverlays(this)){message("Permite el panel flotante para calibrar y detener desde Chrome");return@button}
   val pm=getSystemService(MediaProjectionManager::class.java)
   val consent=if(Build.VERSION.SDK_INT>=34)pm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())else pm.createScreenCaptureIntent()
   startActivityForResult(consent,10)
  }
  if(Build.VERSION.SDK_INT>=33)button("Permitir notificación de parada"){requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),12)}
  text("Datos de diagnóstico",19f)
  body.addView(Switch(this).apply{text="Guardar registros locales";setTextColor(Color.WHITE);isChecked=Hub.journal.enabled;setOnCheckedChangeListener{_,v->Hub.journal.enabled=v}})
  body.addView(Switch(this).apply{text="Guardar capturas para validación";setTextColor(Color.WHITE);isChecked=Hub.journal.screenshots;setOnCheckedChangeListener{_,v->Hub.journal.screenshots=v}})
  text("Desactivados inicialmente. Capturas: máximo 40; registro detallado: rotación a 5 MB. Exportación sólo por decisión tuya. El navegador accede a Silvergames; el detector y OCR funcionan localmente.",12f)
  button("Exportar diagnóstico ZIP"){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"crossy-diagnostico.zip"),20)}
  button("Borrar estadísticas y capturas"){AlertDialog.Builder(this).setMessage("¿Borrar los registros y estadísticas locales? Los perfiles se conservan.").setPositiveButton("Borrar"){_,_->Hub.journal.clear()}.setNegativeButton("Cancelar",null).show()}
 }
 private fun select(parent:LinearLayout,labels:List<String>,position:Int,change:(Int)->Unit){parent.addView(Spinner(this).apply{setBackgroundColor(Color.WHITE);adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,labels);setSelection(position);onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,i:Int,id:Long){change(i)}}})}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun message(s:String){AlertDialog.Builder(this).setMessage(s).setPositiveButton("Aceptar",null).show()}
 override fun onResume(){super.onResume();handler.post(refresh)}
 override fun onPause(){handler.removeCallbacks(refresh);super.onPause()}
 @Deprecated("Legacy result supported from API 26") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==10&&resultCode==RESULT_OK&&data!=null){startForegroundService(Intent(this,CaptureService::class.java).putExtra("code",resultCode).putExtra("data",data))}
  if(requestCode==20&&resultCode==RESULT_OK)data?.data?.let{uri->runCatching{contentResolver.openOutputStream(uri)?.use{Hub.journal.export(it)}}.onFailure{message("No se pudo exportar: ${it.message}")}}
 }
}
