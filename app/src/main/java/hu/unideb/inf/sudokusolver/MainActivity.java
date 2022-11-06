package hu.unideb.inf.sudokusolver;

import static android.graphics.Bitmap.createBitmap;
import static android.widget.Toast.LENGTH_SHORT;
import static androidx.activity.result.contract.ActivityResultContracts.GetContent;
import static androidx.core.content.FileProvider.getUriForFile;
import static com.googlecode.tesseract.android.TessBaseAPI.OEM_DEFAULT;
import static com.googlecode.tesseract.android.TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR;
import static java.util.Objects.requireNonNull;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.TakePicture;
import androidx.appcompat.app.AppCompatActivity;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.IntStream;

public class MainActivity extends AppCompatActivity {
	private static volatile TessBaseAPI tessBaseAPI;
	private Uri latestTmpUri;
	private ActivityResultLauncher<Uri> takeImageResult;
	private ActivityResultLauncher<String> selectImageFromGalleryResult;


	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		initOpenCV();
		initOCR();

		final ImageView imageView = findViewById(R.id.imageView);
		final var cameraButton = findViewById(R.id.cameraButton);
		final var browseButton = findViewById(R.id.browseButton);
		final var rotateLeft = findViewById(R.id.rotateLeft);
		final var rotateRight = findViewById(R.id.rotateRight);
		final var solveButton = findViewById(R.id.solveButton);

		takeImageResult = registerForActivityResult(new TakePicture(), success -> {
			if (success) {
				imageView.setImageURI(latestTmpUri);

			}
		});
		selectImageFromGalleryResult = registerForActivityResult(new GetContent(), uri -> {
			if (uri != null) {
				imageView.setImageURI(uri);
			}
		});

		cameraButton.setOnClickListener(view -> takePicture());
		browseButton.setOnClickListener(view -> selectImageFromGallery());
		rotateLeft.setOnClickListener(v -> rotate(imageView, -90));
		rotateRight.setOnClickListener(v -> rotate(imageView, 90));
		solveButton.setOnClickListener(v -> solveSudoku(imageView));
	}

	private void solveSudoku(ImageView imageView) {
		try {
			final var myImg = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
			final var preprocess = ImageProcessor.preprocess(myImg);
			setImageView(imageView, preprocess);
			final var sudokuPuzzle = ImageProcessor.doOcrOnCells(preprocess, tessBaseAPI);
			var h = Solver.init(sudokuPuzzle);
			final var solution = Solver.search(h, new ArrayList<>());
			IntStream.range(0, sudokuPuzzle.length).filter(i -> Objects.equals(sudokuPuzzle[i], solution[i])).forEach(i -> solution[i] = 0);
			ImageProcessor.drawSolution(preprocess, solution);
//            Utils.matToBitmap(preprocess, bmp);
//            imageView.setImageBitmap(bmp);
			setImageView(imageView, preprocess);
		} catch (final IllegalStateException e) {
			Toast.makeText(this, e.getMessage(), LENGTH_SHORT).show();
		}
	}

	private void setImageView(ImageView imageView, Mat mat) {
		final var bmp = createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(mat, bmp);
		imageView.setImageBitmap(bmp);
	}

	private void initOCR() {
		tessBaseAPI = new TessBaseAPI();
		final var assetManager = this.getAssets();
		final var dstPathDir = this.getFilesDir() + "/tesseract/tessdata/";
		final var dstInitPathDir = this.getFilesDir() + "/tesseract";
		final var srcFile = "eng.traineddata";

		try (final var inFile = assetManager.open(srcFile)) {
			final var f = new File(dstPathDir);
			if (!f.exists()) {
				if (!f.mkdirs()) {
					Toast.makeText(this, srcFile + " can't be created.", LENGTH_SHORT).show();
				}
				if (inFile == null) {
					Toast.makeText(this, srcFile + " can't be read.", LENGTH_SHORT).show();
				} else {
					try (final var outFile = new FileOutputStream(dstPathDir + srcFile)) {
						//copy file
						final var buf = new byte[1024];
						int len;
						while ((len = inFile.read(buf)) != -1) {
							outFile.write(buf, 0, len);
						}
						inFile.close();
						tessBaseAPI.init(dstInitPathDir, "eng", OEM_DEFAULT);
					} catch (final IOException ex) {
						Log.e("Tesseract", ex.getMessage());
					}
				}
			} else {
				tessBaseAPI.init(dstInitPathDir, "eng", OEM_DEFAULT);
			}
		} catch (final IOException ex) {
			Log.e("Tesseract", ex.getMessage());
		}
//        tessBaseAPI.setVariable("user_defined_dpi", "300");
		tessBaseAPI.setPageSegMode(PSM_SINGLE_CHAR);
		tessBaseAPI.setVariable("tessedit_char_whitelist", "123456789");
	}

	private void initOpenCV() {
		if (OpenCVLoader.initDebug()) {
			Log.d("OpenCV", "OpenCV loaded Successfully!");
		} else {
			Log.e("OpenCV", "Unable to load OpenCV!");
		}
	}

	private void rotate(final ImageView imageView, final int angle) {
		final var myImg = ((BitmapDrawable) imageView.getDrawable()).getBitmap();

		final var matrix = new Matrix();
		matrix.postRotate(angle);

		final var rotated = createBitmap(myImg, 0, 0, myImg.getWidth(), myImg.getHeight(), matrix, true);

		imageView.setImageBitmap(rotated);
	}

	private void selectImageFromGallery() {
		selectImageFromGalleryResult.launch("image/*");
	}

	private void takePicture() {
		final var tmpFileUri = getTmpFileUri();
		latestTmpUri = tmpFileUri;
		takeImageResult.launch(tmpFileUri);
	}

	private Uri getTmpFileUri() {
		try {
			final var tempFile = File.createTempFile("tmp_image_file", ".png", this.getCacheDir());
			tempFile.createNewFile();
			tempFile.deleteOnExit();
			return getUriForFile(requireNonNull(getApplicationContext()),
					BuildConfig.APPLICATION_ID + ".provider", tempFile);
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}

	}
}