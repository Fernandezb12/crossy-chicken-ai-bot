package com.crossybot.core

import kotlin.math.*

data class V(val x: Double, val y: Double) {
    operator fun plus(b: V) = V(x + b.x, y + b.y)
    operator fun minus(b: V) = V(x - b.x, y - b.y)
    operator fun times(k: Double) = V(x * k, y * k)
    fun norm() = hypot(x, y)
    fun dot(b: V) = x * b.x + y * b.y
}
data class Box(val l: Double, val t: Double, val r: Double, val b: Double) {
    val center get() = V((l+r)/2, (t+b)/2)
    val width get() = r-l
    val height get() = b-t
    fun contains(p: V) = p.x in l..r && p.y in t..b
    fun normalize(p: V) = V((p.x-l)/width, (p.y-t)/height)
    fun point(p: V) = V(l+p.x*width,t+p.y*height)
    fun valid() = l.isFinite() && t.isFinite() && r.isFinite() && b.isFinite() && width>0 && height>0
}
enum class Action(val dx: Int, val dy: Int) { FORWARD(0,1), LEFT(-1,0), RIGHT(1,0), BACK(0,-1), WAIT(0,0) }
enum class Terrain { GROUND, ROAD, WATER, RAIL, SOLID, UNKNOWN }
enum class Phase { LOADING, MENU, PLAYING, MOVING, DEAD, RESTARTING, ERROR, PAUSED }
enum class Mode { MANUAL, ASSISTED, AUTOMATIC }
enum class Strategy(val margin: Double, val progress: Double, val waitCost: Double) {
    CONSERVATIVE(.50,1.0,.10), BALANCED(.35,1.5,.25), AGGRESSIVE(.22,2.2,.40), ADAPTIVE(.38,1.5,.25)
}
data class Cell(val x:Int,val y:Int,val terrain:Terrain,val confidence:Double)
data class Hazard(val id:Int,val position:V,val velocity:V,val radius:Double,val uncertainty:Double,val samples:Int)
data class Scene(val timeMs:Long,val player:V,val confidence:Double,val cells:List<Cell>,val hazards:List<Hazard>,val camera:V=V(0.0,0.0))
data class Choice(val action:Action,val risk:Double,val reason:String)

/** Bounded beam search in calibrated tile coordinates. Unknown terrain never becomes safe by default. */
class Planner {
    private val visits = ArrayDeque<Pair<Int,Int>>()
    private var globalX=0; private var globalY=0
    var adaptiveMargin=.38
    fun reset() { visits.clear();globalX=0;globalY=0 }
    fun accepted(action:Action) {
        globalX+=action.dx;globalY+=action.dy
        visits.addLast(globalX to globalY); if(visits.size>16)visits.removeFirst()
    }
    private fun closestApproach(a:V,b:V,h:Hazard,start:Double,duration:Double):Double {
        val rel=a-(h.position+h.velocity*start)
        val vel=(b-a)*(1.0/duration)-h.velocity
        val t=if(vel.dot(vel)<1e-8)0.0 else (-rel.dot(vel)/vel.dot(vel)).coerceIn(0.0,duration)
        return (rel+vel*t).norm()
    }
    fun choose(scene:Scene,nowMs:Long,jumpMs:Long,strategy:Strategy,idleMs:Long):Choice {
        if(nowMs-scene.timeMs !in 0..250 || scene.confidence<.86 || jumpMs !in 80..2000)
            return Choice(Action.WAIT,1.0,"Fotograma obsoleto, confianza insuficiente o salto sin medir")
        val margin=if(strategy==Strategy.ADAPTIVE)adaptiveMargin else strategy.margin
        val seconds=jumpMs/1000.0
        data class Node(val x:Int,val y:Int,val score:Double,val first:Action,val risk:Double)
        var beam=listOf(Node(0,0,0.0,Action.WAIT,0.0));val candidates=mutableListOf<Node>()
        for(depth in 0..2) {
            val next=mutableListOf<Node>()
            for(n in beam) for(a in Action.entries) {
                val x=n.x+a.dx;val y=n.y+a.dy
                val c=scene.cells.firstOrNull{it.x==x&&it.y==y}?:continue
                // Water/log and railway semantics require a validated specialised detector.
                if(c.confidence<.86 || c.terrain !in setOf(Terrain.GROUND,Terrain.ROAD))continue
                var risk=0.0;var blocked=false
                for(h in scene.hazards){
                    val distance=closestApproach(V(n.x.toDouble(),n.y.toDouble()),V(x.toDouble(),y.toDouble()),h,
                        depth*seconds+(nowMs-scene.timeMs)/1000.0,seconds)
                    val radius=h.radius+.28+margin+h.uncertainty*(depth+1)
                    if(distance<=radius){blocked=true;break}
                    risk=max(risk,exp(-(distance-radius)*2))
                }
                // Moving road traffic must have temporal observations before entering a road.
                if(blocked || (c.terrain==Terrain.ROAD && scene.hazards.any{it.samples<3}))continue
                val repeats=visits.count{it==globalX+x to globalY+y}
                val value=n.score+a.dy*strategy.progress-risk*2-repeats*.7-
                    (if(a==Action.WAIT)strategy.waitCost+idleMs/4000.0 else .05)
                val node=Node(x,y,value,if(depth==0)a else n.first,max(n.risk,risk))
                next+=node
            }
            if(next.isEmpty())break
            beam=next.sortedByDescending{it.score}.take(18)
            candidates.clear();candidates.addAll(beam)
        }
        val best=candidates.maxByOrNull{it.score}?:return Choice(Action.WAIT,1.0,"Sin ruta validada: detenerse y revisar")
        return Choice(best.first,best.risk,"Horizonte de hasta 3 saltos; riesgo %.2f".format(best.risk))
    }
}

/** Exactly one outstanding gesture. OS delivery completion alone is NOT movement confirmation. */
class MotionGate {
    data class Pending(val action:Action,val start:Long,val origin:V,val expected:V,val duration:Long)
    var pending:Pending?=null;private set
    var locked=false;private set
    private var consecutive=0
    var verified=0;private set
    var failed=0;private set
    fun begin(action:Action,now:Long,origin:V,expected:V,duration:Long):Boolean {
        if(locked||pending!=null||action==Action.WAIT||duration !in 80..2000)return false
        pending=Pending(action,now,origin,expected,duration);consecutive=0;return true
    }
    enum class Result { NONE, WAITING, ACCEPTED, FAILED }
    fun observe(now:Long,position:V,worldCamera:V,confidence:Double):Result {
        val p=pending?:return Result.NONE
        val elapsed=now-p.start
        if(elapsed>max(1800L,p.duration*3)){pending=null;locked=true;failed++;return Result.FAILED}
        if(elapsed<p.duration||confidence<.86)return Result.WAITING
        val actual=position-p.origin-worldCamera
        val error=(actual-p.expected).norm()/max(.001,p.expected.norm())
        consecutive=if(error<.45)consecutive+1 else 0
        if(consecutive<2)return Result.WAITING
        pending=null;verified++;return Result.ACCEPTED
    }
    fun cancel(lock:Boolean=true){pending=null;consecutive=0;locked=lock}
    fun reset(){pending=null;locked=false;consecutive=0}
}

class AdaptiveTiming {
    var jumpMs=350L;private set
    var samples=0;private set
    var uncertainty=.38;private set
    fun restore(duration:Long,count:Int,margin:Double){jumpMs=duration.coerceIn(100,1500);samples=count.coerceAtLeast(0);uncertainty=margin.coerceIn(.25,.8)}
    fun record(latency:Long,accepted:Boolean){
        if(accepted&&latency in 80..2000){jumpMs=(jumpMs*.8+latency*.2).roundToLong().coerceIn(100,1500);samples++}
        uncertainty=(uncertainty+if(accepted)-.005 else .05).coerceIn(.25,.8)
    }
}
