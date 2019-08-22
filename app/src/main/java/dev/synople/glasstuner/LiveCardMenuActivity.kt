package dev.synople.glasstuner

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.MenuItem

class LiveCardMenuActivity : Activity() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        openOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.live_card, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_stop -> {
                stopService(Intent(this, LiveCardService::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onOptionsMenuClosed(menu: Menu) {
        super.onOptionsMenuClosed(menu)
        finish()
    }
}
