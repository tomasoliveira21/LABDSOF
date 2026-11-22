package pt.isep.labdsof.transports.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.stream.Stream;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsAdviceTest {

  private final ProblemDetailsAdvice advice = new ProblemDetailsAdvice();

  @Test
  @DisplayName("193 D10 an unexpected error answers 500 Problem Details without its message")
  void unexpectedErrorIsGeneric() throws Exception {
    var response = advice.unexpected(new IllegalStateException("ana@farol.test leaked"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    var problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo("Unexpected error.");
    assertThat(problem.toString()).doesNotContain("ana@farol.test");
  }

  private static Stream<Arguments> denials() {
    return Stream.of(
        Arguments.of(new AccessDeniedException("denied")),
        Arguments.of(new AuthorizationDeniedException("denied", new AuthorizationDecision(false))),
        Arguments.of(new InvalidBearerTokenException("bad token")));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("denials")
  @DisplayName("193 D1 a denial is rethrown, so the security layer answers 401 or 403 instead of a 500")
  void aDenialIsRethrownToTheSecurityLayer(RuntimeException denial) {
    assertThatThrownBy(() -> advice.denied(denial)).isSameAs(denial);

    assertThat(denial).isInstanceOfAny(AccessDeniedException.class, AuthenticationException.class);
  }

  @Test
  @DisplayName("193 D1 a client that closed the connection is rethrown and never logged as an error")
  void aDisconnectedClientIsNotAnError(CapturedOutput output) {
    var gone = new AsyncRequestNotUsableException("response is not usable");

    assertThatThrownBy(() -> advice.unexpected(gone)).isSameAs(gone);

    assertThat(output.getOut()).doesNotContain("Unexpected error answering the request");
  }

  @Test
  @DisplayName("193 D1 a client abort nested in another exception is also recognised")
  void aNestedClientAbortIsRecognised() {
    var gone = new IllegalStateException("writing failed",
        new AsyncRequestNotUsableException("response is not usable"));

    assertThatThrownBy(() -> advice.unexpected(gone)).isSameAs(gone);
  }

  @Test
  @DisplayName("193 D1 Tomcat's own ClientAbortException is recognised by name, not only the servlet one")
  void tomcatsClientAbortIsRecognised() {
    var gone = new ClientAbortException("broken pipe");

    assertThatThrownBy(() -> advice.unexpected(gone)).isSameAs(gone);
  }

  @Test
  @DisplayName("193 D1 a cause chain that loops, or a deep one, still answers 500 instead of hanging")
  void aLoopingCauseChainStillAnswers() throws Exception {
    var looping = new IOException("broken") {
      @Override
      public synchronized Throwable getCause() {
        return this;
      }
    };

    assertThat(advice.unexpected(looping).getStatusCode().value()).isEqualTo(500);
  }

  @Test
  @DisplayName("193 D1 a client abort buried deeper than the cause limit is not searched for: the request "
      + "answers 500 rather than walking a chain of any length")
  void aClientAbortBeyondTheCauseLimitIsNotSearchedFor() throws Exception {
    Throwable deep = new ClientAbortException("broken pipe");
    for (int depth = 0; depth < 12; depth++) {
      deep = new IllegalStateException("wrapped " + depth, deep);
    }

    assertThat(advice.unexpected(new Exception("top", deep)).getStatusCode().value()).isEqualTo(500);
  }
}
