package pt.isep.labdsof.events.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.events.application.EventNotFoundException;

class ProblemDetailsAdviceTest {

  @Test
  @DisplayName("192 D1 an unexpected error answers 500 Problem Details without its message")
  void unexpectedErrorIsGeneric() {
    var response = new ProblemDetailsAdvice().unexpected(new IllegalStateException("ana@farol.test leaked"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    var problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo("Unexpected error.");
    assertThat(problem.toString()).doesNotContain("ana@farol.test");
  }

  @Test
  @DisplayName("192 AC-5 an event that cannot be read answers 404 with a fixed detail")
  void eventNotFoundHasAFixedDetail() {
    var response = new ProblemDetailsAdvice().eventNotFound(new EventNotFoundException());

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    var problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getDetail()).isEqualTo("Event not found.");
  }
}
