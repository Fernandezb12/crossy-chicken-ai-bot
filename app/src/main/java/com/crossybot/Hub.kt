package com.crossybot

import android.content.Context
import android.graphics.Bitmap
import android.os.*
import com.crossybot.capture.Frame
import com.crossybot.core.*
import com.crossybot.storage.*
import com.crossybot.vision.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Owns a single nonqueued capture -> decision pipeline. UI never supplies inferred game state. */
object Hub {
 lateinit var context:Context;private set
 lateinit var store:ProfileStore;private set
 lateinit var journal:Journal;private set
 private lateinit var vision:Vision
 private val executor=Executors.newSingleThreadExecutor()
 private val busy=AtomicBoolean(false);private val epoch=AtomicInteger()
 val main=Handler(Looper.getMainLooper())
 private val gate=MotionGate();private val planner=Planner();private val learning=AdaptiveTiming()
 private var last:Frame?=null
 @Volatile var profile:Profile?=null
 @Volatile var running=false;private set
 @Volatile var mode=Mode.MANUAL
 @Volatile var strategy=Strategy.BALANCED
 @Volatile var autoRestart=false
 @Volatile var status="Captura detenida";private set
 @Volatile var phase=Phase.PAUSED;private set
 @Volatile var currentScore:Int?=null;private set
 @Volatile var analysisMs=0L;private set
 @Volatile var frameMs=0L;private set
 @Volatile var actionLatency=0L;private set
 @Volatile var fps=0.0;private set
 @Volatile var confidence=0.0;private set
 @Volatile var recommendation=Action.WAIT;private set
 @Volatile var survivalMs=0L;private set
 @Volatile var recordingTest:Action?=null;private set
 @Volatile var captureFps=12
 @Volatile var recognitionThreshold=.86
 @Volatile var actuator:((Gesture,Int,(Boolean)->Unit)->Unit)?=null
 @Volatile var focus:()->Boolean={false}
 private var camera=V(0.0,0.0);private var player:V?=null
 @Volatile private var previousTime=0L;private var started=0L;private var lastMove=0L;private var deadCount=0;private var counted=false
 private var restartingAt=0L;private var restartTest=false;private var restartObserved=0;private var menuCount=0;private var startSent=false;private var startTest=false
 private var scoreAt=0L;private var scoreCandidate:Int?=null;private var scoreGeneration=0
 private val ocrBusy=AtomicBoolean(false)
 private val recognizer by lazy {TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)}
 fun init(c:Context){if(::context.isInitialized)return;context=c.applicationContext;store=ProfileStore(context);journal=Journal(context);vision=Vision(store)
  context.getSharedPreferences("learning",0).let{learning.restore(it.getLong("latency",350),it.getInt("samples",0),it.getFloat("margin",.38f).toDouble())}
  org.opencv.android.OpenCVLoader.initLocal().also{if(!it)status="Error al cargar OpenCV"}
  main.post(object:Runnable{override fun run(){if(running&&previousTime>0&&SystemClock.elapsedRealtime()-previousTime>1500)pause("Sin fotogramas recientes");main.postDelayed(this,500)}})
 }
 fun token()=epoch.get()
 fun valid(token:Int)=running&&token==epoch.get()&&focus()
 @Synchronized fun snapshot():Frame?=last?.let{it.copy(bitmap=it.bitmap.copy(Bitmap.Config.ARGB_8888,false))}
 @Synchronized fun reload(p:Profile){pause("Perfil guardado; prueba los controles");profile=p;store.save(p);vision.close()}
 @Synchronized fun activate():Boolean {
  if(gate.pending!=null||restartingAt>0){status="Espera el resultado de la acción pendiente";return false}
  if(actuator==null||!focus()){status="Abre el juego y activa la captura";return false}
  val p=profile?:run{status="Calibra sobre una captura del juego";return false}
  if(mode==Mode.AUTOMATIC&&!p.ready()){status="Falta calibrar y verificar los cuatro controles";return false}
  epoch.incrementAndGet();gate.reset();running=true;phase=Phase.LOADING;status="Esperando reconocimiento";return true
 }
 @Synchronized fun pause(reason:String="Pausado por el usuario"){
  epoch.incrementAndGet();running=false;gate.cancel();recordingTest=null;restartingAt=0;restartTest=false;startTest=false;startSent=false;phase=Phase.PAUSED;status=reason;journal.event("pause",mapOf("reason" to reason));
 }
 @Synchronized fun stop(){pause("Detenido");actuator=null;focus={false};started=0;survivalMs=0}
 @Synchronized fun test(a:Action):Boolean {
  if(gate.pending!=null||restartingAt>0){status="Espera el resultado de la acción pendiente";return false}
  val p=profile?:return false
  if(!p.validGeometry()||p.controls[a]==null||player==null||confidence<recognitionThreshold||!focus()){status="Necesita geometría, gesto y pollito reconocido para la prueba";return false}
  epoch.incrementAndGet();gate.reset();running=true;recordingTest=a;camera=V(0.0,0.0)
  send(a,p);return gate.pending!=null
 }
 @Synchronized fun testRestart(){if(gate.pending!=null||restartingAt>0)return;val p=profile?:return;if(p.restart==null||deadCount<3||!focus()){status="Captura primero una derrota estable y calibra Reiniciar";return};running=true;epoch.incrementAndGet();restartTest=true;restart(p)}
 @Synchronized fun testStart(){
  if(gate.pending!=null||restartingAt>0)return
  val p=profile?:return
  if(p.start==null||p.score==null||menuCount<3||!focus()){status="Calibra menú, iniciar y marcador OCR; espera un menú estable";return}
  running=true;epoch.incrementAndGet();startTest=true;startFromMenu(p)
 }
 private fun startFromMenu(p:Profile){
  if(startSent)return
  val g=p.start?:run{pause("Falta calibrar Inicio");return}
  if(p.score==null){pause("Falta el marcador para verificar inicio");return}
  startSent=true;restartingAt=SystemClock.elapsedRealtime();restartObserved=0;currentScore=null;scoreCandidate=null;scoreGeneration++;phase=Phase.RESTARTING
  dispatch(g){ok->if(!ok)pause("Inicio rechazado por Android")}
 }
 private fun saveLearning(){context.getSharedPreferences("learning",0).edit().putLong("latency",learning.jumpMs).putInt("samples",learning.samples).putFloat("margin",learning.uncertainty.toFloat()).apply()}
 private fun send(a:Action,p:Profile){
  val from=player?:return;val g=p.controls[a]?:return
  val now=SystemClock.elapsedRealtime();if(!gate.begin(a,now,from,p.vector(a),p.jumpMs))return
  camera=V(0.0,0.0);phase=Phase.MOVING
  journal.event("action",mapOf("action" to a.name,"confidence" to confidence,"jumpMs" to p.jumpMs,"strategy" to strategy.name,"score" to currentScore,"playerX" to from.x,"playerY" to from.y,"sinceLastActionMs" to (now-lastMove),"analysisMs" to analysisMs,"frameAgeMs" to frameMs))
  snapshot()?.let{journal.snapshot(it.bitmap,"before-${a.name}");it.bitmap.recycle()}
  dispatch(g){ok->if(!ok)pause("Android rechazó o canceló el gesto")}
 }
 private fun dispatch(g:Gesture,done:(Boolean)->Unit){
  val t=token();main.post{
   if(!valid(t)){done(false);return@post}
   // Apply the same freshness rule to menu, restart and calibration gestures.
   if(SystemClock.elapsedRealtime()-previousTime !in 0..250){pause("Fotograma obsoleto al enviar el gesto");return@post}
   actuator?.invoke(g,t,done)?:done(false)
  }
 }
 private fun restart(p:Profile){
  if(restartingAt!=0L)return
  val g=p.restart?:return;currentScore=null;scoreCandidate=null;scoreGeneration++;restartingAt=SystemClock.elapsedRealtime();restartObserved=0;startSent=false;phase=Phase.RESTARTING
  dispatch(g){ok->if(!ok){restartingAt=0;pause("No se pudo reiniciar")}}
 }
 fun submit(f:Frame){
  if(!busy.compareAndSet(false,true)){f.bitmap.recycle();return}
  executor.execute{try{process(f)}catch(e:Exception){pause("Error de análisis: ${e.javaClass.simpleName}: ${e.message}")}
   finally{f.bitmap.recycle();busy.set(false)}}
 }
 @Synchronized private fun process(f:Frame){
  last?.bitmap?.recycle();last=f.copy(bitmap=f.bitmap.copy(Bitmap.Config.ARGB_8888,false))
  if(previousTime>0&&f.at>previousTime)fps=1000.0/(f.at-previousTime);previousTime=f.at
  if(profile?.key!=f.key){pause("Resolución o fuente nueva: selecciona/calibra su perfil");profile=store.load(f.key);vision.close();player=null}
  val p=profile?:run{status="Captura activa (${f.width}×${f.height}); falta calibrar";return}
  val observation=vision.analyze(f,p);confidence=observation.confidence;analysisMs=observation.analysisMs;frameMs=SystemClock.elapsedRealtime()-f.at
  camera+=observation.camera;player=observation.player
  if(observation.dead){deadCount++}else deadCount=0
  if(observation.menu)menuCount++ else menuCount=0
  if(deadCount>=3){
   phase=Phase.DEAD
   if(!counted&&started>0){journal.finish(currentScore,f.at-started,"No determinada; derrota reconocida visualmente");counted=true;learning.record(0,false);saveLearning();gate.cancel();recordingTest=null}
   status="Derrota reconocida";if(running&&mode==Mode.AUTOMATIC&&autoRestart&&p.restart?.verified==true)restart(p)
  }
  if(restartingAt>0){
   if(!running){restartingAt=0;return}
   if(f.at-restartingAt>8000){pause("Transición sin respuesta: intervención necesaria");return}
   if(menuCount>=3&&!startSent){
    if(p.start?.verified==true)startFromMenu(p) else pause("Reinicio llegó al menú: calibra y prueba Inicio")
    return
   }
   if(!observation.menu&&!observation.dead)readScore(f,p)
   if(observation.scene!=null&&observation.confidence>.86&&currentScore!=null)restartObserved++ else restartObserved=0
   if(restartObserved>=3){
    val wasTest=restartTest||startTest
    if(restartTest)p.restart?.verified=true
    if(startTest)p.start?.verified=true
    if(wasTest)store.save(p)
    restartingAt=0;startSent=false;restartTest=false;startTest=false;counted=false;started=f.at;planner.reset();gate.reset();phase=Phase.PLAYING
    if(wasTest)pause("Transición a partida verificada: pollo + puntuación nuevos")
   }
   return
  }
  if(menuCount>=3){
   phase=Phase.MENU;status="Menú reconocido"
   if(running&&mode==Mode.AUTOMATIC){
    if(p.start?.verified==true)startFromMenu(p) else pause("Calibra y prueba Inicio antes de automatizar el menú")
   }
   return
  }
  if(deadCount>0)return
  if(observation.scene!=null){
   if(started==0L){started=f.at;counted=false};survivalMs=f.at-started;readScore(f,p)
  }
  if(!running){status=if(phase==Phase.PAUSED)status else observation.reason;return}
  if(!focus()){pause("El juego perdió el enfoque");return}
  if(frameMs>250){pause("Análisis demasiado lento (${frameMs} ms)");return}
  val pending=gate.pending
  if(pending!=null){
   when(gate.observe(f.at,player?:pending.origin,camera,if(confidence>=recognitionThreshold)confidence else 0.0)){
    MotionGate.Result.ACCEPTED->{
     actionLatency=f.at-pending.start;learning.record(actionLatency,true);saveLearning();planner.accepted(pending.action);lastMove=f.at;phase=Phase.PLAYING
     journal.event("result",mapOf("action" to pending.action.name,"accepted" to true,"latencyMs" to actionLatency,"score" to currentScore,"survived" to true))
     if(recordingTest!=null){p.controls[recordingTest]?.verified=true;p.jumpMs=actionLatency.coerceIn(100,1500);store.save(p);pause("${pending.action}: movimiento verificado en dos fotogramas");return}
    }
    MotionGate.Result.FAILED->{learning.record(0,false);saveLearning();journal.event("result",mapOf("action" to pending.action.name,"accepted" to false));pause("Movimiento sin respuesta verificable. Recalibra antes de continuar");return}
    else->return
   }
  }
  val scene=observation.scene
  if(scene==null||confidence<recognitionThreshold){status=observation.reason;if(lastMove>0&&f.at-lastMove>1500)pause("Reconocimiento perdido; no se enviaron más movimientos");return}
  phase=Phase.PLAYING
  planner.adaptiveMargin=learning.uncertainty
  journal.event("scene",mapOf("at" to scene.timeMs,"confidence" to scene.confidence,"hazards" to scene.hazards.joinToString(";"){"${it.id},${it.position.x},${it.position.y},${it.velocity.x},${it.velocity.y},${it.uncertainty}"},"terrain" to scene.cells.joinToString(";"){"${it.x},${it.y},${it.terrain}"}))
  val choice=planner.choose(scene,SystemClock.elapsedRealtime(),p.jumpMs,strategy,if(lastMove==0L)0 else f.at-lastMove)
  recommendation=choice.action;status="${choice.action}: ${choice.reason}"
  if(recordingTest==null&&mode==Mode.AUTOMATIC&&choice.action!=Action.WAIT&&p.ready())send(choice.action,p)
 }
 private fun readScore(f:Frame,p:Profile){
  val box=p.score?:return;if(f.at-scoreAt<900||!ocrBusy.compareAndSet(false,true))return;scoreAt=f.at
  val x=(box.l*f.bitmap.width).toInt().coerceIn(0,f.bitmap.width-1);val y=(box.t*f.bitmap.height).toInt().coerceIn(0,f.bitmap.height-1)
  val w=((box.r-box.l)*f.bitmap.width).toInt().coerceIn(1,f.bitmap.width-x);val h=((box.b-box.t)*f.bitmap.height).toInt().coerceIn(1,f.bitmap.height-y)
  val b=Bitmap.createBitmap(f.bitmap,x,y,w,h).copy(Bitmap.Config.ARGB_8888,false);val sessionStarted=started;val generation=scoreGeneration
  recognizer.process(InputImage.fromBitmap(b,0)).addOnSuccessListener{result->
   synchronized(this){if(started==sessionStarted&&scoreGeneration==generation){
    val text=result.text.trim();val n=if(text.matches(Regex("[0-9]{1,6}")))text.toIntOrNull() else null
    if(n!=null&&(currentScore==null||n in currentScore!!..currentScore!!+30)){
     if(scoreCandidate==n)currentScore=n;scoreCandidate=n
    }
   }}
  }.addOnCompleteListener{b.recycle();ocrBusy.set(false)}
 }
}
