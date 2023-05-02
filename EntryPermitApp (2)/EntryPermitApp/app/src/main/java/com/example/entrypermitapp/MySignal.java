package com.example.entrypermitapp;

import android.content.Context;
import android.widget.Toast;

public class MySignal {

    private static MySignal mySignal= null;
    private Context context;

    private MySignal(Context context){
        this.context = context;
    }

    public static void init(Context context){
        if(mySignal == null)
            mySignal = new MySignal(context);
    }


    public static MySignal getInstance(){
        return mySignal;
    }

    public void toast(String text){
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
