package hu.unideb.inf.sudokusolver;

import static org.opencv.android.Utils.bitmapToMat;
import static org.opencv.core.Core.BORDER_ISOLATED;
import static org.opencv.core.Core.NORM_MINMAX;
import static org.opencv.core.Core.bitwise_and;
import static org.opencv.core.Core.copyMakeBorder;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.normalize;
import static org.opencv.core.CvType.CV_32FC2;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.Mat.zeros;
import static org.opencv.imgcodecs.Imgcodecs.imencode;
import static org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE;
import static org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY;
import static org.opencv.imgproc.Imgproc.COLOR_GRAY2BGR;
import static org.opencv.imgproc.Imgproc.Canny;
import static org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX;
import static org.opencv.imgproc.Imgproc.GaussianBlur;
import static org.opencv.imgproc.Imgproc.HoughLines;
import static org.opencv.imgproc.Imgproc.INTER_CUBIC;
import static org.opencv.imgproc.Imgproc.INTER_LINEAR;
import static org.opencv.imgproc.Imgproc.LINE_8;
import static org.opencv.imgproc.Imgproc.MORPH_CLOSE;
import static org.opencv.imgproc.Imgproc.MORPH_CROSS;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.RETR_EXTERNAL;
import static org.opencv.imgproc.Imgproc.RETR_LIST;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY_INV;
import static org.opencv.imgproc.Imgproc.adaptiveThreshold;
import static org.opencv.imgproc.Imgproc.approxPolyDP;
import static org.opencv.imgproc.Imgproc.arcLength;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.drawContours;
import static org.opencv.imgproc.Imgproc.erode;
import static org.opencv.imgproc.Imgproc.findContours;
import static org.opencv.imgproc.Imgproc.floodFill;
import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;
import static org.opencv.imgproc.Imgproc.getStructuringElement;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.morphologyEx;
import static org.opencv.imgproc.Imgproc.putText;
import static org.opencv.imgproc.Imgproc.resize;
import static org.opencv.imgproc.Imgproc.warpPerspective;
import static org.opencv.photo.Photo.fastNlMeansDenoising;
import static java.lang.Integer.parseInt;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toRadians;
import static java.util.Comparator.comparingDouble;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public final class ImageProcessor {

	private static final int SCALING_SIZE = 765;
	private static final int CELL_SIZE = SCALING_SIZE / 9;
	private static final Size SIZE = new Size(SCALING_SIZE, SCALING_SIZE);

	public static Integer[] doOcrOnCells(final Mat input, final TessBaseAPI tess) {
		final var threshold = applyAdaptiveThreshold(getGaussianBlur(input, 13), 57, 19);
		final var digits = new ArrayList<Integer>();
		for (var i = 0; i < SCALING_SIZE; i += CELL_SIZE) {
			for (var j = 0; j < SCALING_SIZE; j += CELL_SIZE) {
				final var cell = threshold.submat(new Rect(j, i, CELL_SIZE, CELL_SIZE));
				copyMakeBorder(cell, cell, 1, 1, 1, 1, BORDER_ISOLATED, Scalar.all(255));
				final var borderMask = zeros(cell.rows() + 2, cell.cols() + 2, CV_8UC1);
				floodFill(cell, borderMask, new Point(0, 0), Scalar.all(0));
				final var noBorder = cell.rowRange(1, cell.rows() - 1).colRange(1, cell.cols() - 1);

				final var contours = new ArrayList<MatOfPoint>();
				findContours(noBorder, contours, new Mat(), RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
				final var max = contours.stream().max(comparingDouble(Imgproc::contourArea));
				if (max.isPresent()) {
					final var dst = zeros(noBorder.size(), CV_8UC1);
					final var mask = zeros(noBorder.size(), CV_8UC1);
					drawContours(mask, List.of(max.get()), -1, Scalar.all(255), -1);
					final var percentFilled = countNonZero(mask) / (double) (CELL_SIZE * CELL_SIZE);
					if (percentFilled > 0.04) {
						noBorder.copyTo(dst, mask);
//						final var resizedDst = resizeImage(dst, new Size(dst.width() * 0.7, dst.height() * 0.7));
						erode(dst, dst, getStructuringElement(MORPH_CROSS, new Size(3, 3)));
						final var mob = new MatOfByte();
//						imencode(".png", resizedDst, mob);
						imencode(".png", dst, mob);
						digits.add(imgToInteger(tess, mob.toArray()));
					} else {
						digits.add(0);
					}
				} else {
					digits.add(0);
				}
			}
		}
		return digits.toArray(Integer[]::new);
	}


	private static Integer imgToInteger(final TessBaseAPI tess, final byte[] image) {
		tess.setImage(BitmapFactory.decodeStream(new ByteArrayInputStream(image)));
		final var possibleNumber = tess.getUTF8Text().trim();
		return parseInt(possibleNumber);
	}

	public static Mat preprocess(final Bitmap bitmap) {
		final var mat = new Mat();
		final var bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
		bitmapToMat(bmp32, mat);
		final var resized = resizeImage(mat);
		fastNlMeansDenoising(resized, resized);

		final var grayscale = colorToGrayscale(resized);
		final var grid = perspectiveTransform(grayscale);
		normalize(grid, grid, 0, 255, NORM_MINMAX, CV_8UC1);

		final var edges = new Mat();
		final var blurredGrid = getGaussianBlur(grid, 7);
		Canny(blurredGrid, edges, 100, 300, 5, true);

		final var horizontalLines = houghHorizontal(edges);
		final var verticalLines = houghVertical(edges);

		final var gridPoints = new Mat();
		bitwise_and(horizontalLines, verticalLines, gridPoints);

		return correctDefects(gridPoints, grid);
	}

	// Adding text to the image
	public static void drawSolution(final Mat src, final Integer[] solution) {
		cvtColor(src, src, COLOR_GRAY2BGR);
		final var color = new Scalar(23, 137, 5);
		final var size = src.size();
		final var cellWidth = (int) size.width / 9;
		final var cellHeight = (int) size.height / 9;
		for (var i = 0; i < solution.length; i++) {
			if (solution[i] != 0) {
				putText(src, solution[i].toString(), new Point(cellWidth * (i % 9 + 0.25), cellHeight * (i / 9.0 + 0.75)), FONT_HERSHEY_SIMPLEX, 2, color, 2);
			}
		}
	}

	private static Mat perspectiveTransform(final Mat src) {
		final var counter = new AtomicInteger();
		final var points = findLargestRectangle(src) //
				.toList() //
				.stream() //
				.sorted(comparingDouble(p -> p.y)) //
				.collect(groupingBy(it -> counter.getAndIncrement() / 2, //
						collectingAndThen(toList(), //
								x -> x.stream() //
										.sorted(comparingDouble(p -> p.x))))) //
				.values() //
				.stream() //
				.flatMap(identity()) //
				.toArray(Point[]::new);

		final var srcVertices = new MatOfPoint2f(points);
		final var dstVertices = new MatOfPoint2f(new Point(0, 0), //
				new Point(SCALING_SIZE, 0), //
				new Point(0, SCALING_SIZE), //
				new Point(SCALING_SIZE, SCALING_SIZE));
		final var transformationMatrix = getPerspectiveTransform(srcVertices, dstVertices);
		warpPerspective(src, src, transformationMatrix, SIZE);
		return src;
	}

	private static MatOfPoint2f findLargestRectangle(final Mat src) {
		final var gaussianBlur = getGaussianBlur(src, 9);
		final var adaptiveThreshold = applyAdaptiveThreshold(gaussianBlur, 11, 2);
		final var contours = new ArrayList<MatOfPoint>();
		final var hierarchy = new Mat();
		findContours(adaptiveThreshold, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

		final var curve = new MatOfPoint2f();
		contours.stream() //
				.max(comparingDouble(Imgproc::contourArea)) //
				.ifPresentOrElse(max -> max.convertTo(curve, CV_32FC2), //
						() -> {
							throw new IllegalStateException("Could not find largest contour.");
						});

		final var largestRectangle = new MatOfPoint2f();
		final var approxCurve = new MatOfPoint2f();
		var i = 1;
		while (approxCurve.toArray().length != 4) {
			final var epsilon = arcLength(curve, true) * i / 100;
			approxPolyDP(curve, approxCurve, epsilon, true);
			approxCurve.copyTo(largestRectangle);
			i++;
		}
		return largestRectangle;
	}

	static Mat applyAdaptiveThreshold(final Mat src, final int blockSize, final int C) {
		final var dst = new Mat();
		adaptiveThreshold(src, dst, 255, ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY_INV, blockSize, C);
		return dst;
	}

	static Mat getGaussianBlur(final Mat src, final int size) {
		final var dst = new Mat();
		GaussianBlur(src, dst, new Size(size, size), 0);
		return dst;
	}

	static Mat colorToGrayscale(final Mat src) {
		final var dst = new Mat();
		if (src.channels() != 1) {
			cvtColor(src, dst, COLOR_BGR2GRAY);
			return dst;
		}
		return src;
	}

	static Mat resizeImage(final Mat src) {
		final var dst = new Mat();
		final var compare = Double.compare(src.size().area(), SIZE.area());
		// For upscaling
		if (compare < 0) {
			resize(src, dst, SIZE, 0, 0, INTER_CUBIC);
			return dst;
		}
		// For downscaling
		if (compare > 0) {
			resize(src, dst, SIZE, 0, 0, INTER_LINEAR);
			return dst;
		}
		return src;
	}

	private static Mat correctDefects(final Mat gridPoints, final Mat grid) {
		final var contours = new ArrayList<MatOfPoint>();
		findContours(gridPoints, contours, new Mat(), RETR_LIST, CHAIN_APPROX_SIMPLE);

		final var counter = new AtomicInteger();
		final var sortedCentroids = contours.stream() //
				.map(Imgproc::moments) //
				.map(moments -> new Point(moments.m10 / (moments.m00 + 1e-5), moments.m01 / (moments.m00 + 1e-5))) //
				.sorted(comparingDouble(c -> c.y)) //
				.collect(groupingBy(it -> counter.getAndIncrement() / 10)) //
				.values() //
				.stream() //
				.map(points -> points.stream() //
						.sorted(comparingDouble(c -> c.x)) //
						.collect(toList())) //
				.collect(toList());

		if (counter.get() != 100) {
			throw new IllegalStateException("Could not find all grid points");
		}

		final var output = zeros(SIZE, CV_8UC1);
		IntStream.range(0, 100).forEach(i -> {
			final var ri = i / 10;
			final var ci = i % 10;
			if (ri != 9 && ci != 9) {
				final var rowStart = ri * CELL_SIZE;
				final var rowEnd = (ri + 1) * CELL_SIZE;
				final var colStart = ci * CELL_SIZE;
				final var colEnd = (ci + 1) * CELL_SIZE;
				final var warp = new Mat();
				final var src = new MatOfPoint2f(sortedCentroids.get(ri).get(ci), sortedCentroids.get(ri).get(ci + 1), sortedCentroids.get(ri + 1).get(ci), sortedCentroids.get(ri + 1).get(ci + 1));
				final var dst = new MatOfPoint2f(new Point(colStart, rowStart), new Point(colEnd, rowStart), new Point(colStart, rowEnd), new Point(colEnd, rowEnd));
				final var transformationMatrix = getPerspectiveTransform(src, dst);
				warpPerspective(grid, warp, transformationMatrix, SIZE);
				final var src_sub = warp.submat(rowStart, rowEnd, colStart, colEnd);
				final var dst_sub = output.submat(rowStart, rowEnd, colStart, colEnd);
				src_sub.copyTo(dst_sub);
			}
		});
		return output;
	}


	private static Mat houghHorizontal(final Mat edges) {
		final var lines = new Mat();
		HoughLines(edges, lines, 1, toRadians(1), 150, 0, 0, toRadians(89), toRadians(92));
		return morphologicalClosing(drawLines(lines));
	}

	private static Mat houghVertical(final Mat edges) {
		final var lines = new Mat();
		HoughLines(edges, lines, 1, toRadians(1), 150, 0, 0, toRadians(175));
		HoughLines(edges, lines, 1, toRadians(1), 150, 0, 0, 0, toRadians(5));
		return morphologicalClosing(drawLines(lines));
	}

	private static Mat morphologicalClosing(final Mat src) {
		final var kernel = getStructuringElement(MORPH_RECT, new Size(7, 7));
		morphologyEx(src, src, MORPH_CLOSE, kernel, new Point(-1, -1), 3);
		return src;
	}

	private static Mat drawLines(final Mat lines) {
		final var dst = zeros(SIZE, CV_8UC1);
		for (var x = 0; x < lines.rows(); x++) {
			final var rho = lines.get(x, 0)[0];
			final var theta = lines.get(x, 0)[1];
			final var x0 = cos(theta) * rho;
			final var y0 = sin(theta) * rho;
			final var pt1 = new Point(x0 + 2 * SCALING_SIZE * -sin(theta), y0 + 2 * SCALING_SIZE * cos(theta));
			final var pt2 = new Point(x0 - 2 * SCALING_SIZE * -sin(theta), y0 - 2 * SCALING_SIZE * cos(theta));
			line(dst, pt1, pt2, new Scalar(255, 255, 255), 4, LINE_8, 0);
		}
		return dst;
	}
}
