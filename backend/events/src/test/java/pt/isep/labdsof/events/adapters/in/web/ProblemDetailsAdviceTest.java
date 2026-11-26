package pt.isep.labdsof.events.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.events.application.EventChangedConcurrentlyException;
import pt.isep.labdsof.events.application.EventNotFoundException;
import pt.isep.labdsof.events.application.TeamChangedConcurrentlyException;

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

  /**
   * The conflict needs two transactions racing on one row, which no sequential request can produce, so the
   * adapter's outcome is tested in {@code JdbcTeamsIT} and the answer it turns into is tested here.
   */
  @Test
  @DisplayName("US-16 AC-3 a team changed between the read and the write answers 409 with a fixed detail")
  void teamChangedConcurrentlyIsAConflict() {
    var response = new ProblemDetailsAdvice().teamChangedConcurrently(new TeamChangedConcurrentlyException());

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    var problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo("This team changed meanwhile; read it again.");
  }

  /**
   * As the team conflict above: two publishes racing on one row is an outcome only {@code JdbcEventsIT} can
   * produce, so the answer it turns into is tested here (US-11 D4).
   */
  @Test
  @DisplayName("US-11 AC-1 an event changed between the read and the write answers 409 with a fixed detail")
  void eventChangedConcurrentlyIsAConflict() {
    var response = new ProblemDetailsAdvice().eventChangedConcurrently(new EventChangedConcurrentlyException());

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    var problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo("This event changed meanwhile; read it again.");
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
