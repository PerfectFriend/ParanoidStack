package com.n3.app.util

import android.content.Context
import com.google.gson.Gson

object LocalizedStrings {
    private val gson = Gson()

    fun getAll(ctx: Context): String {
        return gson.toJson(mapOf(
            "app_name" to "N3",
            "status_offline" to getString(ctx, "status_offline", "offline"),
            "status_tor" to getString(ctx, "status_tor", "tor"),
            "status_ready" to getString(ctx, "status_ready", "ready"),
            "tab_chat" to getString(ctx, "tab_chat", "Chat"),
            "tab_tor" to getString(ctx, "tab_tor", "Tor"),
            "tab_crypto" to getString(ctx, "tab_crypto", "Crypto"),
            "tab_bridge" to getString(ctx, "tab_bridge", "Bridge"),
            "tab_log" to getString(ctx, "tab_log", "Log"),
            "bridge_title" to getString(ctx, "bridge_title", "Bridge"),
            "bridge_add_ovpn" to getString(ctx, "bridge_add_ovpn", "Add OpenVPN"),
            "bridge_add_wg" to getString(ctx, "bridge_add_wg", "Add WireGuard"),
            "bridge_add_v2ray" to getString(ctx, "bridge_add_v2ray", "Add V2Ray"),
            "bridge_import" to getString(ctx, "bridge_import", "Import Config"),
            "bridge_status" to getString(ctx, "bridge_status", "Status"),
            "bridge_enabled" to getString(ctx, "bridge_enabled", "Enabled"),
            "bridge_disabled" to getString(ctx, "bridge_disabled", "Disabled"),
            "bridge_active" to getString(ctx, "bridge_active", "Active"),
            "bridge_error" to getString(ctx, "bridge_error", "Error"),
            "bridge_no_configs" to getString(ctx, "bridge_no_configs", "No bridge configs"),
            "bridge_chain_build" to getString(ctx, "bridge_chain_build", "Build Chain"),
            "bridge_test_all" to getString(ctx, "bridge_test_all", "Test All"),
            "bridge_chain_status" to getString(ctx, "bridge_chain_status", "Active Chain"),
            "bridge_chain_none" to getString(ctx, "bridge_chain_none", "Not assembled"),
            "profile_title" to getString(ctx, "profile_title", "Identity"),
            "profile_create" to getString(ctx, "profile_create", "Create Identity"),
            "profile_enter_words" to getString(ctx, "profile_enter_words", "Enter 5 words (max 16 chars each)"),
            "profile_word_label" to getString(ctx, "profile_word_label", "Word %d"),
            "profile_generate" to getString(ctx, "profile_generate", "Generate Seed"),
            "profile_seed_phrase" to getString(ctx, "profile_seed_phrase", "Your seed phrase (save it!)"),
            "profile_warning" to getString(ctx, "profile_warning", "You alone are responsible for your keys. Write down this seed phrase. If lost, your identity is unrecoverable."),
            "profile_verify_title" to getString(ctx, "profile_verify_title", "Verify Seed"),
            "profile_verify_prompt" to getString(ctx, "profile_verify_prompt", "Enter word #%d"),
            "profile_verify_error" to getString(ctx, "profile_verify_error", "Wrong word. Try again."),
            "profile_loaded" to getString(ctx, "profile_loaded", "Identity loaded"),
            "profile_created" to getString(ctx, "profile_created", "Identity created"),
            "boot_checking" to getString(ctx, "boot_checking", "Checking network..."),
            "boot_bridges" to getString(ctx, "boot_bridges", "Testing bridges..."),
            "boot_chain" to getString(ctx, "boot_chain", "Assembling chain..."),
            "boot_simplex" to getString(ctx, "boot_simplex", "Testing SMP..."),
            "boot_ready" to getString(ctx, "boot_ready", "Terminal ready"),
            "boot_failed" to getString(ctx, "boot_failed", "Boot failed: %s"),
        ))
    }

    private fun getString(ctx: Context, key: String, fallback: String): String {
        val id = ctx.resources.getIdentifier(key, "string", ctx.packageName)
        return if (id != 0) ctx.getString(id) else fallback
    }
}
