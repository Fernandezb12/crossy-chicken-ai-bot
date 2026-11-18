package com.crossybot.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.crossybot.capture.Frame
import com.crossybot.core.*
import com.crossybot.storage.*
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.*

data class Observation(val scene:Scene?,val player:V?,val camera:V,val dead:Boolean,val confidence:Double,val reason:String,val analysisMs:Long,val menu:Boolean=false)

/** Calibration-specific pixel detector. All world estimates originate from captured pixels. */
class Vision(private val store:ProfileStore):AutoCloseable {
 private var key="";private val templates=mutableListOf<Mat>();private var deathTemplate:Mat?=null;private var menuTemplate:Mat?=null
 private var previous:Mat?=null;private var previousAt=0L
 private var tracked:List<PixelTrack> = emptyList();private var serial=0;private var seen=0
 private var lastPlayer:V?=null
 private data class PixelTrack(val id:Int,val p:V,val velocity:V,val radius:Double,val samples:Int)
 override fun close(){templates.forEach{it.release()};templates.clear();deathTemplate?.release();menuTemplate?.release();menuTemplate=null;previous?.release();deathTemplate=null;previous=null;key="";tracked=emptyList();seen=0;lastPlayer=null}
 private fun rgb(b:Bitmap):Mat {val rgba=Mat();Utils.bitmapToMat(b,rgba);val m=Mat();Imgproc.cvtColor(rgba,m,Imgproc.COLOR_RGBA2RGB);rgba.release();return m}
 private fun rect(b:Box,w:Int,h:Int):Rect {val x=(b.l*w).toInt().coerceIn(0,w-1);val y=(b.t*h).toInt().coerceIn(0,h-1);return Rect(x,y,((b.r*w).toInt()-x).coerceIn(1,w-x),((b.b*h).toInt()-y).coerceIn(1,h-y))}
 private fun matchFixed(m:Mat,t:Mat?,box:Box?):Double {
  if(t==null||box==null)return 0.0
  val roi=m.submat(rect(box,m.cols(),m.rows()));val resized=Mat();Imgproc.resize(roi,resized,t.size());roi.release()
  val result=Mat();Imgproc.matchTemplate(resized,t,result,Imgproc.TM_SQDIFF_NORMED)
  val score=1.0-result.get(0,0)[0];result.release();resized.release();return score
 }
 private fun cameraMotion(old:Mat,current:Mat,p:Profile):V {
  val mask=Mat.zeros(old.size(),CvType.CV_8UC1);Imgproc.rectangle(mask,rect(p.game,old.cols(),old.rows()),Scalar(255.0),-1)
  // Exclude the player, moving avatar is not a camera landmark.
  lastPlayer?.let{Imgproc.circle(mask,Point(it.x*old.cols(),it.y*old.rows()),max(12,(p.right.norm()*old.cols()).toInt()),Scalar(0.0),-1)}
  val pts=MatOfPoint();Imgproc.goodFeaturesToTrack(old,pts,80,.02,12.0,mask);mask.release()
  val a=MatOfPoint2f(*pts.toArray());pts.release();if(a.empty()){a.release();return V(0.0,0.0)}
  val b=MatOfPoint2f();val status=MatOfByte();val err=MatOfFloat()
  Video.calcOpticalFlowPyrLK(old,current,a,b,status,err)
  val aa=a.toArray();val bb=b.toArray();val ss=status.toArray();val ee=err.toArray()
  val flows=aa.indices.filter{ss[it].toInt()==1&&ee[it]<18}.map{V(bb[it].x-aa[it].x,bb[it].y-aa[it].y)}
  a.release();b.release();status.release();err.release()
  if(flows.size<10)return V(0.0,0.0)
  val med=V(flows.map{it.x}.sorted()[flows.size/2],flows.map{it.y}.sorted()[flows.size/2])
  if(flows.count{(it-med).norm()<2.5}<flows.size*.65)return V(0.0,0.0)
  return V(med.x/current.cols(),med.y/current.rows())
 }
 private fun grid(v:V,p:Profile):V {val d=p.right.x*p.forward.y-p.forward.x*p.right.y;return V((v.x*p.forward.y-p.forward.x*v.y)/d,(p.right.x*v.y-v.x*p.right.y)/d)}
 private fun detectPlayer(m:Mat,p:Profile):Pair<V,Double>? = templates.mapNotNull{detectWithTemplate(m,p,it)}.maxByOrNull{it.second}
 private fun detectWithTemplate(m:Mat,p:Profile,t:Mat):Pair<V,Double>? {
  val search=m.submat(rect(p.game,m.cols(),m.rows()))
  if(search.cols()<t.cols()||search.rows()<t.rows()){search.release();return null}
  val res=Mat();Imgproc.matchTemplate(search,t,res,Imgproc.TM_SQDIFF_NORMED)
  val best=Core.minMaxLoc(res);val confidence=1-best.minVal
  // Suppress duplicate candidates: a white car may resemble the calibrated chicken.
  val bx=best.minLoc.x.toInt();val by=best.minLoc.y.toInt()
  val exclude=Rect(max(0,bx-t.cols()/2),max(0,by-t.rows()/2),1,1)
  exclude.width=min(res.cols()-exclude.x,t.cols());exclude.height=min(res.rows()-exclude.y,t.rows())
  Imgproc.rectangle(res,exclude,Scalar(1.0),-1)
  val second=Core.minMaxLoc(res).minVal;res.release();search.release()
  if(confidence<.86||second-best.minVal<.018)return null
  val r=rect(p.game,m.cols(),m.rows())
  return V((r.x+bx+t.cols()/2.0)/m.cols(),(r.y+by+t.rows()*.83)/m.rows()) to confidence
 }
 private fun classify(b:Bitmap,at:V,p:Profile):Pair<Terrain,Double>{
  if(!p.game.contains(at))return Terrain.UNKNOWN to 0.0
  val x=(at.x*b.width).toInt();val y=(at.y*b.height).toInt()
  val votes=mutableListOf<Pair<Terrain,Double>>()
  for(dy in -3..3 step 3)for(dx in -3..3 step 3){
   val c=b.getPixel((x+dx).coerceIn(0,b.width-1),(y+dy).coerceIn(0,b.height-1))
   val best=p.colors.minByOrNull{(_,s)-> colorDistance(c,s)}?:return Terrain.UNKNOWN to 0.0
   val distance=colorDistance(c,best.value)
   votes+=if(distance<.12)best.key to (1-distance) else Terrain.UNKNOWN to 0.0
  }
  val group=votes.groupBy{it.first}.maxBy{it.value.size};val confidence=group.value.map{it.second}.average()
  return group.key to if(group.value.size>=7)confidence else 0.0
 }
 private fun colorDistance(a:Int,b:Int):Double=sqrt((Color.red(a)-Color.red(b)).toDouble().pow(2)+(Color.green(a)-Color.green(b)).toDouble().pow(2)+(Color.blue(a)-Color.blue(b)).toDouble().pow(2))/441.67
 fun analyze(frame:Frame,p:Profile):Observation {
  val start=android.os.SystemClock.elapsedRealtime();val m=rgb(frame.bitmap);val gray=Mat();Imgproc.cvtColor(m,gray,Imgproc.COLOR_RGB2GRAY)
  if(key!=p.key){close();key=p.key;listOf("player","player2","player3","player4").forEach{label->store.bitmap(p.key,label)?.let{templates+=rgb(it);it.recycle()}};store.bitmap(p.key,"death")?.let{deathTemplate=rgb(it);it.recycle()};store.bitmap(p.key,"menu")?.let{menuTemplate=rgb(it);it.recycle()}}
  val dead=matchFixed(m,deathTemplate,p.death)>.97
  val menu=matchFixed(m,menuTemplate,p.menu)>.97
  val cam=previous?.takeIf{it.size()==gray.size()}?.let{cameraMotion(it,gray,p)}?:V(0.0,0.0)
  val player=if(dead)null else detectPlayer(m,p)
  val dt=(frame.at-previousAt)/1000.0
  val currentTracks=mutableListOf<PixelTrack>()
  if(previous!=null&&dt in .015..0.5&&p.validGeometry()){
   val warp=Mat.eye(2,3,CvType.CV_64F);warp.put(0,2,cam.x*m.cols());warp.put(1,2,cam.y*m.rows())
   val aligned=Mat();Imgproc.warpAffine(previous,aligned,warp,gray.size());warp.release()
   val diff=Mat();Core.absdiff(aligned,gray,diff);aligned.release();Imgproc.threshold(diff,diff,28.0,255.0,Imgproc.THRESH_BINARY)
   val kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,Size(5.0,5.0));Imgproc.morphologyEx(diff,diff,Imgproc.MORPH_CLOSE,kernel);kernel.release()
   val contours=ArrayList<MatOfPoint>();val hierarchy=Mat();Imgproc.findContours(diff,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);hierarchy.release();diff.release()
   val used=mutableSetOf<Int>()
   for(c in contours){val box=Imgproc.boundingRect(c);val area=Imgproc.contourArea(c);c.release();if(area<22||box.width>m.cols()*.6)continue
    val center=V((box.x+box.width/2.0)/m.cols(),(box.y+box.height/2.0)/m.rows());if(!p.game.contains(center))continue
    if(player!=null&&(grid(center-player.first,p)).norm()<.8)continue
    val old=tracked.filter{it.id !in used}.minByOrNull{(grid(center-it.p-cam,p)).norm()}
    val matched=old?.takeIf{grid(center-it.p-cam,p).norm()<2.5}
    val velocity=if(matched!=null)(center-matched.p-cam)*(1.0/dt) else V(0.0,0.0)
    val id=matched?.id?:++serial;used+=id
    currentTracks+=PixelTrack(id,center,if(matched!=null)matched.velocity*.5+velocity*.5 else velocity,max(box.width.toDouble()/m.cols(),box.height.toDouble()/m.rows())*.5,(matched?.samples?:0)+1)
   }
  }
  tracked=currentTracks;seen++
  previous?.release();previous=gray;previousAt=frame.at;m.release()
  val elapsed=android.os.SystemClock.elapsedRealtime()-start
  if(menu&&dead)return Observation(null,null,cam,false,0.0,"Menú/derrota ambiguos: recalibra",elapsed)
  if(menu)return Observation(null,null,cam,false,1.0,"Menú reconocido",elapsed,true)
  if(dead)return Observation(null,null,cam,true,1.0,"Derrota: plantilla coincidente",elapsed)
  if(player==null)return Observation(null,null,cam,false,0.0,"Pollito no reconocido de forma única",elapsed)
  lastPlayer=player.first
  if(!p.validGeometry())return Observation(null,player.first,cam,false,player.second,"Completa la geometría y el suelo",elapsed)
  val cells=(-3..3).flatMap{x->(-2..3).map{y->
   val at=player.first+p.right*x.toDouble()+p.forward*y.toDouble()
   val result=if(x==0&&y==0)Terrain.GROUND to player.second else classify(frame.bitmap,at,p)
   Cell(x,y,result.first,result.second)
  }}
  val hazards=currentTracks.map{h->Hazard(h.id,grid(h.p-player.first,p),grid(h.velocity,p),h.radius/min(p.forward.norm(),p.right.norm()),if(h.samples<3).7 else .2,h.samples)}
  val confidence=if(seen<4)0.0 else player.second
  return Observation(Scene(frame.at,V(0.0,0.0),confidence,cells,hazards,cam),player.first,cam,false,confidence,"Segmentación calibrada; ${hazards.size} objetos móviles",elapsed)
 }
}
