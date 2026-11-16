package com.example.smartorganizer

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** User-added Quick Settings tile; does not modify Samsung One UI internals. */
class OrganizerSafetyTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("quick_actions", MODE_PRIVATE)
        val enabled = !prefs.getBoolean("safe_mode", false)
        prefs.edit().putBoolean("safe_mode", enabled).apply()
        renderTile(enabled)
    }

    private fun syncTile() {
        val enabled = getSharedPreferences("quick_actions", MODE_PRIVATE)
            .getBoolean("safe_mode", false)
        renderTile(enabled)
    }

    private fun renderTile(enabled: Boolean) {
        qsTile?.let { tile ->
            tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (enabled) "안전 정리 ON" else "안전 정리 OFF"
            tile.subtitle = if (enabled) "일괄 삭제 차단" else "삭제 허용"
            tile.updateTile()
        }
    }
}
