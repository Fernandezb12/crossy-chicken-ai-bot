package com.crossybot
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.crossybot.ui.MainActivity
import com.crossybot.storage.*
import com.crossybot.capture.Frame
import com.crossybot.vision.Vision
import com.crossybot.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTests {
 @get:Rule val activity=ActivityTestRule(MainActivity::class.java)
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private val context get()=instrumentation.targetContext
 private fun bitmap(name:String)=instrumentation.context.assets.open("$name.png").use{BitmapFactory.decodeStream(it)}
 @Test fun launcherOpensAndRefusesUncalibratedAutomation(){
  assertNotNull(activity.activity)
  instrumentation.runOnMainSync{Hub.stop();Hub.profile=null;Hub.mode=Mode.AUTOMATIC;assertFalse(Hub.activate());assertFalse(Hub.running)}
 }
 @Test fun profilesRoundTripAndRemainDeviceSpecific(){
  val store=ProfileStore(context);val p=Profile("instrumentation-412x915")
  p.game=Box(.05,.1,.95,.9);p.controls[Action.LEFT]=Gesture(V(.1,.8),V(.2,.8),160,true);store.save(p)
  val restored=store.load(p.key)!!;assertEquals(p.game,restored.game);assertEquals(p.controls[Action.LEFT],restored.controls[Action.LEFT]);assertNull(store.load("other-device"))
 }
 @Test fun opencvDetectsHeldOutRealGameFramesAndRejectsMenus(){
  assertTrue(org.opencv.android.OpenCVLoader.initLocal())
  val store=ProfileStore(context);val p=Profile("fixture-412x915")
  val base=bitmap("playing-base");val back=bitmap("playing-back")
  val t1=Bitmap.createBitmap(base,89,451,64,75);val t2=Bitmap.createBitmap(back,200,323,59,70)
  store.saveBitmap(p.key,"player",t1);store.saveBitmap(p.key,"player2",t2)
  p.player=Box(89.0/412,451.0/915,153.0/412,526.0/915);p.forward=V(.1,-.05);p.right=V(.17,.04);p.colors[Terrain.GROUND]=base.getPixel(70,550)
  val vision=Vision(store)
  // Calibration uses base/back only; other images are held out from the templates.
  for(name in listOf("playing-right","playing-left","playing-up")){
   val b=bitmap(name);val o=vision.analyze(Frame(b,412,915,0,160,"fixture",1000),p)
   assertNotNull("Must find chicken in $name",o.player);val expected=when(name){"playing-right"->V(159.0/412,467.0/915);"playing-left"->V(201.0/412,421.0/915);else->V(250.5/412,360.1/915)}
   assertTrue("Incorrect location: $name ${o.player}",(o.player!!-expected).norm()<.035);b.recycle()
  }
  for(name in listOf("mobile-idle","bottom")){
   val b=bitmap(name);val o=vision.analyze(Frame(b,412,915,0,160,"fixture",1200),p);assertNull("Must reject $name",o.player);b.recycle()
  }
  vision.close();base.recycle();back.recycle();t1.recycle();t2.recycle()
 }
 @Test fun menuTemplateRejectsPlayingScene(){
  assertTrue(org.opencv.android.OpenCVLoader.initLocal())
  val store=ProfileStore(context);val p=Profile("menu-fixture")
  val menu=bitmap("menu-second");val sample=Bitmap.createBitmap(menu,12,734,90,83)
  p.menu=Box(12.0/412,734.0/915,102.0/412,817.0/915);store.saveBitmap(p.key,"menu",sample)
  val v=Vision(store);assertTrue(v.analyze(Frame(menu,412,915,0,160,"fixture",1000),p).menu)
  val playing=bitmap("playing-base");assertFalse(v.analyze(Frame(playing,412,915,0,160,"fixture",1100),p).menu)
  v.close();menu.recycle();sample.recycle();playing.recycle()
 }
 @Test fun stopInvalidatesQueuedGestureTokens(){
  instrumentation.runOnMainSync{Hub.stop();val token=Hub.token();Hub.pause("test");assertNotEquals(token,Hub.token());assertFalse(Hub.valid(token))}
 }
}
