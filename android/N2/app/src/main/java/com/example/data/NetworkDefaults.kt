package com.example.data

/**
 * Централизованные константы сетевых адресов и серверов Not Gammon.
 *
 * Извлечены из GameViewModel как часть архитектурной очистки (6E).
 * Все адреса — onion-сервисы (Tor Hidden Services).
 */
object NetworkDefaults {

    /** URL сервера матчмейкинга (onion) */
    const val SERVER_URL = "http://q273p7coau3uvzeddexvdgv6andorfzvplstztheso2qcsj4yqvfzzad.onion"

    /** SMP-сервер по умолчанию */
    const val SMP_ONION = "smp://xlxM8uqJQZgu45bi2OSDokYilqEP8RGBeBb48f0UvTY=@7czed3rxeryz4zxlo7wiwgz36yfmdwvu6ylv5wkby3trei3qsuw4lnqd.onion:5223"

    /** XFTP-сервер по умолчанию */
    const val XFTP_ONION = "xftp://IROP-a...TcI=@fv3pfzxih5sjf33jmusfbskmd2i3lywaaaysh6tijc7df7k6sijq3yyd.onion:443"

    /** TURN-сервер для WebRTC */
    const val TURN_SERVER = "turn:your-turn-server.com:3478"

    /** Tor SOCKS5 порт по умолчанию */
    const val DEFAULT_TOR_SOCKS_PORT = 9050

    // ── Внешние сервисы (clearnet) ──

    /** DNS-over-HTTPS для DoH-резолвинга */
    const val CLOUDFLARE_DOH = "https://cloudflare-dns.com/dns-query"

    /** Сервис определения внешнего IP (основной) */
    const val IPIFY = "https://api.ipify.org"

    /** Сервис определения внешнего IP (запасной) */
    const val IFCONFIG_ME = "https://ifconfig.me"

    /** Набор SMP-серверов для верификации по умолчанию */
    val DEFAULT_SMP_SERVERS: Set<String> = setOf(SMP_ONION)

    /** Набор XFTP-серверов для верификации по умолчанию */
    val DEFAULT_XFTP_SERVERS: Set<String> = setOf(XFTP_ONION)
}
