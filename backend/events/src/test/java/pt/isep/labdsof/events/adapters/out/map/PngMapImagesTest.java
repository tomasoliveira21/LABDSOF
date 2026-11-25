package pt.isep.labdsof.events.adapters.out.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.isep.labdsof.events.domain.InvalidMapFileException;
import pt.isep.labdsof.events.domain.MapSourceType;

/** The one adapter that decodes an upload (US-19 D2 to D4), on real PNG, JPEG and PDF files. */
class PngMapImagesTest {

  private final PngMapImages images = new PngMapImages();

  private static byte[] image(String format, int width, int height) {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.RED);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    return encoded(image, format);
  }

  private static byte[] encoded(BufferedImage image, String format) {
    var bytes = new ByteArrayOutputStream();
    try {
      assertThat(ImageIO.write(image, format, bytes)).isTrue();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  private static byte[] pdf(int pages, PDRectangle size, String userPassword) {
    try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
      for (var page = 0; page < pages; page++) {
        document.addPage(new PDPage(size));
      }
      if (userPassword != null) {
        document.protect(new StandardProtectionPolicy("owner", userPassword, new AccessPermission()));
      }
      document.save(bytes);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static BufferedImage read(byte[] png) {
    try {
      var image = ImageIO.read(new ByteArrayInputStream(png));
      assertThat(image).isNotNull();
      return image;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @ParameterizedTest(name = "{0} {1}x{2}")
  @CsvSource({"png, 800, 600", "jpg, 640, 480", "png, 1, 1", "png, 600, 800"})
  @DisplayName("US-19 AC-1 an image is re-encoded as a PNG of the same size")
  void anImageIsReEncodedAsAPng(String format, int width, int height) {
    var type = "png".equals(format) ? MapSourceType.PNG : MapSourceType.JPEG;

    var png = images.toPng(image(format, width, height), type);

    assertThat(MapSourceType.ofContent(png)).isEqualTo(MapSourceType.PNG);
    var decoded = read(png);
    assertThat(decoded.getWidth()).isEqualTo(width);
    assertThat(decoded.getHeight()).isEqualTo(height);
  }

  @ParameterizedTest(name = "{0}x{1}")
  @CsvSource({"6000, 3000", "3000, 6000", "4097, 10"})
  @DisplayName("US-19 AC-1 a large image is scaled to 4096 px on its long side")
  void aLargeImageIsScaled(int width, int height) {
    var png = images.toPng(image("png", width, height), MapSourceType.PNG);

    var decoded = read(png);
    assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(PngMapImages.MAX_SIDE);
    assertThat(Math.min(decoded.getWidth(), decoded.getHeight())).isPositive();
    assertThat((double) decoded.getWidth() / decoded.getHeight())
        .isCloseTo((double) width / height, org.assertj.core.data.Percentage.withPercentage(1));
  }

  @Test
  @DisplayName("US-19 AC-1 the pixel limit and the smallest image are both accepted")
  void thePixelLimitIsInclusive() {
    var side = (int) Math.sqrt(PngMapImages.MAX_PIXELS); // 5000 x 5000 = exactly the limit

    assertThat(read(images.toPng(image("png", side, side), MapSourceType.PNG)).getWidth())
        .isEqualTo(PngMapImages.MAX_SIDE);
    assertThat(read(images.toPng(image("png", 1, 1), MapSourceType.PNG)).getWidth()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-19 AC-2 an image above the pixel limit is refused before it is decoded")
  void anImageAboveThePixelLimitIsRefused() {
    // A PNG of one colour compresses to a fraction of its pixels: a small file of 36 megapixels (finding 10)
    var bomb = image("png", 6000, 6000);
    assertThat(bomb.length).isLessThan(200_000);

    assertThatThrownBy(() -> images.toPng(bomb, MapSourceType.PNG))
        .isInstanceOf(InvalidMapFileException.class).hasMessageContaining("more than 25000000 pixels");
  }

  @Test
  @DisplayName("US-19 AC-1 a single-page PDF is rendered to a PNG")
  void aSinglePagePdfIsRendered() {
    var png = images.toPng(pdf(1, PDRectangle.A4, null), MapSourceType.PDF);

    assertThat(MapSourceType.ofContent(png)).isEqualTo(MapSourceType.PNG);
    var decoded = read(png);
    // A4 at 150 dpi: 1240 x 1753, inside the 4096 px limit
    assertThat(decoded.getWidth()).isEqualTo(1240);
    assertThat(decoded.getHeight()).isEqualTo(1753);
  }

  @Test
  @DisplayName("US-19 AC-1 a large PDF page is rendered and then scaled to 4096 px")
  void aLargePdfPageIsScaled() {
    var a0 = new PDRectangle(1684, 2384); // A1 in points: 3508 x 4967 px at 150 dpi, 17 megapixels

    var decoded = read(images.toPng(pdf(1, a0, null), MapSourceType.PDF));

    assertThat(decoded.getHeight()).isEqualTo(PngMapImages.MAX_SIDE);
  }

  @Test
  @DisplayName("US-19 AC-2 a PDF that is not a single unencrypted page is refused")
  void aPdfThatIsNotASingleUnencryptedPageIsRefused() {
    var twoPages = pdf(2, PDRectangle.A4, null);
    var ownerLocked = pdf(1, PDRectangle.A4, ""); // opens without a password, but is encrypted
    var userLocked = pdf(1, PDRectangle.A4, "user"); // PDFBox refuses it before the document is ever open
    var truncated = new byte[64];
    System.arraycopy(pdf(1, PDRectangle.A4, null), 0, truncated, 0, truncated.length);

    assertThatThrownBy(() -> images.toPng(twoPages, MapSourceType.PDF))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("a map PDF has exactly one page");
    assertThatThrownBy(() -> images.toPng(ownerLocked, MapSourceType.PDF))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("an encrypted PDF is not a map");
    assertThatThrownBy(() -> images.toPng(userLocked, MapSourceType.PDF))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("the PDF could not be rendered");
    assertThatThrownBy(() -> images.toPng(truncated, MapSourceType.PDF))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("the PDF could not be rendered");
  }

  @Test
  @DisplayName("US-19 AC-2 a PDF page above the pixel limit is refused before it is rendered")
  void aPdfPageAboveThePixelLimitIsRefused() {
    var huge = new PDRectangle(14_400, 14_400); // the largest page Acrobat allows, 900 megapixels at 150 dpi

    assertThatThrownBy(() -> images.toPng(pdf(1, huge, null), MapSourceType.PDF))
        .isInstanceOf(InvalidMapFileException.class).hasMessageContaining("pixels");
  }

  @Test
  @DisplayName("US-19 AC-2 a render that does not finish in time is refused")
  void aRenderThatDoesNotFinishInTimeIsRefused() {
    var impatient = new PngMapImages(Duration.ofMillis(50));

    assertThatThrownBy(() -> impatient.bounded(() -> {
      Thread.sleep(Duration.ofMinutes(1));
      return null;
    })).isInstanceOf(InvalidMapFileException.class).hasMessage("the PDF took too long to render");
  }

  @Test
  @DisplayName("US-19 AC-2 a render that fails is refused, and a refusal of its own is kept as it is")
  void aRenderThatFailsIsRefused() {
    assertThatThrownBy(() -> images.bounded(() -> {
      throw new IllegalStateException("broken");
    })).isInstanceOf(InvalidMapFileException.class).hasMessage("the PDF could not be rendered");
    assertThatThrownBy(() -> images.bounded(() -> {
      throw new InvalidMapFileException("a map PDF has exactly one page");
    })).isInstanceOf(InvalidMapFileException.class).hasMessage("a map PDF has exactly one page");
  }

  @Test
  @DisplayName("US-19 AC-2 a render interrupted while it is waited for is refused, and the thread stays interrupted")
  void anInterruptedRenderIsRefused() {
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> images.bounded(() -> {
        Thread.sleep(Duration.ofMinutes(1));
        return null;
      })).isInstanceOf(InvalidMapFileException.class).hasMessage("the render was interrupted");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted(); // clears the flag for the tests that follow
    }
  }

  @Test
  @DisplayName("US-19 AC-2 a file that claims to be an image but is not is refused")
  void aFileThatIsNotTheImageItClaimsIsRefused() {
    var notAnImage = "89504E470D0A1A0A not a png after all".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> images.toPng(notAnImage, MapSourceType.PNG))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("no reader for this image");
  }

  @Test
  @DisplayName("US-19 AC-2 a PNG that cannot be written out is refused, not answered half-written")
  void anEncodingThatFailsIsRefused() {
    var broken = new java.io.OutputStream() {
      @Override
      public void write(int b) throws IOException {
        throw new IOException("no space left on device");
      }
    };

    assertThatThrownBy(() -> PngMapImages.encode(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), broken))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("the map could not be encoded as a PNG");
  }

  @Test
  @DisplayName("US-19 AC-1 the stored PNG carries none of the source's metadata")
  void theStoredPngCarriesNoMetadata() {
    // A JPEG with an EXIF-shaped APP1 segment: whatever it says, the re-encoded PNG cannot carry it
    var jpeg = image("jpg", 100, 100);
    var exif = "Exif  GPSLatitude 41.1579 GPSLongitude -8.6291".getBytes(StandardCharsets.ISO_8859_1);
    var withExif = new byte[jpeg.length + exif.length];
    System.arraycopy(jpeg, 0, withExif, 0, 2); // SOI
    System.arraycopy(exif, 0, withExif, 2, exif.length);
    System.arraycopy(jpeg, 2, withExif, 2 + exif.length, jpeg.length - 2);

    var png = images.toPng(jpeg, MapSourceType.JPEG);

    assertThat(new String(png, StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("GPSLatitude");
    assertThat(new String(withExif, StandardCharsets.ISO_8859_1)).contains("GPSLatitude"); // the source did
  }

  @Test
  @DisplayName("US-19 AC-1 an image with transparency is flattened onto white, never onto black")
  void transparencyIsFlattenedOntoWhite() {
    var transparent = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);

    var decoded = read(images.toPng(encoded(transparent, "png"), MapSourceType.PNG));

    assertThat(new Color(decoded.getRGB(5, 5))).isEqualTo(Color.WHITE);
  }
}
