package com.sl.scoped_storage_sample

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.sl.scoped_storage_sample.androidQ.AndroidQActivity
import com.sl.scoped_storage_sample.saf.StorageAccessFrameworkActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        androidQBtn.setOnClickListener {
            val intent = Intent(this, AndroidQActivity::class.java)
            startActivity(intent)
        }
        SAF_Btn.setOnClickListener {
            val intent = Intent(this, StorageAccessFrameworkActivity::class.java)
            startActivity(intent)
        }
    }


}
