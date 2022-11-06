package hu.unideb.inf.sudokusolver;

import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class SparseMatrix {

	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("00");

	public static List<List<Node>> createSparseMatrixFromSudokuPuzzle(final Integer[] sudokuCellValues, final Map<String, ColumnNode> headers) {
		return IntStream.range(0, sudokuCellValues.length) //
				.mapToObj(i -> createPositionConstraints(headers, i, sudokuCellValues[i])) //
				.flatMap(identity()) //
				.collect(toList());
	}

	private static Stream<List<Node>> createPositionConstraints(final Map<String, ColumnNode> headers, final int index, final Integer cellValue) {
		if (cellValue == 0) {
			return IntStream.range(0, 9).boxed().map(i -> createNode(headers, index, i + 1, false));
		}
		return Stream.of(createNode(headers, index, cellValue, true));
	}

	private static List<Node> createNode(final Map<String, ColumnNode> headers, final int index, final Integer cellValue, final boolean given) {
		final var nodes = new ArrayList<Node>();
		for (var x = 0; x < 9; x++) {
			for (var value = 1; value <= 9; value++) {
				if (x == index / 9 && value == cellValue) {
					nodes.add(new Node(headers.get("R" + (index / 9 + 1) + cellValue), given));
				}
				if (x == index % 9 && value == cellValue) {
					nodes.add(new Node(headers.get("C" + (index % 9 + 1) + cellValue), given));
				}
				final var z = index / 9 / 3 * 3 + index % 9 / 3;
				if (x == z && value == cellValue) {
					nodes.add(new Node(headers.get("B" + (z + 1) + cellValue), given));
				}
			}
		}
		nodes.add(new Node(headers.get('G' + DECIMAL_FORMAT.format(index + 1)), given));
		return nodes;
	}

	static Map<String, ColumnNode> createHeadersMap(final ColumnNode rootHeader) {
		return Stream.concat(Stream.of(rootHeader), createPositionConstraintHeaders()).collect(toMap(ColumnNode::getName, identity()));
	}

	private static Stream<ColumnNode> createPositionConstraintHeaders() {
		final var headers = new ArrayList<ColumnNode>();
		for (var i = 0; i < 9; i++) {
			for (var j = 0; j < 9; j++) {
				headers.add(new ColumnNode(getName("R", i, j)));
				headers.add(new ColumnNode(getName("C", i, j)));
				headers.add(new ColumnNode(getName("B", i, j)));
				headers.add(new ColumnNode('G' + DECIMAL_FORMAT.format(i * 9 + j + 1)));
			}
		}
		return headers.stream().sorted(comparing(ColumnNode::getName));
	}

	private static String getName(final String constraint, final int i, final int j) {
		return constraint + (i + 1) + (j + 1);
	}

}
