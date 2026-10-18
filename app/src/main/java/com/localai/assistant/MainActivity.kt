package com.localai.assistant
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView
class MainActivity: Activity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); val v=TextView(this);v.text="✦\nLOCAL AI\n\nStarter Android app. Model not connected yet.";v.textSize=24f;v.gravity=Gravity.CENTER;v.setTextColor(Color.WHITE);v.setBackgroundColor(Color.rgb(18,10,30));setContentView(v)} }
