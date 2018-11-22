package cn.rexih.android.testmediacodec

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        val file = File(Environment.getExternalStorageDirectory(), "Download/test.mp4")
        Player.initMedia(holder?.surface, file.absolutePath)
    }

    private val REQUEST_DISK = 1001;
    private var isSdCardAvailable = false
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<SurfaceView>(R.id.sv_play).holder.addCallback(this)
        initListener()
        doRequestPermission()
    }

    private fun initListener() {
        findViewById<TextView>(R.id.tv_play).setOnClickListener {
            if (isSdCardAvailable) {
                Player.play()
            } else {
                Toast.makeText(this, "缺少权限", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<TextView>(R.id.tv_pause).setOnClickListener {
            Player.pause()
        }
        findViewById<TextView>(R.id.tv_resume).setOnClickListener {
            Player.resume()
        }
        findViewById<TextView>(R.id.tv_stop).setOnClickListener {
            Player.stop()
        }
    }


    private fun doRequestPermission() {
        val perm = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val permissionResult = ContextCompat.checkSelfPermission(this, perm[0])
        if (PackageManager.PERMISSION_GRANTED != permissionResult) {
            ActivityCompat.requestPermissions(this, perm, REQUEST_DISK)
        } else {
            isSdCardAvailable = true
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (REQUEST_DISK == requestCode) {
            if (PackageManager.PERMISSION_GRANTED != grantResults[0]) {

                val show =
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])

                if (show) {
                    ActivityCompat.requestPermissions(this, permissions, REQUEST_DISK)
                }
            } else {
                isSdCardAvailable = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Player.destroyMedia()
    }


}
