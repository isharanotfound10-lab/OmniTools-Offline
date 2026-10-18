package com.localai.assistant
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
class FloatingOrbService:Service(){ private var orb:TextView?=null; override fun onCreate(){super.onCreate();if(!Settings.canDrawOverlays(this)){stopSelf();return};val wm=getSystemService(WINDOW_SERVICE) as WindowManager;val v=TextView(this);v.text="✦";v.textSize=28f;v.gravity=Gravity.CENTER;v.setTextColor(Color.WHITE);v.setBackgroundColor(Color.rgb(91,45,145));val type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;val p=WindowManager.LayoutParams(64,64,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP or Gravity.START;p.x=20;p.y=180;wm.addView(v,p);orb=v};override fun onDestroy(){orb?.let{try{(getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)}catch(_:Exception){}};super.onDestroy()};override fun onBind(intent:Intent?):IBinder?=null}
