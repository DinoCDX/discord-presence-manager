package com.dpm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ExternalTest {
    const val DEFAULT_URL = Art.DEFAULT_IMAGE_URL

    fun run(c: Context, imageUrl: String): String {
        val token = Store.token(c) ?: return "Log in first"
        val app = Art.appId(c)
        val path = Art.external(c, app, imageUrl) ?: return "Registering the image failed. Details are in Logs."
        val activity = JSONObject()
            .put("name", "DPM image test")
            .put("type", 0)
            .put("application_id", app)
            .put("details", "External image")
            .put("state", "Sent through DPM")
            .put("platform", "android")
            .put("assets", JSONObject().put("large_image", path).put("large_text", "Test image"))
        val sent = Api.call(
            "POST",
            "/users/@me/headless-sessions",
            token,
            JSONObject().put("activities", JSONArray().put(activity))
        )
        Logs.add("test headless-sessions ${sent.code} ${sent.body.take(400)}")
        return if (sent.code in 200..299) {
            "Sent. Check the Presence card on Discord."
        } else {
            "Discord refused the presence (HTTP ${sent.code}). Details are in Logs."
        }
    }
}
