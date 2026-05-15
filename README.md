# Coupons Service (Spring Boot + Gradle)

REST API do zarządzania kuponami rabatowymi.

## Wymagania

- Java 21

## Uruchomienie (Windows / PowerShell)

W katalogu projektu:

```powershell
./gradlew bootRun
```

Domyślnie serwis startuje na porcie `8081`.

## API

### Utworzenie kuponu

`POST /api/v1/coupons`

Body:

```json
{
  "code": "WIOSNA",
  "maxUses": 5,
  "country": "PL"
}
```

### Zużycie kuponu

`POST /api/v1/coupons/use`

Body:

```json
{
  "code": "wiosna",
  "userId": "user-123"
}
```

`userId` jest opcjonalne, ale jeśli zostanie podane, to kupon może być użyty tylko raz przez tego użytkownika.
IP klienta jest brane z nagłówka `X-Forwarded-For` (pierwszy adres), a jeśli go brak – z `remoteAddr`.

Możliwe powody odrzucenia (`reason`):

- `COUPON_NOT_FOUND`
- `COUNTRY_NOT_ALLOWED`
- `ALREADY_USED_BY_USER`
- `COUPON_EXHAUSTED`
- `GEOIP_UNAVAILABLE`

### Przykładowa odpowiedź akceptacji

```json
{
  "couponId": "<uuid>",
  "code": "WIOSNA",
  "status": "ACCEPTED"
}
```

### Przykładowa odpowiedź odrzucenia

```json
{
  "couponId": "<uuid>",
  "code": "WIOSNA",
  "status": "REJECTED",
  "reason": "COUPON_EXHAUSTED"
}
```

## Kluczowe cechy

- Cache geolokalizacji IP w pamięci przy użyciu `Caffeine` (można zastąpić Redis dla środowiska rozproszonego).
- Obsługa odporności z `Resilience4j`: `CircuitBreaker` + `Retry` dla zapytań do zewnętrznego serwisu GeoIP.
- Optymistyczne blokowanie (`@Version`) w encji `Coupon` dla bezpiecznej aktualizacji liczby użyć.
- Retry mechanizm dla `OptimisticLockingFailureException` - maksymalnie 3 próby przy konfliktach współbieżności.
- Normalizacja kodów kuponów jest case-insensitive, więc `WIOSNA` i `wiosna` traktowane są jako ten sam kod.
- Weryfikacja kraju klienta na podstawie IP i ograniczenie użycia kuponu według limitu.

## Skalowalność i środowisko rozproszone

- **Współbieżność**: Optymistyczne blokowanie z retry mechanizmem zapewnia bezpieczeństwo w środowisku wielowątkowym.
- **Cache**: Lokalny cache Caffeine - dla środowiska rozproszonego można dodać Redis.
- **Baza danych**: Atomowe operacje `UPDATE` zapewniają spójność nawet przy wielu instancjach aplikacji.

