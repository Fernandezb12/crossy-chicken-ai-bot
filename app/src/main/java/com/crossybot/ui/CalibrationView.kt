package com.crossybot.ui
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import com.crossybot.Hub
import com.crossybot.capture.Frame
import com.crossybot.core.*
import com.crossybot.storage.*
import kotlin.math.*

/** Frozen captured pixels + explicit user annotation, no uncalibrated probing gestures. */
class CalibrationView(context:Context,private val frame:Frame,private val close:(CalibrationView)->Unit):LinearLayout(context){
 private val p=Hub.store.load(frame.key)?:Profile(frame.key)
 private val info=TextView(context)
 private var selected=0
 private val pendingTemplates=mutableMapOf<String,Bitmap>()
 private val labels=listOf("Área del juego","Pollito completo","Casilla ADELANTE","Casilla DERECHA","Suelo seguro","Carretera","Agua","Vía de tren","Obstáculo fijo","Control ADELANTE","Control ATRÁS","Control IZQUIERDA","Control DERECHA","Marcador de puntos","Pantalla de derrota","Control REINICIAR","Pollito · aspecto 2","Pollito · aspecto 3","Pollito · aspecto 4","Pantalla de MENÚ","Control INICIAR")
 private val hints=listOf(
  "Arrastra de esquina a esquina del lienzo del juego, excluyendo barras del navegador.",
  "Encierra sólo el pollito, con poco margen. Usa una captura de una partida viva.",
  "Toca el centro de la casilla de suelo inmediatamente delante del pollo (su punto de aterrizaje).",
  "Toca el centro de la casilla inmediatamente a su derecha. La cuadrícula puede ser isométrica.",
  "Toca suelo seguro vacío, lejos de sombras y del pollo. Necesario.",
  "Toca una zona vacía de asfalto. Opcional: si falta, no se cruzará.",
  "Toca agua. El bot la bloquea; no se ha validado cruzar troncos.",
  "Toca una vía. El bot la bloquea; no se ha validado el detector de trenes.",
  "Toca un árbol, roca u obstáculo fijo. El resto de colores desconocidos también se bloquea.",
  "Demuestra el toque, pulsación o deslizamiento de ADELANTE sobre esta captura.",
  "Demuestra el gesto de ATRÁS sobre esta captura.",
  "Demuestra el gesto de IZQUIERDA sobre esta captura.",
  "Demuestra el gesto de DERECHA sobre esta captura.",
  "Encierra únicamente los dígitos de la puntuación (no monedas ni TOP). OCR local opcional.",
  "En una NUEVA captura de derrota, encierra el botón/símbolo estable que sólo aparece al perder.",
  "En la captura de derrota, demuestra el toque de reinicio. Requiere después Prueba reinicio.",
  "Encierra el pollito tras girar o saltar: muestra adicional del mismo personaje, con poco margen.",
  "Encierra otra apariencia real del pollito. Se usará junto a las otras muestras.",
  "Encierra otra apariencia real del pollito. No uses un coche ni una roca.",
  "En el menú, encierra un botón fijo que desaparece al jugar (por ejemplo MORE). Evita el logotipo que también aparece durante la carga.",
  "En el menú, demuestra el gesto que inicia la partida. Después usa Prueba inicio; requiere marcador OCR."
 )
 init{
  orientation=VERTICAL;setBackgroundColor(Color.rgb(15,30,27));setPadding(8,8,8,8)
  val title=TextView(context).apply{text="CALIBRAR CONTROLES · captura congelada";textSize=17f;setTextColor(Color.WHITE)};addView(title)
  val spinner=Spinner(context).apply{adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,labels)}
  spinner.setBackgroundColor(Color.WHITE);addView(spinner)
  info.setTextColor(Color.WHITE);info.textSize=13f;addView(info)
  val picture=Picker(context);addView(picture,LayoutParams(-1,0,1f))
  spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(parent:AdapterView<*>?){};override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){selected=position;info.text=hints[position];picture.selection=null;picture.invalidate()}}
  addView(TextView(context).apply{setTextColor(Color.LTGRAY);text="${frame.width}×${frame.height} · densidad ${frame.density} · rotación ${frame.rotation}\nGuardar no ejecuta movimientos. Vuelve al juego y prueba ↑ ↓ ← →.";textSize=11f})
  val row=LinearLayout(context);addView(row)
  row.addView(Button(context).apply{text="GUARDAR";setOnClickListener{pendingTemplates.forEach{(label,b)->Hub.store.saveBitmap(p.key,label,b);b.recycle()};pendingTemplates.clear();Hub.reload(p);frame.bitmap.recycle();close(this@CalibrationView)}},LayoutParams(0,-2,1f))
  row.addView(Button(context).apply{text="CANCELAR";setOnClickListener{pendingTemplates.values.forEach{it.recycle()};pendingTemplates.clear();frame.bitmap.recycle();close(this@CalibrationView)}},LayoutParams(0,-2,1f))
 }
 private fun invalidateTests(){p.controls.values.forEach{it.verified=false}}
 private fun annotate(a:V,b:V,ms:Long){
  val box=Box(min(a.x,b.x),min(a.y,b.y),max(a.x,b.x),max(a.y,b.y))
  fun crop(label:String){require(box.width*frame.bitmap.width>=8&&box.height*frame.bitmap.height>=8){"Arrastra un rectángulo de al menos 8 píxeles"}
   val x=(box.l*frame.bitmap.width).toInt().coerceIn(0,frame.bitmap.width-1);val y=(box.t*frame.bitmap.height).toInt().coerceIn(0,frame.bitmap.height-1)
   val bm=Bitmap.createBitmap(frame.bitmap,x,y,(box.width*frame.bitmap.width).toInt().coerceIn(1,frame.bitmap.width-x),(box.height*frame.bitmap.height).toInt().coerceIn(1,frame.bitmap.height-y))
   pendingTemplates.remove(label)?.recycle();pendingTemplates[label]=bm.copy(Bitmap.Config.ARGB_8888,false);if(bm!==frame.bitmap)bm.recycle()
  }
  try{
   when(selected){
    0->{require(box.width>.15&&box.height>.15){"El área debe encerrar el juego"};p.game=box;invalidateTests()}
    1->{crop("player");p.player=box;invalidateTests()}
    2,3->{val player=p.player?:error("Marca primero el pollito");val foot=V(player.center.x,player.t+player.height*.83);val delta=b-foot
     require(delta.norm()>.015){"Selecciona una casilla vecina"};if(selected==2)p.forward=delta else p.right=delta;invalidateTests()}
    in 4..8->{val type=listOf(Terrain.GROUND,Terrain.ROAD,Terrain.WATER,Terrain.RAIL,Terrain.SOLID)[selected-4]
     p.colors[type]=frame.bitmap.getPixel((b.x*frame.bitmap.width).toInt().coerceIn(0,frame.bitmap.width-1),(b.y*frame.bitmap.height).toInt().coerceIn(0,frame.bitmap.height-1))}
    in 9..12->{require(p.game.contains(a)&&p.game.contains(b)){"El gesto debe quedar dentro del área del juego"};val action=listOf(Action.FORWARD,Action.BACK,Action.LEFT,Action.RIGHT)[selected-9];p.controls[action]=Gesture(p.game.normalize(a),p.game.normalize(b),ms.coerceIn(40,1000),false)}
    13->{require(box.width>.005&&box.height>.005);p.score=box}
    14->{crop("death");p.death=box;p.restart?.verified=false}
    15->{require(p.game.contains(a)&&p.game.contains(b));p.restart=Gesture(p.game.normalize(a),p.game.normalize(b),ms.coerceIn(40,1000),false)}
    in 16..18->{crop("player${selected-14}");invalidateTests()}
    19->{crop("menu");p.menu=box;p.start?.verified=false;p.restart?.verified=false}
    20->{require(p.game.contains(a)&&p.game.contains(b));p.start=Gesture(p.game.normalize(a),p.game.normalize(b),ms.coerceIn(40,1000),false);p.restart?.verified=false}
   }
   info.text="✓ ${labels[selected]} registrado. ${hints[selected]}"
  }catch(e:Exception){info.text=e.message?:"Selección inválida"}
 }
 inner class Picker(c:Context):View(c){
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  private var area=RectF();private var down:V?=null;private var at=0L
  var selection:Box?=null
  override fun onDraw(canvas:Canvas){
   super.onDraw(canvas);val s=min(width.toFloat()/frame.bitmap.width,height.toFloat()/frame.bitmap.height)
   val w=frame.bitmap.width*s;val h=frame.bitmap.height*s;area=RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
   canvas.drawBitmap(frame.bitmap,null,area,paint)
   fun drawBox(b:Box,color:Int){paint.color=color;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;canvas.drawRect(area.left+(b.l*area.width()).toFloat(),area.top+(b.t*area.height()).toFloat(),area.left+(b.r*area.width()).toFloat(),area.top+(b.b*area.height()).toFloat(),paint);paint.style=Paint.Style.FILL}
   drawBox(p.game,Color.CYAN);p.player?.let{drawBox(it,Color.GREEN)};selection?.let{drawBox(it,Color.MAGENTA)}
   paint.color=Color.YELLOW;paint.textSize=16f
   p.controls.forEach{(a,g)->val v=p.game.point(g.start);val x=area.left+(v.x*area.width()).toFloat();val y=area.top+(v.y*area.height()).toFloat();canvas.drawCircle(x,y,5f,paint);canvas.drawText(a.name,x+5,y,paint)}
  }
  override fun onTouchEvent(e:MotionEvent):Boolean{
   if(!area.contains(e.x,e.y))return true
   val v=V(((e.x-area.left)/area.width()).toDouble().coerceIn(0.0,.9999),((e.y-area.top)/area.height()).toDouble().coerceIn(0.0,.9999))
   when(e.actionMasked){MotionEvent.ACTION_DOWN->{down=v;at=e.eventTime};MotionEvent.ACTION_MOVE->{down?.let{selection=Box(min(it.x,v.x),min(it.y,v.y),max(it.x,v.x),max(it.y,v.y))};invalidate()};MotionEvent.ACTION_UP->{down?.let{annotate(it,v,e.eventTime-at)};down=null;performClick();invalidate()};MotionEvent.ACTION_CANCEL->down=null}
   return true
  }
  override fun performClick():Boolean{super.performClick();return true}
 }
}
