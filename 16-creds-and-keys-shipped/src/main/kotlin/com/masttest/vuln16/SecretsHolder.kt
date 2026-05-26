package com.masttest.vuln16

/**
 * VULN-16 (часть 1): hardcoded секреты прямо в .kt константах.
 * Любой, кто декомпилирует APK через jadx — увидит эти значения.
 */
object SecretsHolder {
    // подпись JWT, которую сервер использует для верификации
    const val JWT_SIGNING_KEY: String = "s3cr3t-jwt-signing-key-do-not-share"

    // ключ AWS (формат намеренно похож на настоящий)
    const val AWS_ACCESS_KEY_ID: String = "AKIAIOSFODNN7EXAMPLE"
    const val AWS_SECRET_ACCESS_KEY: String = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"

    // токен админа на staging (часто забывают убрать)
    const val ADMIN_BEARER_TOKEN: String =
        "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYWRtaW4ifQ.fakeFakeFake"
}
