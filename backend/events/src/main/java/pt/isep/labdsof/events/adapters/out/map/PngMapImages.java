package pt.isep.labdsof.events.adapters.out.map;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.application.MapImages;
import pt.isep.labdsof.events.domain.InvalidMapFileException;
import pt.isep.labdsof.events.domain.MapSourceType;

/**
 * The one place an uploaded map is decoded (US-19 D2 to D4, ADR-007 decision 2). Whatever comes in, what comes out
 * is a PNG of at most {@value #MAX_SIDE} px on its long side, drawn into a new opaque image and re-encoded, so no
 * EXIF (a photo's GPS position), no PDF metadata and no active content of the source survives.
 *
 * <p>The size of an image is read from its header <em>before</em> it is decoded, and a PDF page's is computed from
 * the page box, so a small file can never expand into a huge bitmap in memory (D3, assessment finding 10). An image
 * carried <em>inside</em> a page is bounded by the render itself, see {@link #renderer(PDDocument)}.
 */
@Component
class PngMapImages implements MapImages {

  /** US-19 D3: 25 megapixels, ~100 MB of heap at the decode's peak. */
  static final long MAX_PIXELS = 25_000_000L;

  /** ADR-007 decision 2. */
  static final int MAX_SIDE = 4096;

  /** US-19 D4: an A3 page lands at ~2500 px, inside {@link #MAX_SIDE}. */
  static final int PDF_DPI = 150;

  private static final int POINTS_PER_INCH = 72;

  private final Duration renderTimeout;

  PngMapImages() {
    this(Duration.ofSeconds(20)); // D4
  }

  PngMapImages(Duration renderTimeout) {
    this.renderTimeout = renderTimeout;
  }

  @Override
  public byte[] toPng(byte[] source, MapSourceType type) {
    return encode(flattened(type.isPdf() ? fromPdf(source) : fromImage(source)));
  }

  /** An image upload: its header decides whether it is decoded at all (D3). */
  private BufferedImage fromImage(byte[] source) {
    try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) {
        throw new InvalidMapFileException("no reader for this image");
      }
      var reader = readers.next();
      try {
        reader.setInput(stream);
        checkPixels((long) reader.getWidth(0) * reader.getHeight(0));
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      throw invalid("the image could not be read", e);
    }
  }

  /**
   * A PDF upload: one page, not encrypted, rendered at {@value #PDF_DPI} dpi inside the timeout of D4. PDFBox's
   * render cannot be interrupted from the outside, so it runs on its own virtual thread and the upload is refused
   * when it takes too long, whatever the render then does.
   */
  private BufferedImage fromPdf(byte[] source) {
    return bounded(() -> {
      try (var document = Loader.loadPDF(source)) {
        if (document.isEncrypted()) {
          throw new InvalidMapFileException("an encrypted PDF is not a map");
        }
        if (document.getNumberOfPages() != 1) {
          throw new InvalidMapFileException("a map PDF has exactly one page");
        }
        var box = document.getPage(0).getCropBox();
        var scale = (double) PDF_DPI / POINTS_PER_INCH;
        checkPixels((long) Math.ceil(box.getWidth() * scale) * (long) Math.ceil(box.getHeight() * scale));
        return renderer(document).renderImageWithDPI(0, PDF_DPI, ImageType.RGB);
      }
    });
  }

  /**
   * The renderer of D4, with subsampling on. The page's own size is bounded by {@link #checkPixels}, but an image
   * drawn on it is not: PDFBox decodes an image XObject at its own resolution and only scales it while drawing, so
   * an A4 page carrying a Flate image of 100 megapixels would ask for ~300 MB of heap out of the container's 384
   * (review of #253). Subsampled, the image is decoded at the size it is drawn at, so D3's limit holds for what is
   * really decoded. Package-private so a test can read the flag back, which no rendered PNG shows.
   */
  static PDFRenderer renderer(PDDocument document) {
    var renderer = new PDFRenderer(document);
    renderer.setSubsamplingAllowed(true);
    return renderer;
  }

  /**
   * Runs a render on its own virtual thread and gives up on it after {@link #renderTimeout}. Package-private so
   * the three outcomes a real PDF cannot be made to produce on demand — too slow, broken, interrupted — are
   * tested on a render that does exactly that.
   */
  BufferedImage bounded(Callable<BufferedImage> render) {
    // Not try-with-resources: closing an executor waits for its task, which is the one thing a timeout must not do
    var executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    try {
      var rendering = executor.submit(render);
      try {
        return rendering.get(renderTimeout.toNanos(), TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        rendering.cancel(true);
        throw invalid("the PDF took too long to render", e);
      } catch (ExecutionException e) {
        throw invalid("the PDF could not be rendered", e.getCause());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw invalid("the render was interrupted", e);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  /** Scaled to fit {@link #MAX_SIDE} and drawn onto white: one opaque image with nothing but its pixels (D2). */
  private static BufferedImage flattened(BufferedImage image) {
    var longest = Math.max(image.getWidth(), image.getHeight());
    var ratio = longest > MAX_SIDE ? (double) MAX_SIDE / longest : 1;
    var width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
    var height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
    var flattened = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = flattened.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, width, height);
      graphics.drawImage(image, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return flattened;
  }

  private static byte[] encode(BufferedImage image) {
    var png = new ByteArrayOutputStream();
    encode(image, png);
    return png.toByteArray();
  }

  /** Package-private with the stream: the only way to make the encoding of a decoded image fail is to break it. */
  static void encode(BufferedImage image, OutputStream png) {
    try {
      if (!ImageIO.write(image, "png", png)) {
        throw new InvalidMapFileException("the map could not be encoded as a PNG");
      }
    } catch (IOException e) {
      throw invalid("the map could not be encoded as a PNG", e);
    }
  }

  private static void checkPixels(long pixels) {
    if (pixels > MAX_PIXELS) {
      throw new InvalidMapFileException("the image has more than " + MAX_PIXELS + " pixels");
    }
  }

  /** Keeps the reason of a refusal that is already one, so a bad file never reads as an unexpected error. */
  private static InvalidMapFileException invalid(String reason, Throwable cause) {
    return cause instanceof InvalidMapFileException refusal ? refusal : new InvalidMapFileException(reason, cause);
  }
}
