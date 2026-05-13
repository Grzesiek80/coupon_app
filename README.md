# Coupons Service (Spring Boot + Gradle)

REST API do zarządzania kuponami rabatowymi.

## Wymagania

- Java 21

## Uruchomienie (Windows / PowerShell)

W katalogu `server/`:

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
  "countryIso2": "PL"
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

IP klienta jest brane z nagłówka `X-Forwarded-For` (pierwszy adres), a jeśli go brak – z `remoteAddr`.

Możliwe powody odrzucenia (`reason`):

- `COUPON_NOT_FOUND`
- `COUNTRY_NOT_ALLOWED`
- `ALREADY_USED_BY_USER`
- `COUPON_EXHAUSTED`
- `GEOIP_UNAVAILABLE`

