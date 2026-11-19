// O serviço que mantém a mesa aberta. Enquanto ele correr, o telemóvel do
// anfitrião é o anunciador: os outros jogadores ligam-se ao endereço que aparece
// na notificação e no ecrã.
package com.sueca.cafe.svc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sueca.cafe.MainActivity
import com.sueca.cafe.R
import com.sueca.cafe.net.CafeServer
import com.sueca.cafe.net.Lan
import com.sueca.cafe.net.PORT
import com.sueca.cafe.net.RoomStore
import kotlinx.coroutines.flow.MutableStateFlow

object HostStatus {
    val running = MutableStateFlow(false)
    val url = MutableStateFlow("")
    val room = MutableStateFlow("")

    fun publish(alive: Boolean, ip: String, port: Int, roomId: String) {
        running.value = alive
        url.value = if (alive && ip.isNotEmpty()) "http://$ip:$port" else ""
        room.value = if (alive) roomId else ""
    }
}

class CafeService : Service() {
    companion object {
        const val ACTION_START = "com.sueca.cafe.action.START"
        const val ACTION_STOP = "com.sueca.cafe.action.STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_ROOM = "room"
        private const val CHANNEL = "mesa"
        private const val NOTIF_ID = 41

        var server: CafeServer? = null
            private set

        fun start(context: Context, port: Int = PORT, room: String = "") {
            val i = Intent(context, CafeService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PORT, port)
                .putExtra(EXTRA_ROOM, room)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CafeService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val port = intent?.getIntExtra(EXTRA_PORT, PORT) ?: PORT
        val room = intent?.getStringExtra(EXTRA_ROOM).orEmpty()
        makeChannel()
        startForeground(NOTIF_ID, build(port, room))
        if (server == null) {
            val s = CafeServer(RoomStore(port))
            s.start()
            server = s
        }
        publish(port, room)
        return START_STICKY
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        val s = server
        server = null
        s?.stop()
        HostStatus.publish(false, "", 0, "")
    }

    private fun publish(port: Int, room: String) {
        HostStatus.publish(true, Lan.ipv4(), port, room)
    }

    private fun makeChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_mesa), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.channel_mesa_desc) }
        )
    }

    private fun build(port: Int, room: String): Notification {
        val ip = Lan.ipv4()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CafeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val where = if (ip == "127.0.0.1")
            getString(R.string.notif_sem_wifi)
        else getString(R.string.notif_em, "http://$ip:$port")
        val text = if (room.isEmpty()) where else "$where · $room"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_copito)
            .setContentTitle(getString(R.string.notif_titulo))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addAction(0, getString(R.string.notif_parar), stop)
            .build()
    }
}
