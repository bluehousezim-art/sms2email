package zw.ecocash.forwarder

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Sender.schedulePeriodic(this)
        Sender.kick(this) // anything saved before a crash goes out now
    }
}
