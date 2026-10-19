package app.cosmox

import android.net.VpnService

/**
 * VPN service entry point for the future transport engine.
 * Deliberately does not create a fake tunnel: a real core must be integrated before
 * the app can claim that traffic is protected.
 */
class CosmoVpnService : VpnService()
