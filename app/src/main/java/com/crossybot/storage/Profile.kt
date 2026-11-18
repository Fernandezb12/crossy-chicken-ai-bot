package com.crossybot.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.crossybot.core.*
import org.json.*
import java.io.File

data class Gesture(val start:V,val end:V,val holdMs:Long=80,var verified:Boolean=false)
data class Profile(
 val key:String,
 var game:Box=Box(0.0,0.0,1.0,1.0),
 var player:Box?=null,
 var forward:V=V(0.0,0.0),var right:V=V(0.0,0.0),
 var score:Box?=null,var death:Box?=null,var anchor:Box?=null,
 var restart:Gesture?=null,
 var menu:Box?=null,var start:Gesture?=null,
 var jumpMs:Long=350,
 val controls:MutableMap<Action,Gesture> = mutableMapOf(),
 val colors:MutableMap<Terrain,Int> = mutableMapOf()
){
 fun validGeometry():Boolean {
  val det=forward.x*right.y-forward.y*right.x
  return game.valid()&&game.l>=0&&game.t>=0&&game.r<=1&&game.b<=1&&player!=null&&
   forward.norm()>.01&&right.norm()>.01&&kotlin.math.abs(det)>.0001&&colors.containsKey(Terrain.GROUND)
 }
 fun ready()=validGeometry()&&Action.entries.filter{it!=Action.WAIT}.all{controls[it]?.verified==true}
 fun vector(a:Action)=forward*a.dy.toDouble()+right*a.dx.toDouble()
}
class ProfileStore(private val context:Context){
 private val dir=File(context.filesDir,"profiles").apply{mkdirs()}
 private fun stem(key:String)=key.replace(Regex("[^a-zA-Z0-9_-]"),"_")
 private fun file(key:String,suffix:String)=File(dir,"${stem(key)}.$suffix")
 fun saveBitmap(key:String,label:String,b:Bitmap){file(key,"$label.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
 fun bitmap(key:String,label:String):Bitmap?=BitmapFactory.decodeFile(file(key,"$label.png").path)
 fun load(key:String):Profile?=runCatching{
  val j=JSONObject(file(key,"json").readText());val p=Profile(key)
  fun box(name:String):Box?=j.optJSONArray(name)?.let{Box(it.getDouble(0),it.getDouble(1),it.getDouble(2),it.getDouble(3))}
  fun v(name:String)=j.getJSONArray(name).let{V(it.getDouble(0),it.getDouble(1))}
  fun gesture(o:JSONObject)=Gesture(V(o.getDouble("x"),o.getDouble("y")),V(o.getDouble("ex"),o.getDouble("ey")),o.getLong("ms"),o.optBoolean("verified"))
  p.game=box("game")!!;p.player=box("player");p.forward=v("forward");p.right=v("right");p.score=box("score");p.death=box("death");p.anchor=box("anchor");p.jumpMs=j.optLong("jump",350).coerceIn(100,1500)
  j.optJSONObject("restart")?.let{p.restart=gesture(it)};j.optJSONObject("start")?.let{p.start=gesture(it)};p.menu=box("menu")
  val c=j.getJSONObject("controls");c.keys().forEach{p.controls[Action.valueOf(it)]=gesture(c.getJSONObject(it))}
  val colors=j.getJSONObject("colors");colors.keys().forEach{p.colors[Terrain.valueOf(it)]=colors.getInt(it)}
  p
 }.getOrNull()
 fun save(p:Profile){
  val j=JSONObject();fun box(name:String,b:Box?){b?.let{j.put(name,JSONArray(listOf(it.l,it.t,it.r,it.b)))}}
  fun vector(name:String,v:V){j.put(name,JSONArray(listOf(v.x,v.y)))}
  fun gesture(g:Gesture)=JSONObject().put("x",g.start.x).put("y",g.start.y).put("ex",g.end.x).put("ey",g.end.y).put("ms",g.holdMs).put("verified",g.verified)
  box("game",p.game);box("menu",p.menu);p.start?.let{j.put("start",gesture(it))};box("player",p.player);box("score",p.score);box("death",p.death);box("anchor",p.anchor);vector("forward",p.forward);vector("right",p.right)
  j.put("jump",p.jumpMs);p.restart?.let{j.put("restart",gesture(it))}
  j.put("controls",JSONObject().apply{p.controls.forEach{(k,v)->put(k.name,gesture(v))}})
  j.put("colors",JSONObject().apply{p.colors.forEach{(k,v)->put(k.name,v)}})
  val tmp=file(p.key,"tmp");tmp.writeText(j.toString(2));check(tmp.renameTo(file(p.key,"json")))
 }
}
class Journal(val context:Context){
 private val prefs=context.getSharedPreferences("stats",0)
 private val dir=File(context.filesDir,"diagnostics").apply{mkdirs()}
 private val log=File(dir,"events.jsonl")
 var enabled:Boolean get()=prefs.getBoolean("logs",false);set(v){prefs.edit().putBoolean("logs",v).apply()}
 var screenshots:Boolean get()=prefs.getBoolean("screenshots",false);set(v){prefs.edit().putBoolean("screenshots",v).apply()}
 val best get()=prefs.getInt("best",0)
 val games get()=prefs.getInt("games",0)
 val defeats get()=prefs.getInt("defeats",0)
 fun finish(score:Int?,survival:Long,cause:String){
  prefs.edit().putInt("games",games+1).putInt("defeats",defeats+1).putInt("best",maxOf(best,score?:0)).apply()
  val f=File(dir,"sessions.jsonl");f.appendText(JSONObject().put("endedAt",System.currentTimeMillis()).put("score",score?:JSONObject.NULL).put("survivalMs",survival).put("cause",cause).toString()+"\n")
 }
 @Synchronized fun event(type:String,fields:Map<String,Any?> = emptyMap()){
  if(!enabled)return
  if(log.length()>5_000_000){File(dir,"events.previous.jsonl").delete();log.renameTo(File(dir,"events.previous.jsonl"))}
  log.appendText(JSONObject().put("time",System.currentTimeMillis()).put("type",type).apply{fields.forEach{(k,v)->put(k,v?:JSONObject.NULL)}}.toString()+"\n")
 }
 fun snapshot(b:Bitmap,label:String){if(!screenshots)return
  val f=File(dir,"${System.currentTimeMillis()}-$label.png");f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,90,it)}
  dir.listFiles{f2->f2.extension=="png"}?.sortedByDescending{it.lastModified()}?.drop(40)?.forEach{it.delete()}
 }
 fun export(out:java.io.OutputStream){java.util.zip.ZipOutputStream(out).use{z->
  z.putNextEntry(java.util.zip.ZipEntry("device.txt"));z.write("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid ${android.os.Build.VERSION.SDK_INT}\n".toByteArray());z.closeEntry()
  File(context.filesDir,"profiles").listFiles{f->f.extension=="json"}?.forEach{f->z.putNextEntry(java.util.zip.ZipEntry("profiles/"+f.name));f.inputStream().use{it.copyTo(z)};z.closeEntry()}
  dir.listFiles()?.forEach{f->z.putNextEntry(java.util.zip.ZipEntry(f.name));f.inputStream().use{it.copyTo(z)};z.closeEntry()}
 }}
 fun clear(){dir.listFiles()?.forEach{it.delete()};prefs.edit().clear().apply()}
}
