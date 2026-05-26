import Foundation

/// VULN-IPA-02: чувствительная информация в NSUserDefaults.
///
/// `UserDefaults.standard` сохраняет значения в
/// `~/Library/Preferences/<bundle>.plist`. Это **обычный plain-text plist**,
/// не зашифрован, попадает в backup, читается приложениями из той же
/// app-group, и трivialно достаётся из rooted/jailbroken устройства или
/// при наличии физического доступа к устройству.
enum UserDefaultsOps {

    private static let AUTH_TOKEN: String = "eyJhbGciOiJIUzI1NiJ9.userdefaults.fake"
    private static let PASSWORD: String = "hunter2-prod"
    private static let API_KEY: String = "AIzaSyA_INTENTIONALLY_FAKE_FOR_TESTING"
    private static let PIN_CODE: String = "1234"

    static func runAll() {
        let d = UserDefaults.standard
        // Все четыре set — каждый отдельный sink. Имена ключей нарочно
        // очевидные, чтобы grep-сканер ловил по имени переменной /
        // имени ключа в plist.
        d.set(AUTH_TOKEN, forKey: "auth_token")          // <-- VULN sink
        d.set(PASSWORD,   forKey: "user_password")       // <-- VULN sink
        d.set(API_KEY,    forKey: "google_api_key")      // <-- VULN sink
        d.set(PIN_CODE,   forKey: "unlock_pin")          // <-- VULN sink

        // Дополнительно — словарь с creditCard данными.
        let card: [String: Any] = [
            "pan": "4242424242424242",
            "cvv": "123",
            "exp": "12/29"
        ]
        d.set(card, forKey: "saved_card")                // <-- VULN sink

        // Bonus: synchronize() — старый API, deprecated в iOS 12+, всё
        // ещё встречается; принудительный flush на диск.
        d.synchronize()
    }
}
