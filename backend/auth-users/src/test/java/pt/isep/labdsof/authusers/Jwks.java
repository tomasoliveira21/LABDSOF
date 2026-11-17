package pt.isep.labdsof.authusers;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Stands in for Keycloak in the tests: one RSA key published as a JWKS over HTTP (as Keycloak's certs
 * endpoint), and tokens shaped like the farol realm's (iss, aud farol-api, sub, 5-minute expiry).
 */
public final class Jwks {

  public static final String ISSUER = "http://localhost:8180/realms/farol";
  public static final String AUDIENCE = "farol-api";

  private final RSAKey key = generate("farol-test-key");
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

  public String valid(String subject) {
    var expiry = Instant.now().plusSeconds(300);
    var claims = new JWTClaimsSet.Builder()
        .subject(subject)
        .issuer(ISSUER)
        .audience(List.of(AUDIENCE))
        .issueTime(Date.from(expiry.minusSeconds(300)))
        .expirationTime(Date.from(expiry))
        .claim("email", subject + "@farol.test")
        .build();
    return sign(key, claims);
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
