package pt.isep.labdsof.authusers.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemDetailsAdviceTest {

  @Test
  @DisplayName("191 D9 an unexpected error answers 500 Problem Details without its message")
  void unexpectedErrorIsGeneric() {
    var response = new ProblemDetailsAdvice().unexpected(new IllegalStateException("ana@farol.test leaked"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    var problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo("Unexpected error.");
    assertThat(problem.toString()).doesNotContain("ana@farol.test");
  }
}
