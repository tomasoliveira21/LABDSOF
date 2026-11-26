package pt.isep.labdsof.events.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import pt.isep.labdsof.events.application.EventChangedConcurrentlyException;
import pt.isep.labdsof.events.application.EventNotFoundException;
import pt.isep.labdsof.events.application.TeamChangedConcurrentlyException;
import pt.isep.labdsof.events.domain.InvalidMapFileException;
import pt.isep.labdsof.events.domain.MapFileTooLargeException;
import pt.isep.labdsof.events.domain.VenueMapVersion;

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

  /**
   * The domain's own size check (US-19 D5): the multipart limit normally answers first, so this is the path a
   * caller that reached the use case another way takes.
   */
  @Test
  @DisplayName("US-19 AC-3 a map file above the limit answers 413 with the limit in the detail")
  void mapFileTooLargeNamesTheLimit() {
    var response = new ProblemDetailsAdvice().mapFileTooLarge(new MapFileTooLargeException());

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    var problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(413);
    assertThat(problem.getDetail()).isEqualTo("The map file must be at most 20 MB.");
  }

  @Test
  @DisplayName("US-19 AC-3 the multipart limit answers the same 413, not the framework's generic detail")
  void theMultipartLimitAnswersTheSame413() {
    var response = new ProblemDetailsAdvice().handleMaxUploadSizeExceededException(
        new MaxUploadSizeExceededException(VenueMapVersion.MAX_SOURCE_SIZE_BYTES), HttpHeaders.EMPTY,
        HttpStatus.PAYLOAD_TOO_LARGE, null);

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
    assertThat(((ProblemDetail) response.getBody()).getDetail())
        .isEqualTo("The map file must be at most 20 MB.");
  }

  @Test
  @DisplayName("US-19 AC-2 a file that is not a map answers 400 saying what is accepted, never the reason")
  void invalidMapFileSaysWhatIsAccepted() {
    var response = new ProblemDetailsAdvice()
        .invalidMapFile(new InvalidMapFileException("the image has more than 25000000 pixels"));

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    var problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getDetail()).isEqualTo("The map must be a PNG, JPEG or single-page PDF.");
    assertThat(problem.toString()).doesNotContain("25000000");
  }
}
