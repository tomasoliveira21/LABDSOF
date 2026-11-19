package pt.isep.labdsof.authusers;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Stands in for Keycloak in the tests: one RSA key published as a JWKS over HTTP (as Keycloak's certs
 * endpoint), and tokens shaped like the farol realm's (iss, aud farol-api, sub, 5-minute expiry).
 */
public final class Jwks {

  public static final String ISSUER = "http://localhost:8180/realms/farol";
  public static final String AUDIENCE = "farol-api";

  private final RSAKey key = generate("farol-test-key");
  private final RSAKey otherKey = generate("farol-test-key");
  private final RSAKey unknownKey = generate("other-key");
  private final HttpServer server;

  Jwks() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    var body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    server.createContext("/certs", exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    server.start();
  }

  public String uri() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/certs";
  }

  /**
   * Claims of a good token; the other builders change one of them. {@code email_verified} is true, as Keycloak sends
   * it for an ordinary user: only then is the e-mail stored (D5), so the unverified cases set the claim themselves.
   */
  public JWTClaimsSet.Builder claims(String subject) {
    var expiry = Instant.now().plusSeconds(300);
    return new JWTClaimsSet.Builder()
        .subject(subject)
        .issuer(ISSUER)
        .audience(List.of(AUDIENCE))
        .issueTime(Date.from(expiry.minusSeconds(300)))
        .expirationTime(Date.from(expiry))
        .claim("email", subject + "@farol.test")
        .claim("email_verified", true);
  }

  public String sign(JWTClaimsSet claims) {
    return sign(key, claims);
  }

  public String valid(String subject) {
    return sign(claims(subject).build());
  }

  public String expired(String subject, Duration ago) {
    var expiry = Instant.now().minus(ago);
    return sign(claims(subject).issueTime(Date.from(expiry.minusSeconds(300))).expirationTime(Date.from(expiry))
        .build());
  }

  /** Same key id as the published key, another private key. */
  public String wrongSignature(String subject) {
    return sign(otherKey, claims(subject).build());
  }

  /** A key id the JWKS does not publish. */
  public String unknownKey(String subject) {
    return sign(unknownKey, claims(subject).build());
  }

  public String wrongIssuer(String subject) {
    return sign(claims(subject).issuer("http://localhost:8180/realms/other").build());
  }

  public String wrongAudience(String subject) {
    return sign(claims(subject).audience(List.of("account")).build());
  }

  /** {@code alg: none}. */
  public String unsigned(String subject) {
    return new PlainJWT(claims(subject).build()).serialize();
  }

  /** HS256 under the published key id, to try an algorithm confusion. */
  public String hmac(String subject) {
    try {
      var secret = new byte[32];
      new SecureRandom().nextBytes(secret);
      var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(key.getKeyID()).build(),
          claims(subject).build());
      jwt.sign(new MACSigner(secret));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Same header and signature, another user in the payload. */
  public static String tampered(String token) {
    var parts = token.split("\\.");
    var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    var changed = payload.replaceFirst("\"sub\":\"[^\"]*\"", "\"sub\":\"" + UUID.randomUUID() + "\"");
    return parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(changed.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
  }

  private static String sign(RSAKey signingKey, JWTClaimsSet claims) {
    try {
      var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
          claims);
      jwt.sign(new RSASSASigner(signingKey));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static RSAKey generate(String keyId) {
    try {
      return new RSAKeyGenerator(2048).keyID(keyId).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
