import Foundation
import Security

/// VULN-IPA-01: Keychain item записан с самым слабым access-control'ом.
///
/// Сканер должен видеть в коде сочетание `SecItemAdd` + либо
/// `kSecAttrAccessibleAlways` (читается даже когда устройство залочено),
/// либо `kSecAttrAccessibleAfterFirstUnlock` без `kSecAttrAccessControl`
/// (читается фоновыми процессами без user-presence). Дополнительный
/// сигнал — литералы токена и пароля прямо в коде.
enum KeychainOps {

    private static let SERVICE: String = "com.masttest.stingapp"

    // Hardcoded literals — попадают в Swift binary как plaintext strings
    private static let AUTH_TOKEN: String = "eyJhbGciOiJIUzI1NiJ9.fake.fake"
    private static let API_PASSWORD: String = "P@ssw0rd-2024"
    private static let SESSION_TOKEN: String = "sess_live_4eC39HqLyjWDarjtT1zdp7dc"

    static func runAll() {
        _ = saveTokenAccessibleAlways()
        _ = savePasswordAfterFirstUnlockNoAccessControl()
        _ = saveSessionWithSyncedAccess()
    }

    /// Худшая категория: `kSecAttrAccessibleAlways` — токен в Keychain
    /// читается даже когда устройство залочено, любым процессом с
    /// тем же entitlement keychain-group'ом.
    @discardableResult
    static func saveTokenAccessibleAlways() -> OSStatus {
        let query: [CFString: Any] = [
            kSecClass:           kSecClassGenericPassword,
            kSecAttrService:     SERVICE,
            kSecAttrAccount:     "auth_token",
            kSecValueData:       AUTH_TOKEN.data(using: .utf8)!,
            kSecAttrAccessible:  kSecAttrAccessibleAlways          // <-- VULN sink
        ]
        SecItemDelete(query as CFDictionary)
        return SecItemAdd(query as CFDictionary, nil)
    }

    /// `kSecAttrAccessibleAfterFirstUnlock` без `kSecAttrAccessControl` —
    /// доступ из любого процесса (включая background fetch) после первой
    /// разблокировки телефона. Нет требования биометрии/passcode для
    /// доступа к ключу.
    @discardableResult
    static func savePasswordAfterFirstUnlockNoAccessControl() -> OSStatus {
        let query: [CFString: Any] = [
            kSecClass:           kSecClassGenericPassword,
            kSecAttrService:     SERVICE,
            kSecAttrAccount:     "api_password",
            kSecValueData:       API_PASSWORD.data(using: .utf8)!,
            kSecAttrAccessible:  kSecAttrAccessibleAfterFirstUnlock  // <-- VULN sink
            // kSecAttrAccessControl с .userPresence / .biometryAny НЕ задан
        ]
        SecItemDelete(query as CFDictionary)
        return SecItemAdd(query as CFDictionary, nil)
    }

    /// `kSecAttrSynchronizable = true` + `kSecAttrAccessibleWhenUnlocked` —
    /// токен **уезжает в iCloud Keychain** и реплицируется на все устройства
    /// под этим Apple ID. Часто не желаемое поведение для серверных токенов.
    @discardableResult
    static func saveSessionWithSyncedAccess() -> OSStatus {
        let query: [CFString: Any] = [
            kSecClass:                 kSecClassGenericPassword,
            kSecAttrService:           SERVICE,
            kSecAttrAccount:           "session_token",
            kSecValueData:             SESSION_TOKEN.data(using: .utf8)!,
            kSecAttrAccessible:        kSecAttrAccessibleWhenUnlocked,
            kSecAttrSynchronizable:    kCFBooleanTrue!                 // <-- VULN sink
        ]
        SecItemDelete(query as CFDictionary)
        return SecItemAdd(query as CFDictionary, nil)
    }
}
