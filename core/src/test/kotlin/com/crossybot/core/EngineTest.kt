package com.crossybot.core
import org.junit.Assert.*
import org.junit.Test
class EngineTest {
 private fun scene(h:List<Hazard> = emptyList(),terrain:Terrain=Terrain.GROUND)=Scene(1000,V(0.0,0.0),.99,(-3..3).flatMap{x->(-2..3).map{y->Cell(x,y,terrain,.99)}},h)
 @Test fun staleFramesNeverMove(){assertEquals(Action.WAIT,Planner().choose(scene(),1500,300,Strategy.BALANCED,0).action)}
 @Test fun unknownTerrainNeverMoves(){assertEquals(Action.WAIT,Planner().choose(scene(terrain=Terrain.UNKNOWN),1000,300,Strategy.AGGRESSIVE,0).action)}
 @Test fun clearGroundProgresses(){assertEquals(Action.FORWARD,Planner().choose(scene(),1000,300,Strategy.BALANCED,0).action)}
 @Test fun incomingVehicleBlocksForward(){val h=Hazard(1,V(2.0,1.0),V(-6.0,0.0),.5,.1,5);assertNotEquals(Action.FORWARD,Planner().choose(scene(listOf(h)),1000,400,Strategy.CONSERVATIVE,0).action)}
 @Test fun sweptCollisionDetectedEvenWhenFinalPositionSafe(){val h=Hazard(1,V(-3.0,.5),V(20.0,0.0),.5,.1,5);assertNotEquals(Action.FORWARD,Planner().choose(scene(listOf(h)),1000,300,Strategy.BALANCED,0).action)}
 @Test fun waterAndRailAreNotGuessedSafe(){for(t in listOf(Terrain.WATER,Terrain.RAIL))assertEquals(Action.WAIT,Planner().choose(scene(terrain=t),1000,300,Strategy.BALANCED,0).action)}
 @Test fun untrustedVisionNeverMoves(){assertEquals(Action.WAIT,Planner().choose(scene().copy(confidence=.5),1000,300,Strategy.BALANCED,0).action)}
 @Test fun gateRejectsDuplicates(){val g=MotionGate();assertTrue(g.begin(Action.FORWARD,0,V(0.0,0.0),V(0.0,-1.0),300));assertFalse(g.begin(Action.RIGHT,100,V(0.0,0.0),V(1.0,0.0),300))}
 @Test fun deliveryIsNotAcceptance(){val g=MotionGate();g.begin(Action.FORWARD,0,V(0.0,0.0),V(0.0,-1.0),300);assertEquals(MotionGate.Result.WAITING,g.observe(350,V(0.0,0.0),V(0.0,0.0),.99));assertEquals(MotionGate.Result.FAILED,g.observe(1801,V(0.0,0.0),V(0.0,0.0),.99));assertTrue(g.locked)}
 @Test fun twoObservationsAndCameraCompensationRequired(){val g=MotionGate();g.begin(Action.FORWARD,0,V(10.0,10.0),V(0.0,-2.0),300);assertEquals(MotionGate.Result.WAITING,g.observe(310,V(10.0,9.0),V(0.0,1.0),.99));assertEquals(MotionGate.Result.ACCEPTED,g.observe(340,V(10.0,9.0),V(0.0,1.0),.99))}
 @Test fun stopCancelsPendingAndLocks(){val g=MotionGate();g.begin(Action.LEFT,0,V(0.0,0.0),V(-1.0,0.0),300);g.cancel();assertNull(g.pending);assertFalse(g.begin(Action.LEFT,1,V(0.0,0.0),V(-1.0,0.0),300))}
 @Test fun timingLearningBounded(){val a=AdaptiveTiming();repeat(1000){a.record(999999,false)};assertEquals(.8,a.uncertainty,.001);assertEquals(350,a.jumpMs);a.record(400,true);assertEquals(360,a.jumpMs)}
 @Test fun coordinatesRoundTrip(){val b=Box(20.0,100.0,420.0,800.0);val p=V(.25,.8);assertEquals(p,b.normalize(b.point(p)))}
}
