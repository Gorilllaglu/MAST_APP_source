import Foundation

/// VULN-IPA-05: HTTP-запрос с bearer-токеном.
///
/// В Info.plist (часть этого таргета) выставлены:
///   NSAllowsArbitraryLoads=true,
///   NSAllowsArbitraryLoadsForMedia=true,
///   NSAllowsArbitraryLoadsInWebContent=true,
///   + NSExceptionDomains для insecure.example.com.
///
/// Здесь — соответствующий код, который реально использует это
/// разрешение (без него ATS-послабление формальная ошибка, без impact).
enum NetworkOps {

    private static let AUTH_BEARER: String = "Bearer eyJhbGciOiJIUzI1NiJ9.fake.fake"
    private static let HTTP_ENDPOINT: String = "http://insecure.example.com/v1/profile"

    static func fetchInsecure() {
        guard let url = URL(string: HTTP_ENDPOINT) else { return }   // <-- VULN: http://
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.addValue(AUTH_BEARER, forHTTPHeaderField: "Authorization") // <-- VULN: токен в HTTP
        let task = URLSession.shared.dataTask(with: req) { data, _, _ in
            // намеренно ничего не делаем с ответом
            _ = data
        }
        task.resume()
    }
}
