# mTLS Setup Guide (Recastly -> Stream Engine)

This guide covers creating certs, wiring `stream-engine` server-side mTLS, and configuring `recastly` as an mTLS client.

## 1) Create a local CA

Use your PKI tooling in production. For local/dev, you can use OpenSSL:

```bash
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/CN=stream-engine-local-ca" \
  -out ca.crt
```

## 2) Create Stream Engine server cert

Generate a server keystore and CSR:

```bash
keytool -genkeypair \
  -alias stream-engine \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore server-keystore.p12 \
  -validity 825 \
  -dname "CN=stream-engine.internal,OU=Platform,O=YourOrg,L=City,ST=State,C=US" \
  -storepass change-me \
  -keypass change-me

keytool -certreq \
  -alias stream-engine \
  -keystore server-keystore.p12 \
  -storetype PKCS12 \
  -storepass change-me \
  -file server.csr
```

Sign the CSR with your CA:

```bash
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days 825 -sha256
```

Import CA and signed cert back into keystore:

```bash
keytool -importcert -noprompt -alias local-ca -file ca.crt \
  -keystore server-keystore.p12 -storetype PKCS12 -storepass change-me

keytool -importcert -noprompt -alias stream-engine -file server.crt \
  -keystore server-keystore.p12 -storetype PKCS12 -storepass change-me
```

## 3) Create Recastly client cert

Generate client keystore and CSR:

```bash
keytool -genkeypair \
  -alias recastly-client \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore recastly-client-keystore.p12 \
  -validity 825 \
  -dname "CN=recastly.internal,OU=Platform,O=YourOrg,L=City,ST=State,C=US" \
  -storepass change-me \
  -keypass change-me

keytool -certreq \
  -alias recastly-client \
  -keystore recastly-client-keystore.p12 \
  -storetype PKCS12 \
  -storepass change-me \
  -file recastly-client.csr
```

Sign client CSR with same CA:

```bash
openssl x509 -req -in recastly-client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out recastly-client.crt -days 825 -sha256
```

Import CA and signed cert into client keystore:

```bash
keytool -importcert -noprompt -alias local-ca -file ca.crt \
  -keystore recastly-client-keystore.p12 -storetype PKCS12 -storepass change-me

keytool -importcert -noprompt -alias recastly-client -file recastly-client.crt \
  -keystore recastly-client-keystore.p12 -storetype PKCS12 -storepass change-me
```

## 4) Build Stream Engine truststore (trust client CA)

```bash
keytool -importcert -noprompt -alias local-ca -file ca.crt \
  -keystore stream-engine-client-truststore.p12 \
  -storetype PKCS12 \
  -storepass change-me
```

## 5) Configure Stream Engine (server-side mTLS)

```bash
STREAM_ENGINE_TLS_ENABLED=true
STREAM_ENGINE_TLS_KEY_STORE=/etc/stream-engine/tls/server-keystore.p12
STREAM_ENGINE_TLS_KEY_STORE_PASSWORD=change-me
STREAM_ENGINE_TLS_KEY_STORE_TYPE=PKCS12
STREAM_ENGINE_TLS_KEY_ALIAS=stream-engine
STREAM_ENGINE_TLS_CLIENT_AUTH=need
STREAM_ENGINE_TLS_TRUST_STORE=/etc/stream-engine/tls/stream-engine-client-truststore.p12
STREAM_ENGINE_TLS_TRUST_STORE_PASSWORD=change-me
STREAM_ENGINE_TLS_TRUST_STORE_TYPE=PKCS12
```

Keep these enabled for defense-in-depth:

```bash
RECASTLY_INBOUND_AUTH_REQUIRED=true
RECASTLY_INBOUND_SIGNATURE_REQUIRED=true
```

## 6) Recastly client checklist

When Recastly calls Stream Engine APIs:

- Use `https://` Stream Engine base URL.
- Configure Recastly HTTP client to present `recastly-client-keystore.p12`.
- Configure Recastly truststore with CA used to sign Stream Engine server cert.
- Keep `X-Stream-Engine-Secret` and signature headers enabled.
- Keep timestamps in UTC epoch seconds and enforce short request lifetime.

## 7) Certificate rotation workflow

- Issue new server/client certs before expiry (for example, T-30 days).
- Add new CA/cert chain to truststores first (overlap window).
- Deploy new keystore on one side, validate mTLS handshake.
- Deploy new keystore on other side.
- Remove old certs/CA from truststores after cutover.
- Track expiry and alert on certs older than your rotation policy.

## 8) Verification

Basic connectivity test with client cert:

```bash
curl --cert recastly-client.crt --key recastly-client.key \
  --cacert ca.crt \
  https://stream-engine.internal:8080/actuator/health
```

If handshake fails, check:

- cert subject/SAN matches target host
- truststore contains issuing CA/intermediate chain
- `STREAM_ENGINE_TLS_CLIENT_AUTH` is `need`
- keystore passwords and alias are correct
